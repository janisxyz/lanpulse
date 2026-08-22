package com.lanpulse.app.scan

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal object DnsPackets {
    const val TYPE_A = 1
    const val TYPE_PTR = 12
    const val TYPE_SRV = 33
    const val TYPE_AAAA = 28

    fun reversePtr(ip: String): String =
        ip.split('.').reversed().joinToString(".") + ".in-addr.arpa"

    fun query(name: String, type: Int, unicast: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeShort(0)
            dos.writeShort(0)
            dos.writeShort(1)
            dos.writeShort(0)
            dos.writeShort(0)
            dos.writeShort(0)
            name.trim('.').split('.').forEach { label ->
                val bytes = label.toByteArray(Charsets.UTF_8)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0)
            dos.writeShort(type)
            dos.writeShort(if (unicast) 0x8001 else 1)
        }
        return out.toByteArray()
    }

    fun parseRecords(data: ByteArray, length: Int): List<DnsRecord> {
        if (length < 12) return emptyList()
        val ancount = u16(data, 6)
        val nscount = u16(data, 8)
        val arcount = u16(data, 10)
        var offset = 12
        val questions = u16(data, 4)
        repeat(questions) {
            val (_, next) = readName(data, offset)
            offset = next + 4
            if (offset > length) return emptyList()
        }
        val records = mutableListOf<DnsRecord>()
        repeat(ancount + nscount + arcount) {
            if (offset + 10 > length) return records
            val (name, afterName) = readName(data, offset)
            offset = afterName
            if (offset + 10 > length) return records
            val type = u16(data, offset)
            offset += 8 // type, class, ttl
            val rdlen = u16(data, offset)
            offset += 2
            if (offset + rdlen > length) return records
            when (type) {
                TYPE_A -> if (rdlen == 4) {
                    val ip = listOf(0, 1, 2, 3).joinToString(".") { (data[offset + it].toInt() and 0xFF).toString() }
                    records += DnsRecord.A(pretty(name), ip)
                }
                TYPE_PTR -> {
                    val (ptr, _) = readName(data, offset)
                    records += DnsRecord.Ptr(pretty(name), pretty(ptr))
                }
                TYPE_SRV -> if (rdlen >= 6) {
                    val (target, _) = readName(data, offset + 6)
                    records += DnsRecord.Srv(pretty(name), pretty(target))
                }
            }
            offset += rdlen
        }
        return records
    }

    fun pretty(name: String): String = name.trim('.').removeSuffix(".local")

    private fun u16(data: ByteArray, i: Int): Int =
        ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)

    private fun readName(data: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var i = start
        var jumped = false
        var consumed = 0
        val seen = HashSet<Int>()
        var hops = 0
        while (i < data.size && hops++ < 20) {
            val len = data[i].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (!jumped) consumed = i + 1 - start
                    return labels.joinToString(".") to start + consumed
                }
                len and 0xC0 == 0xC0 -> {
                    if (i + 1 >= data.size) break
                    val ptr = ((len and 0x3F) shl 8) or (data[i + 1].toInt() and 0xFF)
                    if (!jumped) consumed = i + 2 - start
                    jumped = true
                    if (!seen.add(ptr)) break
                    i = ptr
                }
                else -> {
                    i += 1
                    if (i + len > data.size) break
                    labels += String(data, i, len, Charsets.UTF_8)
                    i += len
                }
            }
        }
        return labels.joinToString(".") to start + consumed.coerceAtLeast(1)
    }
}

internal sealed class DnsRecord {
    data class A(val name: String, val ip: String) : DnsRecord()
    data class Ptr(val name: String, val target: String) : DnsRecord()
    data class Srv(val name: String, val target: String) : DnsRecord()
}
