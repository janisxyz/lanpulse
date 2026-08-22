package com.lanpulse.app.scan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object Netbios {
    fun nameOf(ip: String, timeoutMs: Int = 280): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val query = nbstatQuery()
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 137))
                val buf = ByteArray(576)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                parseNbstat(buf, packet.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun nbstatQuery(): ByteArray {
        val out = ByteArray(50)
        out[0] = 0x10.toByte()
        out[1] = 0x32.toByte()
        out[5] = 1
        out[12] = 0x20
        val encoded = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toByteArray()
        System.arraycopy(encoded, 0, out, 13, 32)
        out[45] = 0x00
        out[46] = 0x00
        out[47] = 0x21
        out[48] = 0x00
        out[49] = 0x01
        return out
    }

    private fun parseNbstat(data: ByteArray, length: Int): String? {
        if (length < 57) return null
        var i = 12
        while (i < length && data[i] != 0.toByte()) {
            val len = data[i].toInt() and 0xFF
            if (len and 0xC0 == 0xC0) {
                i += 2
                break
            }
            i += 1 + len
        }
        if (i < length && data[i] == 0.toByte()) i += 1
        i += 10
        if (i >= length) return null
        val rdlen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
        i += 2
        if (i >= length || rdlen <= 0) return null
        val count = data[i].toInt() and 0xFF
        i += 1
        var best: String? = null
        repeat(count) {
            if (i + 18 > length) return best
            val raw = String(data, i, 15, Charsets.US_ASCII).trim()
            val suffix = data[i + 15].toInt() and 0xFF
            val flags = ((data[i + 16].toInt() and 0xFF) shl 8) or (data[i + 17].toInt() and 0xFF)
            i += 18
            if (raw.isBlank() || raw.startsWith("__")) return@repeat
            val group = flags and 0x8000 != 0
            if (!group && (suffix == 0x00 || suffix == 0x20)) {
                best = raw
            } else if (best == null) {
                best = raw
            }
        }
        return best
    }
}
