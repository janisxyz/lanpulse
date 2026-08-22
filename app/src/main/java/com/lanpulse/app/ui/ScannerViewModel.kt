package com.lanpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanpulse.app.data.Accent
import com.lanpulse.app.data.AppPreferences
import com.lanpulse.app.data.DeviceNamesStore
import com.lanpulse.app.data.SshCredsStore
import com.lanpulse.app.data.ThemeMode
import com.lanpulse.app.model.DeviceKind
import com.lanpulse.app.model.LanDevice
import com.lanpulse.app.model.NetworkRange
import com.lanpulse.app.model.OpenPort
import com.lanpulse.app.model.PortScanProgress
import com.lanpulse.app.model.RangeKind
import com.lanpulse.app.model.ScanProgress
import com.lanpulse.app.model.WifiSnapshot
import com.lanpulse.app.scan.Cidr
import com.lanpulse.app.scan.HostScanner
import com.lanpulse.app.scan.Pinger
import com.lanpulse.app.scan.PortCatalog
import com.lanpulse.app.scan.PortScanner
import com.lanpulse.app.scan.RangeDetector
import com.lanpulse.app.scan.ScanEvent
import com.lanpulse.app.scan.SubnetHunter
import com.lanpulse.app.ssh.SshShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SshUi(
    val ip: String,
    val title: String,
    val port: Int = 22,
    val user: String,
    val password: String = "",
    val remember: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val output: String = "",
)

