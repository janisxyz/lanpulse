package com.lanpulse.app.scan

import java.io.File

object ArpTable {
    private val arpLine = Regex(
        """(\d{1,3}(?:\.\d{1,3}){3})\s+\S+\s+(\S+)\s+\S+\s+(\S+)""",
    )
    private val neighLine = Regex(
        """(\d{1,3}(?:\.\d{1,3}){3})\s+.*lladdr\s+([0-9a-fA-F:]{11,17})""",
    )

    fun macFor(ip: String): String? = snapshot()[ip]

    fun snapshot(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        readProcArp(out)
        readIpNeigh(out)
        return out
    }

    private fun readProcArp(out: MutableMap<String, String>) {
        val file = File("/proc/net/arp")
        if (!file.canRead()) return
        file.readLines().drop(1).forEach { line ->
            val m = arpLine.find(line.trim()) ?: return@forEach
            val ip = m.groupValues[1]
            val mac = m.groupValues[3].lowercase()
            if (mac != "00:00:00:00:00:00" && !mac.contains("incomplete")) {
                out[ip] = mac
            }
        }
    }

    private fun readIpNeigh(out: MutableMap<String, String>) {
        try {
            val proc = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            text.lineSequence().forEach { line ->
                val m = neighLine.find(line) ?: return@forEach
                out[m.groupValues[1]] = m.groupValues[2].lowercase()
            }
        } catch (_: Exception) {
            // toybox ip may be missing
        }
    }
}
