package com.lanpulse.app.scan

import com.lanpulse.app.model.OpenPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PortCatalog {
    val COMMON = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 111 to "RPC", 135 to "MSRPC", 139 to "NetBIOS",
        143 to "IMAP", 161 to "SNMP", 389 to "LDAP", 443 to "HTTPS", 445 to "SMB",
        515 to "LPD", 548 to "AFP", 554 to "RTSP", 587 to "Submission", 631 to "IPP",
        993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL", 1521 to "Oracle",
        1883 to "MQTT", 2049 to "NFS", 3306 to "MySQL", 3389 to "RDP",
        5000 to "UPnP/DSM", 5001 to "DSM-SSL", 5353 to "mDNS", 5432 to "Postgres",
        5900 to "VNC", 6379 to "Redis", 8000 to "HTTP-alt", 8008 to "Cast",
        8009 to "Cast", 8080 to "HTTP-proxy", 8123 to "Home Assistant",
        8443 to "HTTPS-alt", 8888 to "HTTP-alt", 9000 to "HTTP-alt",
        9090 to "WebUI", 9100 to "JetDirect", 32400 to "Plex",
        25565 to "Minecraft", 27017 to "MongoDB", 62078 to "lockdownd",
        8291 to "Winbox", 51820 to "WireGuard",
    )

    val QUICK = COMMON.filter { it.first in QUICK_SET }

    private val QUICK_SET = setOf(
        22, 53, 80, 139, 443, 445, 548, 554, 631, 1883, 3306, 3389,
        5000, 5353, 5900, 8008, 8080, 8123, 8443, 9100, 32400, 62078,
    )
}

object PortScanner {
    suspend fun probeOpen(ip: String, ports: List<Int>, timeoutMs: Int = 180): List<OpenPort> =
        coroutineScope {
            val sem = Semaphore(32)
            ports.map { port ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        connect(ip, port, timeoutMs)
                    }
                }
            }.awaitAll().filterNotNull()
        }

    fun scan(ip: String, full: Boolean): Flow<Pair<Int, OpenPort?>> = flow {
        val list = if (full) PortCatalog.COMMON else PortCatalog.QUICK
        list.forEachIndexed { index, (port, _) ->
            val hit = withContext(Dispatchers.IO) { connect(ip, port, 220) }
            emit(index + 1 to hit)
        }
    }

    private fun connect(ip: String, port: Int, timeoutMs: Int): OpenPort? {
        val t0 = System.nanoTime()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
                sock.soTimeout = 180
                val rtt = ((System.nanoTime() - t0) / 1_000_000L).toInt()
                val banner = runCatching {
                    if (sock.getInputStream().available() > 0) {
                        val buf = ByteArray(64)
                        val n = sock.getInputStream().read(buf)
                        if (n > 0) String(buf, 0, n).trim().take(48) else null
                    } else null
                }.getOrNull()
                val service = PortCatalog.COMMON.find { it.first == port }?.second ?: "tcp"
                OpenPort(port, service, banner, rtt)
            }
        } catch (_: Exception) {
            null
        }
    }
}