data class UiState(
    val wifi: WifiSnapshot = WifiSnapshot("LanPulse", null, null, null, null, null, null),
    val ranges: List<NetworkRange> = emptyList(),
    val devices: List<LanDevice> = emptyList(),
    val scan: ScanProgress = ScanProgress(),
    val query: String = "",
    val selectedIp: String? = null,
    val portScan: PortScanProgress? = null,
    val ssh: SshUi? = null,
    val languageTag: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.TEAL,
)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val names = DeviceNamesStore(app)
    private val sshCreds = SshCredsStore(app)
    private val prefs = AppPreferences(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var portJob: Job? = null
    private var sshReader: Job? = null
    private var shell: SshShell? = null

    init {
        val loaded = prefs.load()
        _state.update {
            it.copy(languageTag = loaded.languageTag, themeMode = loaded.themeMode, accent = loaded.accent)
        }
    }

    fun setLanguage(tag: String) {
        _state.update { it.copy(languageTag = tag) }
        persistPrefs()
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        persistPrefs()
    }

    fun setAccent(accent: Accent) {
        _state.update { it.copy(accent = accent) }
        persistPrefs()
    }

    private fun persistPrefs() {
        val s = _state.value
        prefs.save(com.lanpulse.app.data.AppSettings(s.languageTag, s.themeMode, s.accent))
    }

    fun refreshNetwork() {
        val ctx = getApplication<Application>()
        val ranges = RangeDetector.detect(ctx)
        val wifi = RangeDetector.wifi(ctx)
        val gw = ranges.firstOrNull { it.kind == RangeKind.WIFI }?.gateway
            ?: ranges.firstOrNull()?.gateway
        _state.update {
            it.copy(
                ranges = mergeRanges(it.ranges, ranges),
                wifi = wifi.copy(gateway = gw ?: wifi.gateway, ip = wifi.ip ?: ranges.firstOrNull()?.localIp),
            )
        }
        viewModelScope.launch {
            val extra = SubnetHunter.hunt(ctx, _state.value.ranges)
            if (extra.isNotEmpty()) {
                _state.update { s -> s.copy(ranges = mergeRanges(s.ranges, extra)) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun select(ip: String?) = _state.update { it.copy(selectedIp = ip) }

    fun rename(ip: String, mac: String?, name: String) {
        names.set(mac, ip, name)
        val custom = name.trim().ifBlank { null }
        _state.update { s ->
            s.copy(devices = s.devices.map { if (it.ip == ip) it.copy(customName = custom) else it })
        }
    }

    fun startScan() {
        val ranges = _state.value.ranges
        if (ranges.isEmpty() || _state.value.scan.active) return
        scanJob?.cancel()
        _state.update {
            it.copy(
                devices = it.devices.map { d -> d.copy(online = false) },
                scan = ScanProgress(active = true, total = ranges.sumOf { r -> r.hostCount }),
            )
        }
        val ctx = getApplication<Application>()
        scanJob = viewModelScope.launch {
            HostScanner.scan(ctx, ranges).collect { ev ->
                when (ev) {
                    is ScanEvent.Progress -> _state.update { s ->
                        s.copy(
                            scan = s.scan.copy(
                                scanned = ev.scanned,
                                total = ev.total,
                                found = ev.found,
                                rangeLabel = ev.rangeLabel,
                                currentIp = ev.ip,
                            ),
                        )
                    }
                    is ScanEvent.Host -> _state.update { s ->
                        s.copy(devices = upsert(s.devices, ev.device))
                    }
                    is ScanEvent.Range -> _state.update { s ->
                        s.copy(ranges = mergeRanges(s.ranges, listOf(ev.range)))
                    }
                    ScanEvent.Done -> _state.update { it.copy(scan = it.scan.copy(active = false)) }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(scan = it.scan.copy(active = false)) }
    }

    fun ping(ip: String) {
        viewModelScope.launch {
            val result = Pinger.ping(ip, 1000)
            _state.update { s ->
                s.copy(
                    devices = s.devices.map {
                        if (it.ip == ip) it.copy(pingMs = result.rttMs, online = result.reachable) else it
                    },
                )
            }
        }
    }

    fun startPortScan(ip: String, full: Boolean) {
        portJob?.cancel()
        val total = if (full) PortCatalog.ALL else PortCatalog.QUICK.size
        _state.update { it.copy(portScan = PortScanProgress(ip, true, 0, total, emptyList())) }
        portJob = viewModelScope.launch {
            val found = mutableListOf<OpenPort>()
            PortScanner.scan(ip, full).collect { (scanned, hit) ->
                if (hit != null) found += hit
                _state.update { s ->
                    val devices = if (hit != null) {
                        s.devices.map { d ->
                            if (d.ip == ip && d.openPorts.none { it.port == hit.port }) {
                                d.copy(openPorts = (d.openPorts + hit).sortedBy { it.port })
                            } else d
                        }
                    } else s.devices
                    s.copy(
                        devices = devices,
                        portScan = s.portScan?.copy(scanned = scanned, results = found.sortedBy { it.port }),
                    )
                }
            }
            _state.update { it.copy(portScan = it.portScan?.copy(running = false)) }
        }
    }

    fun stopPortScan() {
        portJob?.cancel()
        _state.update { it.copy(portScan = it.portScan?.copy(running = false)) }
    }

    fun openSsh(device: LanDevice) {
        val guess = guessUser(device)
        _state.update {
            it.copy(
                ssh = SshUi(
                    ip = device.ip,
                    title = device.displayName,
                    port = sshCreds.port(device.ip),
                    user = sshCreds.user(device.ip, guess),
                    password = sshCreds.password(device.ip),
                    remember = sshCreds.remember(device.ip),
                ),
            )
        }
    }

    fun closeSsh() {
        sshReader?.cancel()
        sshReader = null
        runCatching { shell?.close() }
        shell = null
        _state.update { it.copy(ssh = null) }
    }

    fun sshConnect(user: String, password: String, port: Int, remember: Boolean) {
        val current = _state.value.ssh ?: return
        if (current.connecting) return
        sshCreds.save(current.ip, user, password, port, remember)
        _state.update {
            it.copy(
                ssh = it.ssh?.copy(
                    user = user,
                    password = password,
                    port = port,
                    remember = remember,
                    connecting = true,
                    connected = false,
                    error = null,
                    output = "",
                ),
            )
        }
        sshReader?.cancel()
        runCatching { shell?.close() }
        viewModelScope.launch {
            try {
                val client = SshShell(File(getApplication<Application>().filesDir, "known_hosts"))
                withContext(Dispatchers.IO) { client.connect(current.ip, port, user.trim(), password) }
                shell = client
                _state.update { s ->
                    s.copy(ssh = s.ssh?.copy(connecting = false, connected = true, user = user.trim(), port = port))
                }
                sshReader = launch(Dispatchers.IO) {
                    val stream = client.inputStream() ?: return@launch
                    val buf = ByteArray(4096)
                    while (isActive) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        val chunk = SshShell.stripAnsi(String(buf, 0, n, Charsets.UTF_8))
                        if (chunk.isEmpty()) continue
                        _state.update { s ->
                            val out = ((s.ssh?.output ?: "") + chunk).takeLast(80_000)
                            s.copy(ssh = s.ssh?.copy(output = out))
                        }
                    }
                    _state.update { s ->
                        s.copy(ssh = s.ssh?.copy(connected = false, connecting = false, error = "closed"))
                    }
                }
            } catch (e: Exception) {
                runCatching { shell?.close() }
                shell = null
                val msg = e.message.orEmpty()
                val nice = when {
                    "Auth fail" in msg || "auth fail" in msg.lowercase() -> "auth"
                    "timeout" in msg.lowercase() || "timed out" in msg.lowercase() -> "timeout"
                    "Connection refused" in msg -> "refused:$port"
                    "Network is unreachable" in msg || "unreachable" in msg.lowercase() -> "unreach"
                    else -> msg.ifBlank { "err" }
                }
                _state.update { s ->
                    s.copy(ssh = s.ssh?.copy(connecting = false, connected = false, error = nice))
                }
            }
        }
    }

    fun sshSend(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { shell?.write(text) }
        }
    }

    fun sshDisconnect() {
        sshReader?.cancel()
        sshReader = null
        runCatching { shell?.close() }
        shell = null
        _state.update { s ->
            s.copy(ssh = s.ssh?.copy(connected = false, connecting = false))
        }
    }

    override fun onCleared() {
        closeSsh()
        super.onCleared()
    }

    private fun guessUser(device: LanDevice): String {
        val v = device.vendor.orEmpty().lowercase()
        val h = device.hostname.orEmpty().lowercase()
        return when {
            "raspberry" in v || "raspberry" in h || h == "pi" || h.startsWith("pi-") -> "pi"
            "ubiquiti" in v || "unifi" in h || "udm" in h -> "root"
            "synology" in v || "diskstation" in h -> "admin"
            device.kind == DeviceKind.GATEWAY -> "admin"
            else -> "root"
        }
    }

    private fun mergeRanges(current: List<NetworkRange>, incoming: List<NetworkRange>): List<NetworkRange> {
        val map = LinkedHashMap<String, NetworkRange>()
        current.forEach { map[it.id] = it }
        incoming.forEach { extra ->
            val old = map[extra.id]
            map[extra.id] = if (old == null) extra else old.copy(
                localIp = old.localIp ?: extra.localIp,
                gateway = old.gateway ?: extra.gateway,
                dns = old.dns.ifEmpty { extra.dns },
                label = if (old.kind == RangeKind.WIFI) old.label else extra.label,
            )
        }
        return map.values.toList()
    }

    private fun upsert(list: List<LanDevice>, incoming: LanDevice): List<LanDevice> {
        val custom = names.get(incoming.mac, incoming.ip)
            ?: incoming.mac?.let { names.get(it, incoming.ip) }
            ?: names.get(null, incoming.ip)
        val idx = list.indexOfFirst { it.ip == incoming.ip }
        val merged = if (idx < 0) {
            incoming.copy(customName = custom ?: incoming.customName, online = true)
        } else {
            val old = list[idx]
            old.copy(
                hostname = incoming.hostname?.takeIf { it.isNotBlank() } ?: old.hostname,
                customName = custom ?: old.customName,
                mac = incoming.mac ?: old.mac,
                vendor = incoming.vendor ?: old.vendor,
                kind = if (incoming.kind != DeviceKind.UNKNOWN) incoming.kind else old.kind,
                pingMs = incoming.pingMs ?: old.pingMs,
                openPorts = (old.openPorts + incoming.openPorts).distinctBy { it.port },
                services = (incoming.services + old.services).distinct().ifEmpty { old.services },
                isGateway = incoming.isGateway || old.isGateway,
                isYou = incoming.isYou || old.isYou,
                online = true,
            )
        }
        val next = if (idx < 0) list + merged else list.toMutableList().apply { set(idx, merged) }
        return next.sortedWith(
            compareByDescending<LanDevice> { it.isGateway }
                .thenByDescending { it.isYou }
                .thenByDescending { it.online }
                .thenBy { Cidr.ipToLong(it.ip) },
        )
    }
}
