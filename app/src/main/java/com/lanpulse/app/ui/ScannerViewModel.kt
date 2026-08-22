package com.lanpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanpulse.app.data.DeviceNamesStore
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val wifi: WifiSnapshot = WifiSnapshot("LanPulse", null, null, null, null, null, null),
    val ranges: List<NetworkRange> = emptyList(),
    val devices: List<LanDevice> = emptyList(),
    val scan: ScanProgress = ScanProgress(),
    val query: String = "",
    val selectedIp: String? = null,
    val portScan: PortScanProgress? = null,
)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val names = DeviceNamesStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var portJob: Job? = null

    fun refreshNetwork() {
        val ctx = getApplication<Application>()
        val ranges = RangeDetector.detect(ctx)
        val wifi = RangeDetector.wifi(ctx)
        val gw = ranges.firstOrNull { it.kind == RangeKind.WIFI }?.gateway
            ?: ranges.firstOrNull()?.gateway
        _state.update {
            it.copy(
                ranges = ranges,
                wifi = wifi.copy(gateway = gw ?: wifi.gateway, ip = wifi.ip ?: ranges.firstOrNull()?.localIp),
            )
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
