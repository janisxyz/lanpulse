package com.lanpulse.app.scan

import android.content.Context
import android.net.wifi.WifiManager
import com.lanpulse.app.model.NetworkRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

object Mdns {
    private const val GROUP = "224.0.0.251"
    private const val PORT = 5353

    private val BROWSE = listOf(
        "_workstation._tcp.local",
        "_ssh._tcp.local",
        "_sftp-ssh._tcp.local",
        "_http._tcp.local",
        "_https._tcp.local",
        "_device-info._tcp.local",
        "_smb._tcp.local",
        "_afpovertcp._tcp.local",
        "_homeassistant._tcp.local",
        "_home-assistant._tcp.local",
        "_hap._tcp.local",
        "_googlecast._tcp.local",
        "_companion-link._tcp.local",
        "_airplay._tcp.local",
        "_raop._tcp.local",
        "_ipp._tcp.local",
        "_printer._tcp.local",
        "_rfb._tcp.local",
        "_nvstream._tcp.local",
        "_services._dns-sd._udp.local",
    )

    data class Probe(val reachable: Boolean, val rttMs: Float?, val hostname: String?)

    fun probe(ip: String, timeoutMs: Int = 250): PingResult {
        val named = probeNamed(ip, timeoutMs)
        return PingResult(named.reachable, named.rttMs)
    }

