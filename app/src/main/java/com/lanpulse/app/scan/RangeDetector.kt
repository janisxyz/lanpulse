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
            val iface = lp.interfaceName.orEmpty()

            lp.linkAddresses.forEach { la ->
                val addr = la.address
                if (addr !is Inet4Address || addr.isLoopbackAddress) return@forEach
                val ip = addr.hostAddress ?: return@forEach
                put(
                    found,
                    ip = ip,
                    prefixHint = la.prefixLength,
                    kind = inferKind(kind, ip, iface),
                    iface = iface,
                    gateway = gateway,
                    dns = dns,
                    localIp = ip,
                )
            }

            lp.routes.forEach { route ->
                val dest = route.destination ?: return@forEach
                val addr = dest.address as? Inet4Address ?: return@forEach
                if (dest.prefixLength == 0 || dest.prefixLength > 30) return@forEach
                val ip = addr.hostAddress ?: return@forEach
                if (!Cidr.isRfc1918(ip)) return@forEach
                val routeGw = (route.gateway as? Inet4Address)?.hostAddress ?: gateway
                put(
                    found,
                    ip = ip,
                    prefixHint = dest.prefixLength,
                    kind = if (kind == RangeKind.WIFI || kind == RangeKind.ETHERNET) RangeKind.VLAN else kind,
                    iface = iface,
                    gateway = routeGw,
                    dns = dns,
                    localIp = null,
                    labelOverride = if (found.containsKey(Cidr.cidr(Cidr.network(ip, Cidr.scanPrefix(dest.prefixLength)), Cidr.scanPrefix(dest.prefixLength)))) {
                        null
                    } else {
                        "VLAN"
                    },
                )
            }
        }

        dhcpRange(context)?.let { dhcp ->
            val existing = found[dhcp.id]
            if (existing == null) found[dhcp.id] = dhcp
        }

        return found.values.toList()
    }

    private fun put(
        found: LinkedHashMap<String, NetworkRange>,
        ip: String,
        prefixHint: Int,
        kind: RangeKind,
        iface: String,
        gateway: String?,
        dns: List<String>,
        localIp: String?,
        labelOverride: String? = null,
    ) {
        val prefix = Cidr.scanPrefix(prefixHint)
        val networkAddr = Cidr.network(ip, prefix)
        val cidr = "$networkAddr/$prefix"
        val previous = found[cidr]
        val inferred = inferKind(kind, ip, iface)
        found[cidr] = NetworkRange(
            id = cidr,
            cidr = cidr,
            network = networkAddr,
            prefix = prefix,
            kind = previous?.kind ?: inferred,
            label = previous?.label ?: (labelOverride ?: labelFor(inferred, iface)),
            interfaceName = previous?.interfaceName?.ifBlank { iface } ?: iface,
            localIp = localIp ?: previous?.localIp,
            gateway = gateway ?: previous?.gateway ?: Cidr.longToIp(Cidr.ipToLong(networkAddr) + 1),
            dns = dns.ifEmpty { previous?.dns.orEmpty() },
            hostCount = Cidr.hostCount(prefix),
        )
    }

    private fun dhcpRange(context: Context): NetworkRange? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val dhcp = wm.dhcpInfo ?: return null
            val ip = intToIp(dhcp.ipAddress) ?: return null
            val prefix = Cidr.scanPrefix(Cidr.prefixFromMaskLe(dhcp.netmask).takeIf { it in 8..30 } ?: 24)
            val gw = intToIp(dhcp.gateway)
            val networkAddr = Cidr.network(ip, prefix)
            NetworkRange(
                id = "$networkAddr/$prefix",
                cidr = "$networkAddr/$prefix",
                network = networkAddr,
                prefix = prefix,
                kind = RangeKind.WIFI,
                label = "Wi-Fi",
                interfaceName = "wlan0",
                localIp = ip,
                gateway = gw,
                dns = listOfNotNull(intToIp(dhcp.dns1), intToIp(dhcp.dns2)),
                hostCount = Cidr.hostCount(prefix),
            )
        } catch (_: Exception) {
            null
        }
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
        RangeKind.VLAN -> "VLAN"
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
