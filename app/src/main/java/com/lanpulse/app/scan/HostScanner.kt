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
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ScanEvent {
    data class Progress(
        val scanned: Int,
        val total: Int,
        val found: Int,
        val rangeLabel: String,
        val ip: String,
    ) : ScanEvent

    data class Host(val device: LanDevice) : ScanEvent
    data class Range(val range: NetworkRange) : ScanEvent
    data object Done : ScanEvent
}

object HostScanner {
    fun scan(context: Context, ranges: List<NetworkRange>): Flow<ScanEvent> = channelFlow {
        val allRanges = CopyOnWriteArrayList(ranges)
        val swept = ConcurrentHashMap.newKeySet<String>()
        val sem = Semaphore(48)
        val mutex = Mutex()
        var scanned = 0
        var found = 0
        var total = 0
        val seen = ConcurrentHashMap.newKeySet<String>()
        val names = ConcurrentHashMap<String, String>()
        val emitter = this
        val nameHits = Channel<Pair<String, String>>(Channel.UNLIMITED)

        fun rangeFor(ip: String): NetworkRange? =
            allRanges.find { Cidr.contains(it.network, it.prefix, ip) }

        suspend fun emitHost(device: LanDevice) {
            val hostname = device.hostname?.takeIf { it.isNotBlank() } ?: names[device.ip]
            val withName = if (hostname != device.hostname) device.copy(hostname = hostname) else device
            if (seen.add(withName.ip)) {
                mutex.withLock { found += 1 }
            }
            emitter.send(ScanEvent.Host(withName))
        }

        suspend fun sweep(range: NetworkRange) {
            if (!swept.add(range.id)) return
            val hosts = Cidr.hosts(range.network, range.prefix, cap = 1022)
            mutex.withLock { total += hosts.size }
            coroutineScope {
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

        val hunter = async(Dispatchers.IO) { SubnetHunter.hunt(context, ranges) }

        ranges.forEach { sweep(it) }

        hunter.await().forEach { extra ->
            if (allRanges.none { it.id == extra.id }) {
                allRanges += extra
                emitter.send(ScanEvent.Range(extra))
                sweep(extra)
            }
        }

        (ArpTable.snapshot().keys + names.keys).forEach { ip ->
            if (rangeFor(ip) != null || !Cidr.isRfc1918(ip) || allRanges.size >= 16) return@forEach
            val extra = SubnetHunter.rangeOf(ip, "Discovered")
            if (allRanges.none { it.id == extra.id }) {
                allRanges += extra
                emitter.send(ScanEvent.Range(extra))
                sweep(extra)
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
