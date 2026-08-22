package com.lanpulse.app.scan

import android.content.Context
import com.lanpulse.app.model.LanDevice
import com.lanpulse.app.model.NetworkRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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
    fun scan(context: Context, ranges: List<NetworkRange>): Flow<ScanEvent> = channelFlow {
        val jobs = ranges.map { range -> range to Cidr.hosts(range.network, range.prefix, cap = 1022) }
        val total = jobs.sumOf { it.second.size }
        val sem = Semaphore(48)
        val mutex = Mutex()
        var scanned = 0
        var found = 0
        val seen = ConcurrentHashMap.newKeySet<String>()
        val names = ConcurrentHashMap<String, String>()
        val emitter = this
        val nameHits = Channel<Pair<String, String>>(Channel.UNLIMITED)

        fun rangeFor(ip: String): NetworkRange? =
            ranges.find { Cidr.network(ip, it.prefix) == it.network }

        suspend fun emitHost(device: LanDevice) {
            val hostname = device.hostname?.takeIf { it.isNotBlank() } ?: names[device.ip]
            val withName = if (hostname != device.hostname) device.copy(hostname = hostname) else device
            if (seen.add(withName.ip)) {
                mutex.withLock { found += 1 }
            }
            emitter.send(ScanEvent.Host(withName))
        }

        val mdnsJob = launch(Dispatchers.IO) {
            Mdns.collect(context, ranges, durationMs = 10_000) { ip, host ->
                names[ip] = host
                nameHits.trySend(ip to host)
            }
            nameHits.close()
        }

        val namesJob = launch {
            for ((ip, host) in nameHits) {
                val range = rangeFor(ip) ?: continue
                val mac = ArpTable.macFor(ip)
                val vendor = MacVendors.lookup(mac)
                val isYou = ip == range.localIp
                val isGw = ip == range.gateway
                val (kind, services) = DeviceClassifier.classify(host, vendor, emptyList(), isGw, isYou)
                emitHost(
                    LanDevice(
                        ip = ip,
                        rangeId = range.id,
                        hostname = host,
                        mac = mac,
                        vendor = vendor,
                        kind = kind,
                        services = services,
                        isGateway = isGw,
                        isYou = isYou,
                    ),
                )
            }
        }

        coroutineScope {
            jobs.forEach { (range, hosts) ->
                hosts.map { ip ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val device = probe(range, ip, names[ip])
                            mutex.withLock { scanned += 1 }
                            emitter.send(
                                ScanEvent.Progress(
                                    scanned = scanned,
                                    total = total,
                                    found = found,
                                    rangeLabel = range.label,
                                    ip = ip,
                                ),
                            )
                            if (device != null) emitHost(device)
                        }
                    }
                }.awaitAll()
            }
        }

        ArpTable.snapshot().forEach { (ip, mac) ->
            val range = rangeFor(ip) ?: return@forEach
            if (ip in seen) return@forEach
            val hostname = names[ip] ?: withContext(Dispatchers.IO) {
                Netbios.nameOf(ip) ?: DeviceClassifier.dnsHostname(ip)
            }
            val vendor = MacVendors.lookup(mac)
            val isYou = ip == range.localIp
            val isGw = ip == range.gateway
            val (kind, services) = DeviceClassifier.classify(hostname, vendor, emptyList(), isGw, isYou)
            emitHost(
                LanDevice(
                    ip = ip,
                    rangeId = range.id,
                    hostname = hostname,
                    mac = mac,
                    vendor = vendor,
                    kind = kind,
                    services = services,
                    isGateway = isGw,
                    isYou = isYou,
                ),
            )
        }

        delay(2_000)
        mdnsJob.cancel()
        nameHits.close()
        namesJob.join()
        send(ScanEvent.Done)
    }

    private suspend fun probe(range: NetworkRange, ip: String, knownName: String?): LanDevice? {
        val isYou = ip == range.localIp
        val isGw = ip == range.gateway
        val ping = Pinger.ping(ip, if (isYou) 250 else 800)
        if (!ping.reachable && !isYou && !isGw) return null

        val mdns = withContext(Dispatchers.IO) { Mdns.probeNamed(ip) }
        val mac = ArpTable.macFor(ip)
        val vendor = MacVendors.lookup(mac)
        val ports = PortScanner.probeOpen(
            ip,
            PortCatalog.FINGERPRINT,
            timeoutMs = 160,
        )
        val hostname = knownName
            ?: mdns.hostname
            ?: withContext(Dispatchers.IO) {
                Netbios.nameOf(ip) ?: DeviceClassifier.dnsHostname(ip)
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