    fun probeNamed(ip: String, timeoutMs: Int = 280): Probe {
        return try {
            val query = DnsPackets.query(DnsPackets.reversePtr(ip), DnsPackets.TYPE_PTR, unicast = true)
            MulticastSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val started = System.nanoTime()
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), PORT))
                val buf = ByteArray(1500)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val rtt = (System.nanoTime() - started) / 1_000_000f
                val host = namesFromPacket(buf, packet.length, ip)
                    .map { it.second }
                    .firstOrNull { it.isNotBlank() && !it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) }
                Probe(true, rtt, host)
            }
        } catch (_: Exception) {
            Probe(false, null, null)
        }
    }

    suspend fun collect(
        context: Context,
        ranges: List<NetworkRange>,
        durationMs: Long = 8_000,
        onName: (ip: String, hostname: String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("lanpulse-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        val names = ConcurrentHashMap<String, String>()
        val group = InetAddress.getByName(GROUP)
        val iface = interfaceFor(ranges.firstOrNull()?.localIp)
        try {
            MulticastSocket().use { bound ->
                bound.reuseAddress = true
                bound.broadcast = true
                bound.soTimeout = 350
                try {
                    bound.bind(InetSocketAddress(PORT))
                } catch (_: Exception) {
                    // 5353 taken — still send QU queries from this ephemeral port
                }
                if (iface != null) {
                    bound.networkInterface = iface
                    runCatching { bound.joinGroup(InetSocketAddress(group, PORT), iface) }
                } else {
                    runCatching { bound.joinGroup(group) }
                }
                queryAll(bound, group, ranges)
                val deadline = System.currentTimeMillis() + durationMs
                val buf = ByteArray(2048)
                var lastQuery = System.currentTimeMillis()
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        bound.receive(packet)
                        ingest(buf, packet.length, packet.address?.hostAddress, names, onName)
                    } catch (_: SocketTimeoutException) {
                    }
                    if (System.currentTimeMillis() - lastQuery > 2_000) {
                        queryAll(bound, group, ranges)
                        lastQuery = System.currentTimeMillis()
                    }
                }
                runCatching {
                    if (iface != null) bound.leaveGroup(InetSocketAddress(group, PORT), iface)
                    else bound.leaveGroup(group)
                }
            }
        } catch (_: Exception) {
            // 5353 in use — still try ephemeral QU queries
            runEphemeral(iface, group, ranges, durationMs, names, onName)
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    fun namesFromPacket(data: ByteArray, length: Int, sourceIp: String?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val records = DnsPackets.parseRecords(data, length)
        val hostToIp = mutableMapOf<String, String>()
        records.filterIsInstance<DnsRecord.A>().forEach { rec ->
            hostToIp[rec.name.lowercase()] = rec.ip
            out += rec.ip to rec.name
        }
        records.filterIsInstance<DnsRecord.Srv>().forEach { rec ->
            hostToIp[rec.name.lowercase()]?.let { ip -> out += ip to rec.target }
            sourceIp?.let { out += it to rec.target }
        }
        records.filterIsInstance<DnsRecord.Ptr>().forEach { rec ->
            if (rec.name.endsWith("in-addr.arpa")) {
                val ip = arpaToIp(rec.name)
                if (ip != null) out += ip to rec.target
            } else {
                val label = rec.target.substringBefore('.')
                sourceIp?.let { out += it to label }
            }
        }
        return out.filter { it.second.isNotBlank() && !looksLikeIp(it.second) }
    }

    private fun runEphemeral(
        iface: NetworkInterface?,
        group: InetAddress,
        ranges: List<NetworkRange>,
        durationMs: Long,
        names: ConcurrentHashMap<String, String>,
        onName: (String, String) -> Unit,
    ) {
        try {
            MulticastSocket().use { socket ->
                socket.soTimeout = 350
                socket.broadcast = true
                if (iface != null) socket.networkInterface = iface
                queryAll(socket, group, ranges)
                val deadline = System.currentTimeMillis() + durationMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        ingest(buf, packet.length, packet.address?.hostAddress, names, onName)
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun queryAll(socket: MulticastSocket, group: InetAddress, ranges: List<NetworkRange>) {
        val dest = InetSocketAddress(group, PORT)
        BROWSE.forEach { name ->
            send(socket, DnsPackets.query(name, DnsPackets.TYPE_PTR, unicast = true), dest)
        }
        ranges.forEach { range ->
            range.localIp?.let { ip ->
                send(socket, DnsPackets.query(DnsPackets.reversePtr(ip), DnsPackets.TYPE_PTR, unicast = true), dest)
            }
        }
    }

    fun queryHost(ip: String) {
        try {
            MulticastSocket().use { socket ->
                socket.soTimeout = 200
                val q = DnsPackets.query(DnsPackets.reversePtr(ip), DnsPackets.TYPE_PTR, unicast = true)
                socket.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), PORT))
                socket.send(DatagramPacket(q, q.size, InetAddress.getByName(GROUP), PORT))
            }
        } catch (_: Exception) {
        }
    }

    private fun send(socket: MulticastSocket, payload: ByteArray, dest: InetSocketAddress) {
        runCatching { socket.send(DatagramPacket(payload, payload.size, dest.address, dest.port)) }
    }

    private fun ingest(
        buf: ByteArray,
        length: Int,
        sourceIp: String?,
        names: ConcurrentHashMap<String, String>,
        onName: (String, String) -> Unit,
    ) {
        namesFromPacket(buf, length, sourceIp).forEach { (ip, host) ->
            val cleaned = DnsPackets.pretty(host.substringBefore("._"))
            if (cleaned.isBlank() || looksLikeIp(cleaned) || cleaned.startsWith("_")) return@forEach
            val previous = names.putIfAbsent(ip, cleaned)
            if (previous == null) onName(ip, cleaned)
        }
    }

    private fun arpaToIp(name: String): String? {
        val parts = name.removeSuffix(".in-addr.arpa").split('.')
        if (parts.size != 4) return null
        return parts.reversed().joinToString(".")
    }

    private fun looksLikeIp(value: String): Boolean =
        value.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    private fun interfaceFor(localIp: String?): NetworkInterface? {
        if (localIp.isNullOrBlank()) return null
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().firstOrNull { ni ->
                ni.inetAddresses.toList().any { it.hostAddress == localIp }
            }
        }.getOrNull()
    }
}
