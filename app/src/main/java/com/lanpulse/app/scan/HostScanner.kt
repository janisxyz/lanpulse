package com.lanpulse.app.scan

import com.lanpulse.app.model.LanDevice
import com.lanpulse.app.model.NetworkRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface ScanEvent {
    data class Progress(
        val scanned: Int,
        val total: Int,
        val found: Int,
        val rangeLabel: String,
        val ip: String,
    ) : ScanEvent

    data class Host(val device: LanDevice) : ScanEvent
    data object Done : ScanEvent
}

object HostScanner {
    fun scan(ranges: List<NetworkRange>): Flow<ScanEvent> = channelFlow {
        val jobs = ranges.map { range -> range to Cidr.hosts(range.network, range.prefix, cap = 1022) }
        val total = jobs.sumOf { it.second.size }
        val sem = Semaphore(64)
        val mutex = Mutex()
        var scanned = 0
        var found = 0
        val emitter = this

        coroutineScope {
            jobs.forEach { (range, hosts) ->
                hosts.map { ip ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val device = probe(range, ip)
                            mutex.withLock {
                                scanned += 1
                                if (device != null) found += 1
                            }
                            emitter.send(
                                ScanEvent.Progress(
                                    scanned = scanned,
                                    total = total,
                                    found = found,
                                    rangeLabel = range.label,
                                    ip = ip,
                                ),
                            )
                            if (device != null) emitter.send(ScanEvent.Host(device))
                        }
                    }
                }.awaitAll()
            }
        }
        send(ScanEvent.Done)
    }

    private suspend fun probe(range: NetworkRange, ip: String): LanDevice? {
        val isYou = ip == range.localIp
        val isGw = ip == range.gateway
        val ping = Pinger.ping(ip, if (isYou) 200 else 700)
        if (!ping.reachable && !isYou && !isGw) return null

        val mac = ArpTable.macFor(ip)
        val vendor = MacVendors.lookup(mac)
        val ports = PortScanner.probeOpen(
            ip,
            PortCatalog.QUICK.map { it.first },
            timeoutMs = 160,
        )
        val hostname = withContext(Dispatchers.IO) {
            DeviceClassifier.hostnameOf(
                ip,
                vendor?.let { "${it.lowercase().replace(' ', '-')}-$ip" } ?: ip,
            )
        }
        val (kind, services) = DeviceClassifier.classify(hostname, vendor, ports, isGw, isYou)
        return LanDevice(
            ip = ip,
            rangeId = range.id,
            hostname = hostname,
            mac = mac,
            vendor = vendor,
            kind = kind,
            pingMs = ping.rttMs,
            openPorts = ports,
            services = services,
            isGateway = isGw,
            isYou = isYou,
        )
    }
}
