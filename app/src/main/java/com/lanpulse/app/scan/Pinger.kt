package com.lanpulse.app.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

data class PingResult(val reachable: Boolean, val rttMs: Float?)

object Pinger {
    private val timeRe = Regex("time[=<]([0-9.]+)")

    suspend fun ping(ip: String, timeoutMs: Int = 800): PingResult = withContext(Dispatchers.IO) {
        val fromBin = pingBinary(ip, timeoutMs)
        if (fromBin.reachable) return@withContext fromBin
        tcpProbe(ip, timeoutMs)
    }

    private fun pingBinary(ip: String, timeoutMs: Int): PingResult {
        return try {
            val waitSec = ((timeoutMs / 1000f).coerceAtLeast(1f)).roundToInt().toString()
            val proc = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", waitSec, ip)
                .redirectErrorStream(true)
                .start()
            val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            val ok = proc.waitFor() == 0
            val rtt = timeRe.find(out)?.groupValues?.get(1)?.toFloatOrNull()
            PingResult(ok, rtt)
        } catch (_: Exception) {
            PingResult(false, null)
        }
    }

    private fun tcpProbe(ip: String, timeoutMs: Int): PingResult {
        val ports = intArrayOf(80, 443, 22, 445, 53, 8080, 5353, 62078)
        for (port in ports) {
            val started = System.nanoTime()
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(ip, port), timeoutMs.coerceAtMost(400))
                    val rtt = (System.nanoTime() - started) / 1_000_000f
                    return PingResult(true, rtt)
                }
            } catch (_: Exception) {
                // closed or filtered — try next
            }
        }
        return PingResult(false, null)
    }
}
