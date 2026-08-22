package com.lanpulse.app.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lanpulse.app.model.NetworkRange
import com.lanpulse.app.model.RangeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object SubnetHunter {
    private const val MAX_EXTRA = 12

    suspend fun hunt(context: Context, known: List<NetworkRange>): List<NetworkRange> = coroutineScope {
        val wifi = wifiNetwork(context)
        val found = LinkedHashMap<String, NetworkRange>()
        fun adopt(range: NetworkRange) {
            if (found.size + known.size >= MAX_EXTRA + known.size) return
            if (known.any { it.id == range.id } || found.containsKey(range.id)) return
            found[range.id] = range
        }

        val jobs = listOf(
            async(Dispatchers.IO) { unifiIps(known, wifi).forEach { adopt(rangeOf(it, "UniFi", RangeKind.VLAN)) } },
            async(Dispatchers.IO) { ssdpIps(wifi).forEach { adopt(rangeOf(it, "SSDP", RangeKind.VLAN)) } },
            async(Dispatchers.IO) { adjacentGateways(known).forEach { adopt(rangeOf(it, "VLAN", RangeKind.VLAN, gatewayHint = it)) } },
            async(Dispatchers.IO) { kernelRoutes().forEach { adopt(it) } },
        )
        jobs.awaitAll()
        found.values.toList()
    }

    fun rangeOf(
        ip: String,
        label: String,
        kind: RangeKind = RangeKind.VLAN,
        gatewayHint: String? = null,
    ): NetworkRange {
        val prefix = 24
        val network = Cidr.network(ip, prefix)
        val gw = gatewayHint ?: Cidr.longToIp(Cidr.ipToLong(network) + 1)
        return NetworkRange(
            id = "$network/$prefix",
            cidr = "$network/$prefix",
            network = network,
            prefix = prefix,
            kind = kind,
            label = label,
            interfaceName = "",
            localIp = null,
            gateway = gw,
            dns = emptyList(),
            hostCount = Cidr.hostCount(prefix),
        )
    }

    private suspend fun adjacentGateways(known: List<NetworkRange>): List<String> = coroutineScope {
        val candidates = linkedSetOf<String>()
        known.forEach { range ->
            val p = range.network.split('.')
            if (p.size != 4) return@forEach
            val a = p[0].toIntOrNull() ?: return@forEach
            val b = p[1].toIntOrNull() ?: return@forEach
            val c = p[2].toIntOrNull() ?: return@forEach
            if (a == 192 && b == 168) {
                val thirds = (0..16).toList() + listOf(c - 1, c + 1, c + 2, c + 10, 20, 24, 30, 40, 50, 64, 80, 100, 101, 200, 250)
                thirds.distinct().filter { it in 0..255 && it != c }.forEach { t ->
                    candidates += "192.168.$t.1"
                }
            }
            if (a == 10) {
                listOf("10.0.0.1", "10.0.1.1", "10.1.1.1", "10.8.0.1", "10.10.0.1", "10.10.10.1", "10.20.0.1", "10.42.0.1")
                    .forEach { candidates += it }
                if (c in 1..254) {
                    candidates += "10.$b.${c - 1}.1"
                    candidates += "10.$b.${c + 1}.1"
                }
            }
            if (a == 172 && b in 16..31) {
                listOf("172.16.0.1", "172.16.1.1", "172.17.0.1", "172.20.0.1", "172.31.0.1").forEach { candidates += it }
            }
        }
        val unseen = candidates.filter { ip ->
            Cidr.isRfc1918(ip) && known.none { Cidr.contains(it.network, it.prefix, ip) }
        }
        unseen.map { ip ->
            async(Dispatchers.IO) {
                val ping = Pinger.ping(ip, 450)
                ip.takeIf { ping.reachable }
            }
        }.awaitAll().filterNotNull()
    }

    private fun unifiIps(known: List<NetworkRange>, wifi: Network?): Set<String> {
        val ips = linkedSetOf<String>()
        val request = byteArrayOf(0x01, 0x00, 0x00, 0x00)
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.reuseAddress = true
                socket.soTimeout = 350
                wifi?.bindSocket(socket)
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), 10001))
                known.forEach { range ->
                    runCatching {
                        val bcast = Cidr.broadcast(range.network, range.prefix)
                        socket.send(DatagramPacket(request, request.size, InetAddress.getByName(bcast), 10001))
                    }
                }
                val deadline = System.currentTimeMillis() + 1_200
                val buf = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        parseUnifi(buf, packet.length, packet.address?.hostAddress).forEach { ips += it }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ips.filter { Cidr.isRfc1918(it) }.toSet()
    }

    private fun parseUnifi(data: ByteArray, length: Int, source: String?): List<String> {
        val ips = mutableListOf<String>()
        source?.let { ips += it }
        var i = 4
        while (i + 3 <= length) {
            val type = data[i].toInt() and 0xFF
            val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
            i += 3
            if (len < 0 || i + len > length) break
            if (type == 0x02 && len >= 10) {
                ipv4(data, i + 6)?.let { ips += it }
            } else if (len == 4) {
                ipv4(data, i)?.let { ips += it }
            }
            i += len
        }
        return ips
    }

    private fun ipv4(data: ByteArray, at: Int): String? {
        if (at + 4 > data.size) return null
        val ip = "${data[at].toInt() and 0xFF}.${data[at + 1].toInt() and 0xFF}.${data[at + 2].toInt() and 0xFF}.${data[at + 3].toInt() and 0xFF}"
        return ip.takeIf { Cidr.isRfc1918(it) }
    }

    private fun ssdpIps(wifi: Network?): Set<String> {
        val ips = linkedSetOf<String>()
        val body = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n"
        val payload = body.toByteArray()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 300
                socket.broadcast = true
                wifi?.bindSocket(socket)
                socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("239.255.255.250"), 1900))
                val deadline = System.currentTimeMillis() + 900
                val buf = ByteArray(1500)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        packet.address?.hostAddress?.let { ips += it }
                        val text = String(buf, 0, packet.length, Charsets.ISO_8859_1)
                        Regex("""https?://(\d{1,3}(?:\.\d{1,3}){3})""").findAll(text).forEach { ips += it.groupValues[1] }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ips.filter { Cidr.isRfc1918(it) }.toSet()
    }

    private fun kernelRoutes(): List<NetworkRange> {
        val out = mutableListOf<NetworkRange>()
        try {
            val proc = ProcessBuilder("ip", "-4", "route").redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val re = Regex("""(\d{1,3}(?:\.\d{1,3}){3})/(\d{1,2})""")
            text.lineSequence().forEach { line ->
                if (line.startsWith("default")) return@forEach
                val m = re.find(line) ?: return@forEach
                val ip = m.groupValues[1]
                val pfx = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (!Cidr.isRfc1918(ip) || pfx !in 16..30) return@forEach
                val prefix = Cidr.scanPrefix(pfx)
                val gw = Regex("""via (\d{1,3}(?:\.\d{1,3}){3})""").find(line)?.groupValues?.get(1)
                out += rangeOf(Cidr.network(ip, prefix) , "Route", RangeKind.VLAN, gatewayHint = gw)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun wifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
}
