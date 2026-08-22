package com.lanpulse.app.scan

import com.lanpulse.app.model.DeviceKind
import com.lanpulse.app.model.OpenPort

object DeviceClassifier {
    fun classify(
        hostname: String?,
        vendor: String?,
        ports: List<OpenPort>,
        isGateway: Boolean,
        isYou: Boolean,
    ): Pair<DeviceKind, List<String>> {
        if (isYou) return DeviceKind.YOU to listOf("This device")
        if (isGateway) return DeviceKind.GATEWAY to listOf("Gateway", "DHCP")

        val h = hostname.orEmpty().lowercase()
        val v = vendor.orEmpty().lowercase()
        val open = ports.map { it.port }.toSet()
        val services = ports.map { it.service }.distinct()

        fun has(vararg p: Int) = p.any { it in open }

        val kind = when {
            "udm" in h || "unifi" in h || "gateway" in h || "router" in h -> DeviceKind.GATEWAY
            "iphone" in h || "pixel" in h || "galaxy" in h || "android" in h -> DeviceKind.PHONE
            "ipad" in h -> DeviceKind.PHONE
            "apple-tv" in h || "chromecast" in h || "bravia" in h || "roku" in h || "firetv" in h -> DeviceKind.TV
            "ps5" in h || "ps4" in h || "xbox" in h || "switch" in h -> DeviceKind.CONSOLE
            "printer" in h || "brother" in h || "hp-print" in h || has(631, 9100, 515) -> DeviceKind.PRINTER
            "diskstation" in h || "synology" in h || "truenas" in h || "qnap" in h -> DeviceKind.NAS
            has(445, 139) && has(5000, 5001) -> DeviceKind.NAS
            "reolink" in h || "amcrest" in h || "camera" in h || has(554) -> DeviceKind.CAMERA
            "shelly" in h || "tasmota" in h || "esp" in h || "hue" in h -> DeviceKind.IOT
            "raspberry" in v || "raspberry" in h || "dietpi" in h || "octopi" in h ||
                "retropie" in h || "pihole" in h || "pi-hole" in h || h == "pi" ||
                h.startsWith("raspberrypi") -> DeviceKind.SERVER
            "homeassistant" in h || "home-assistant" in h || has(8123) -> DeviceKind.SERVER
            "ubiquiti" in v || "ubnt" in v || "unifi" in h || "u7-" in h || "u6-" in h || "uap" in h ->
                if (isGateway) DeviceKind.GATEWAY else DeviceKind.AP
            has(22) && "pi" in h -> DeviceKind.SERVER
            "apple" in v && has(62078) -> DeviceKind.PHONE
            "apple" in v && has(548, 5900, 22) -> DeviceKind.COMPUTER
            has(3389, 445, 135) -> DeviceKind.COMPUTER
            has(22, 5900, 548) -> DeviceKind.COMPUTER
            has(8008, 8009) -> DeviceKind.TV
            has(32400) -> DeviceKind.SERVER
            "espressif" in v -> DeviceKind.IOT
            else -> DeviceKind.UNKNOWN
        }

        return kind to services.ifEmpty { listOf(kind.name.lowercase().replaceFirstChar { it.titlecase() }) }
    }

    fun dnsHostname(ip: String): String? {
        return try {
            val host = java.net.InetAddress.getByName(ip).canonicalHostName ?: return null
            val pretty = DnsPackets.pretty(host)
            if (pretty.isBlank() || pretty == ip || pretty.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) null
            else pretty
        } catch (_: Exception) {
            null
        }
    }
}
