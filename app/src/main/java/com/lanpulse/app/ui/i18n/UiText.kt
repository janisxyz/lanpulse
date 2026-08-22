package com.lanpulse.app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.lanpulse.app.model.DeviceKind
import com.lanpulse.app.model.LanDevice
import java.util.Locale

data class AppLanguage(val tag: String, val nativeName: String)

val AppLanguages = listOf(
    AppLanguage("", ""),
    AppLanguage("en", "English"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("fr", "Français"),
    AppLanguage("it", "Italiano"),
    AppLanguage("es", "Español"),
    AppLanguage("pt", "Português"),
    AppLanguage("nl", "Nederlands"),
    AppLanguage("pl", "Polski"),
    AppLanguage("cs", "Čeština"),
    AppLanguage("sv", "Svenska"),
    AppLanguage("nb", "Norsk"),
    AppLanguage("da", "Dansk"),
    AppLanguage("fi", "Suomi"),
    AppLanguage("el", "Ελληνικά"),
    AppLanguage("ro", "Română"),
    AppLanguage("hu", "Magyar"),
    AppLanguage("ru", "Русский"),
    AppLanguage("uk", "Українська"),
    AppLanguage("tr", "Türkçe"),
    AppLanguage("ar", "العربية"),
    AppLanguage("hi", "हिन्दी"),
    AppLanguage("th", "ไทย"),
    AppLanguage("vi", "Tiếng Việt"),
    AppLanguage("id", "Bahasa Indonesia"),
    AppLanguage("ja", "日本語"),
    AppLanguage("ko", "한국어"),
    AppLanguage("zh", "简体中文"),
    AppLanguage("zh-TW", "繁體中文"),
)

data class UiText(
    val appName: String,
    val settings: String,
    val discover: String,
    val hosts: String,
    val radar: String,
    val scan: String,
    val stop: String,
    val close: String,
    val searchHint: String,
    val noRanges: String,
    val sweeping: String,
    val devicesRanges: String,
    val hostsEmpty: String,
    val hostsHint: String,
    val hostsNoScan: String,
    val hostsNoMatch: String,
    val hostsCount: String,
    val radarTitle: String,
    val radarSubtitle: String,
    val thisPhone: String,
    val gateway: String,
    val unknownDevice: String,
    val youBadge: String,
    val gwBadge: String,
    val customName: String,
    val nameThisDevice: String,
    val saveName: String,
    val ping: String,
    val type: String,
    val ports: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val services: String,
    val notAdvertised: String,
    val noArp: String,
    val unknown: String,
    val ssh: String,
    val sshDots: String,
    val quickScan: String,
    val allPorts: String,
    val scanningProgress: String,
    val scanDone: String,
    val copyIp: String,
    val username: String,
    val password: String,
    val port: String,
    val rememberPassword: String,
    val connect: String,
    val connecting: String,
    val hangUp: String,
    val command: String,
    val connectedHint: String,
    val language: String,
    val languageSystem: String,
    val languageHint: String,
    val appearance: String,
    val appearanceSystem: String,
    val appearanceLight: String,
    val appearanceDark: String,
    val color: String,
    val colorDynamic: String,
    val colorTeal: String,
    val colorRaspberry: String,
    val colorIndigo: String,
    val colorAmber: String,
    val colorForest: String,
    val settingsStayOnPhone: String,
    val privacyPolicy: String = "Privacy policy",
    val errAuth: String,
    val errTimeout: String,
    val errRefused: String,
    val errUnreachable: String,
    val errGeneric: String,
    val errClosed: String,
    val portOpen: String,
    val kindGateway: String,
    val kindYou: String,
    val kindComputer: String,
    val kindPhone: String,
    val kindNas: String,
    val kindPrinter: String,
    val kindTv: String,
    val kindCamera: String,
    val kindIot: String,
    val kindConsole: String,
    val kindServer: String,
    val kindAp: String,
    val kindUnknown: String,
    val rtl: Boolean = false,
) {
    fun kindLabel(kind: DeviceKind): String = when (kind) {
        DeviceKind.GATEWAY -> kindGateway
        DeviceKind.YOU -> kindYou
        DeviceKind.COMPUTER -> kindComputer
        DeviceKind.PHONE -> kindPhone
        DeviceKind.NAS -> kindNas
        DeviceKind.PRINTER -> kindPrinter
        DeviceKind.TV -> kindTv
        DeviceKind.CAMERA -> kindCamera
        DeviceKind.IOT -> kindIot
        DeviceKind.CONSOLE -> kindConsole
        DeviceKind.SERVER -> kindServer
        DeviceKind.AP -> kindAp
        DeviceKind.UNKNOWN -> kindUnknown
    }

    fun deviceLabel(device: LanDevice): String = when {
        !device.customName.isNullOrBlank() -> device.customName.orEmpty()
        !device.hostname.isNullOrBlank() -> device.hostname.orEmpty()
        device.isYou -> thisPhone
        device.isGateway -> gateway
        !device.vendor.isNullOrBlank() -> device.vendor.orEmpty()
        else -> unknownDevice
    }

    fun sshError(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return when {
            code == "auth" -> errAuth
            code == "timeout" -> errTimeout
            code.startsWith("refused:") -> errRefused.format(code.substringAfter(":"))
            code == "unreach" -> errUnreachable
            code == "closed" -> errClosed
            code == "err" -> errGeneric
            else -> code
        }
    }

    companion object {
        val En = UiText(
            appName = "LanPulse",
            settings = "Settings",
            discover = "Discover",
            hosts = "Hosts",
            radar = "Radar",
            scan = "Scan",
            stop = "Stop",
            close = "Close",
            searchHint = "Name, IP, vendor",
            noRanges = "Connect to Wi-Fi or VPN — ranges appear automatically.",
            sweeping = "Sweeping %s",
            devicesRanges = "%1\$d devices · %2\$d ranges",
            hostsEmpty = "Sweep the LAN to fill this list.",
            hostsHint = "%1\$d of %2\$d · tap a row for ports, ping, SSH",
            hostsNoScan = "No hosts yet. Hit scan on Discover.",
            hostsNoMatch = "No match for “%s”.",
            hostsCount = "%d hosts",
            radarTitle = "RADAR",
            radarSubtitle = "Distance by ping",
            thisPhone = "This phone",
            gateway = "Gateway",
            unknownDevice = "Unknown device",
            youBadge = "YOU",
            gwBadge = "GW",
            customName = "Custom name",
            nameThisDevice = "Name this device",
            saveName = "Save name",
            ping = "Ping",
            type = "Type",
            ports = "Ports",
            hostname = "Hostname",
            mac = "MAC",
            vendor = "Vendor",
            services = "Services",
            notAdvertised = "Not advertised",
            noArp = "No ARP entry",
            unknown = "Unknown",
            ssh = "SSH",
            sshDots = "SSH…",
            quickScan = "Quick · 1k",
            allPorts = "All ports",
            scanningProgress = "Scanning %1\$s / %2\$s",
            scanDone = "Done · %1\$d open · %2\$s probed",
            copyIp = "Copy IP",
            username = "Username",
            password = "Password",
            port = "Port",
            rememberPassword = "Remember password on this phone",
            connect = "Connect",
            connecting = "Connecting…",
            hangUp = "Hang up",
            command = "Command",
            connectedHint = "Connected. Type a command below.",
            language = "Language",
            languageSystem = "System default",
            languageHint = "UI language. Follows the phone unless you pick one.",
            appearance = "Appearance",
            appearanceSystem = "System",
            appearanceLight = "Light",
            appearanceDark = "Dark",
            color = "Color",
            colorDynamic = "Dynamic",
            colorTeal = "Teal",
            colorRaspberry = "Raspberry",
            colorIndigo = "Indigo",
            colorAmber = "Amber",
            colorForest = "Forest",
            settingsStayOnPhone = "Language and theme stay on this phone. Nothing is uploaded.",
            errAuth = "Wrong username or password",
            errTimeout = "Timed out — is SSH open?",
            errRefused = "Connection refused on port %s",
            errUnreachable = "Host unreachable",
            errGeneric = "Could not connect",
            errClosed = "Session closed",
            portOpen = "open",
            kindGateway = "Gateway",
            kindYou = "This phone",
            kindComputer = "Computer",
            kindPhone = "Phone",
            kindNas = "NAS",
            kindPrinter = "Printer",
            kindTv = "TV",
            kindCamera = "Camera",
            kindIot = "IoT",
            kindConsole = "Console",
            kindServer = "Server",
            kindAp = "Access point",
            kindUnknown = "Unknown",
        )
    }
}

val LocalUiText = staticCompositionLocalOf { UiText.En }

fun resolveUiText(languageTag: String): UiText {
    val raw = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
    val tag = raw.lowercase(Locale.ROOT)
    return when {
        tag.startsWith("zh-tw") || tag.startsWith("zh-hant") || tag == "zh-hk" -> Translations.ZhTw
        tag.startsWith("zh") -> Translations.Zh
        tag.startsWith("pt") -> Translations.Pt
        tag.startsWith("nb") || tag.startsWith("nn") || tag == "no" -> Translations.Nb
        tag.startsWith("de") -> Translations.De
        tag.startsWith("fr") -> Translations.Fr
        tag.startsWith("it") -> Translations.It
        tag.startsWith("es") -> Translations.Es
        tag.startsWith("nl") -> Translations.Nl
        tag.startsWith("pl") -> Translations.Pl
        tag.startsWith("cs") -> Translations.Cs
        tag.startsWith("sv") -> Translations.Sv
        tag.startsWith("da") -> Translations.Da
        tag.startsWith("fi") -> Translations.Fi
        tag.startsWith("el") -> Translations.El
        tag.startsWith("ro") -> Translations.Ro
        tag.startsWith("hu") -> Translations.Hu
        tag.startsWith("ru") -> Translations.Ru
        tag.startsWith("uk") -> Translations.Uk
        tag.startsWith("tr") -> Translations.Tr
        tag.startsWith("ar") -> Translations.Ar
        tag.startsWith("hi") -> Translations.Hi
        tag.startsWith("th") -> Translations.Th
        tag.startsWith("vi") -> Translations.Vi
        tag.startsWith("id") -> Translations.Id
        tag.startsWith("ja") -> Translations.Ja
        tag.startsWith("ko") -> Translations.Ko
        else -> UiText.En
    }
}
