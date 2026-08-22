package com.lanpulse.app.model

enum class RangeKind { WIFI, ETHERNET, VPN, HOTSPOT, CELLULAR, VLAN, OTHER }

enum class DeviceKind {
    GATEWAY, YOU, COMPUTER, PHONE, NAS, PRINTER, TV, CAMERA, IOT, CONSOLE, SERVER, AP, UNKNOWN
}

data class NetworkRange(
    val id: String,
    val cidr: String,
    val network: String,
    val prefix: Int,
    val kind: RangeKind,
    val label: String,
    val interfaceName: String,
    val localIp: String?,
    val gateway: String?,
    val dns: List<String>,
    val hostCount: Int,
)

data class OpenPort(
    val port: Int,
    val service: String,
    val banner: String? = null,
    val rttMs: Int? = null,
)

data class LanDevice(
    val ip: String,
    val rangeId: String,
    val hostname: String? = null,
    val customName: String? = null,
    val mac: String? = null,
    val vendor: String? = null,
    val kind: DeviceKind = DeviceKind.UNKNOWN,
    val pingMs: Float? = null,
    val openPorts: List<OpenPort> = emptyList(),
    val services: List<String> = emptyList(),
    val isGateway: Boolean = false,
    val isYou: Boolean = false,
    val online: Boolean = true,
) {
    val displayName: String
        get() = when {
            !customName.isNullOrBlank() -> customName
            !hostname.isNullOrBlank() -> hostname
            isYou -> "This phone"
            isGateway -> "Gateway"
            !vendor.isNullOrBlank() -> vendor
            else -> "Unknown device"
        }
}

data class WifiSnapshot(
    val ssid: String,
    val bssid: String?,
    val frequencyMhz: Int?,
    val rssi: Int?,
    val linkMbps: Int?,
    val gateway: String?,
    val ip: String?,
)

data class ScanProgress(
    val active: Boolean = false,
    val rangeLabel: String = "",
    val scanned: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val currentIp: String? = null,
)

data class PortScanProgress(
    val ip: String,
    val running: Boolean,
    val scanned: Int,
    val total: Int,
    val results: List<OpenPort>,
)
