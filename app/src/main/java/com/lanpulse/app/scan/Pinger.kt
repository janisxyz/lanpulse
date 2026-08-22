package com.lanpulse.app.scan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.math.roundToInt

data class PingResult(val reachable: Boolean, val rttMs: Float?)

object Pinger {
    private val timeRe = Regex("time[=<]([0-9.]+)")
    private val liveTcpPorts = intArrayOf(
        80, 443, 22, 53, 445, 139, 8080, 8443, 5357, 62078, 8123, 548, 5900, 5000, 111, 1883, 9100,
    )

    suspend fun ping(ip: String, timeoutMs: Int = 800): PingResult = coroutineScope {
        val done = CompletableDeferred<PingResult>()
        val jobs = listOf(
            async(Dispatchers.IO) { pingBinary(ip, timeoutMs).takeIf { it.reachable }?.let { done.complete(it) } },
            async(Dispatchers.IO) { tcpAlive(ip).takeIf { it.reachable }?.let { done.complete(it) } },
            async(Dispatchers.IO) { Mdns.probe(ip).takeIf { it.reachable }?.let { done.complete(it) } },
        )
        val result = withTimeoutOrNull(timeoutMs.toLong()) { done.await() } ?: PingResult(false, null)
        jobs.forEach { it.cancel() }
        result
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

    private suspend fun tcpAlive(ip: String): PingResult = coroutineScope {
        val done = CompletableDeferred<PingResult>()
        val jobs = liveTcpPorts.map { port ->
            async(Dispatchers.IO) {
                val started = System.nanoTime()
                try {
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(ip, port), 220)
                        done.complete(PingResult(true, (System.nanoTime() - started) / 1_000_000f))
                    }
                } catch (e: ConnectException) {
                    done.complete(PingResult(true, (System.nanoTime() - started) / 1_000_000f))
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    val msg = e.message?.lowercase().orEmpty()
                    if ("refused" in msg || "econnreset" in msg || "reset" in msg) {
                        done.complete(PingResult(true, (System.nanoTime() - started) / 1_000_000f))
                    }
                }
            }
        }
        val result = withTimeoutOrNull(260) { done.await() } ?: PingResult(false, null)
        jobs.forEach { it.cancel() }
        result
    }
}
