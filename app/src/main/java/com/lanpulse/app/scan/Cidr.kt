package com.lanpulse.app.scan

object Cidr {
    fun ipToLong(ip: String): Long {
        val p = ip.split('.')
        if (p.size != 4) return 0L
        return ((p[0].toLongOrNull() ?: 0) shl 24) or
            ((p[1].toLongOrNull() ?: 0) shl 16) or
            ((p[2].toLongOrNull() ?: 0) shl 8) or
            (p[3].toLongOrNull() ?: 0)
    }

    fun longToIp(n: Long): String =
        "${(n shr 24) and 255}.${(n shr 16) and 255}.${(n shr 8) and 255}.${n and 255}"

    fun network(ip: String, prefix: Int): String {
        val mask = if (prefix <= 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return longToIp(ipToLong(ip) and mask)
    }

    fun cidr(ip: String, prefix: Int): String = "${network(ip, prefix)}/$prefix"

    fun contains(network: String, prefix: Int, ip: String): Boolean =
        network(ip, prefix) == network(network, prefix)

    fun hostCount(prefix: Int): Int {
        if (prefix >= 31) return 2
        val raw = 1 shl (32 - prefix)
        return (raw - 2).coerceAtLeast(0)
    }

    fun hosts(network: String, prefix: Int, cap: Int = 1022): List<String> {
        val base = ipToLong(network(network, prefix))
        val count = hostCount(prefix).coerceAtMost(cap)
        val start = if (prefix >= 31) base else base + 1
        return (0 until count).map { longToIp(start + it) }
    }

    fun scanPrefix(prefix: Int): Int = when {
        prefix >= 30 -> 24
        prefix < 24 -> 24
        else -> prefix
    }

    fun isRfc1918(ip: String): Boolean {
        val n = ipToLong(ip)
        return (n and 0xFF000000L) == 0x0A000000L ||
            (n and 0xFFF00000L) == 0xAC100000L ||
            (n and 0xFFFF0000L) == 0xC0A80000L
    }

    fun broadcast(network: String, prefix: Int): String {
        val mask = if (prefix <= 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return longToIp((ipToLong(network) and mask) or (mask.inv() and 0xFFFFFFFFL))
    }

    fun prefixFromMaskLe(mask: Int): Int {
        if (mask == 0) return 24
        val bits = ((mask and 0xff) shl 24) or
            (((mask shr 8) and 0xff) shl 16) or
            (((mask shr 16) and 0xff) shl 8) or
            ((mask shr 24) and 0xff)
        return Integer.bitCount(bits)
    }
}
