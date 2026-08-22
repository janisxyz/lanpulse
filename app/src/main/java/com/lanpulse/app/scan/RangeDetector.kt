package com.lanpulse.app.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.lanpulse.app.model.NetworkRange
import com.lanpulse.app.model.RangeKind
import com.lanpulse.app.model.WifiSnapshot
import java.net.Inet4Address

object RangeDetector {
    fun detect(context: Context): List<NetworkRange> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val found = LinkedHashMap<String, NetworkRange>()

        @Suppress("DEPRECATION")
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            val lp = cm.getLinkProperties(network) ?: return@forEach
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@forEach

            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> RangeKind.VPN
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> RangeKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> RangeKind.ETHERNET
                else -> RangeKind.OTHER
            }

            val gateway = lp.routes.firstOrNull { route ->
                val dest = route.destination
                val isDefault = dest == null || dest.prefixLength == 0
                isDefault && route.gateway is Inet4Address
            }?.gateway?.hostAddress

            val dns = lp.dnsServers.mapNotNull { it.hostAddress }

            lp.linkAddresses.forEach { la ->
                val addr = la.address
                if (addr !is Inet4Address || addr.isLoopbackAddress) return@forEach
                val ip = addr.hostAddress ?: return@forEach
                val prefix = Cidr.scanPrefix(la.prefixLength)
                val networkAddr = Cidr.network(ip, prefix)
                val cidr = "$networkAddr/$prefix"
                val inferred = inferKind(kind, ip, lp.interfaceName.orEmpty())
                found[cidr] = NetworkRange(
                    id = cidr,
                    cidr = cidr,
                    network = networkAddr,
                    prefix = prefix,
                    kind = inferred,
                    label = labelFor(inferred, lp.interfaceName.orEmpty()),
                    interfaceName = lp.interfaceName.orEmpty(),
                    localIp = ip,
                    gateway = gateway ?: Cidr.longToIp(Cidr.ipToLong(networkAddr) + 1),
                    dns = dns,
                    hostCount = Cidr.hostCount(prefix),
                )
            }
        }

        return found.values.toList()
    }

    fun wifi(context: Context): WifiSnapshot {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo
        val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "Not connected"
        return WifiSnapshot(
            ssid = ssid,
            bssid = info?.bssid,
            frequencyMhz = info?.frequency?.takeIf { it > 0 },
            rssi = info?.rssi,
            linkMbps = info?.linkSpeed?.takeIf { it > 0 },
            gateway = null,
            ip = intToIp(info?.ipAddress ?: 0),
        )
    }

    private fun inferKind(base: RangeKind, ip: String, iface: String): RangeKind {
        if (base == RangeKind.VPN) return RangeKind.VPN
        val ifc = iface.lowercase()
        if (ifc.startsWith("wg") || ifc.contains("tun") || ifc.contains("ppp")) return RangeKind.VPN
        if (ifc.startsWith("ap") || ifc.contains("softap") || ip.startsWith("192.168.43.")) return RangeKind.HOTSPOT
        if (ifc.startsWith("eth") || ifc.startsWith("usb")) return RangeKind.ETHERNET
        return base
    }

    private fun labelFor(kind: RangeKind, iface: String): String = when (kind) {
        RangeKind.WIFI -> "Wi-Fi"
        RangeKind.ETHERNET -> "Ethernet"
        RangeKind.VPN -> "VPN"
        RangeKind.HOTSPOT -> "Hotspot / USB"
        RangeKind.CELLULAR -> "Cellular"
        RangeKind.OTHER -> iface.ifBlank { "Interface" }
    }

    private fun intToIp(value: Int): String? {
        if (value == 0) return null
        return "${value and 0xff}.${(value shr 8) and 0xff}.${(value shr 16) and 0xff}.${(value shr 24) and 0xff}"
    }

    fun bandOf(freq: Int?): String = when {
        freq == null -> "—"
        freq in 2400..2500 -> "2.4 GHz"
        freq in 4900..5900 -> "5 GHz"
        freq >= 5900 -> "6 GHz"
        else -> "$freq MHz"
    }

    fun channelOf(freq: Int?): Int? {
        if (freq == null) return null
        return when {
            freq in 2412..2484 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5000) / 5
            else -> null
        }
    }
}
