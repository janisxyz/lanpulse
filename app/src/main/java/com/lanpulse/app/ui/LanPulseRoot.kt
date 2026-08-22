package com.lanpulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanpulse.app.model.DeviceKind
import com.lanpulse.app.model.LanDevice
import com.lanpulse.app.scan.RangeDetector
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun LanPulseRoot(vm: ScannerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val selected = state.devices.find { it.ip == state.selectedIp }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0 && selected == null,
                    onClick = {
                        tab = 0
                        vm.select(null)
                    },
                    icon = { Icon(Icons.Outlined.Wifi, contentDescription = "Discover") },
                    label = { Text("Discover") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = {
                        tab = 1
                        vm.select(null)
                    },
                    icon = { Icon(Icons.Outlined.TrackChanges, contentDescription = "Radar") },
                    label = { Text("Radar") },
                )
                NavigationBarItem(
                    selected = selected != null && tab == 0,
                    onClick = { },
                    icon = { Icon(Icons.Outlined.Dns, contentDescription = "Host") },
                    label = { Text("Host") },
                    enabled = selected != null,
                )
            }
        },
        floatingActionButton = {
            if (selected == null) {
                FloatingActionButton(
                    onClick = { if (state.scan.active) vm.stopScan() else vm.startScan() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(
                        if (state.scan.active) Icons.Outlined.Stop else Icons.Outlined.TrackChanges,
                        contentDescription = if (state.scan.active) "Stop" else "Scan",
                    )
                }
            }
        },
    ) { padding ->
        when {
            selected != null -> DevicePane(
                device = selected,
                portScan = state.portScan,
                padding = padding,
                onClose = { vm.select(null) },
                onPing = { vm.ping(selected.ip) },
                onQuick = { vm.startPortScan(selected.ip, false) },
                onFull = { vm.startPortScan(selected.ip, true) },
                onStopPorts = vm::stopPortScan,
            )
            tab == 1 -> RadarPane(state.devices, padding) { vm.select(it) }
            else -> DiscoverPane(state, padding, vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverPane(state: UiState, padding: PaddingValues, vm: ScannerViewModel) {
    val wifi = state.wifi
    val band = RangeDetector.bandOf(wifi.frequencyMhz)
    val ch = RangeDetector.channelOf(wifi.frequencyMhz)
    val filtered = state.devices.filter { d ->
        val q = state.query.trim().lowercase()
        if (q.isEmpty()) true
        else d.hostname.lowercase().contains(q) || d.ip.contains(q) || (d.vendor ?: "").lowercase().contains(q)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "LANPULSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(wifi.ssid, style = MaterialTheme.typography.headlineLarge)
                Text(
                    buildString {
                        append(band)
                        if (ch != null) append(" · ch $ch")
                        wifi.linkMbps?.let { append(" · ${it} Mbps") }
                        wifi.rssi?.let { append(" · ${it} dBm") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.ranges.forEach { r ->
                    val n = state.devices.count { it.rangeId == r.id }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .width(148.dp),
                        ) {
                            Text(
                                r.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(r.cidr, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            Text(
                                "$n hosts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.ranges.isEmpty()) {
                    Text(
                        "Connect to Wi-Fi or VPN — ranges appear automatically.",
                        Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            val progress = if (state.scan.total == 0) 0f else state.scan.scanned / state.scan.total.toFloat()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                LinearProgressIndicator(
                    progress = { if (state.scan.active) progress else 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.scan.active) "Sweeping ${state.scan.currentIp ?: state.scan.rangeLabel}"
                    else "${state.devices.size} devices · ${state.ranges.size} ranges",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        item {
            TextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Host, IP, vendor") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(8.dp))
        }
        items(filtered, key = { it.ip }) { device ->
            DeviceRow(device) { vm.select(device.ip) }
        }
    }
}

@Composable
private fun DeviceRow(device: LanDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(device.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.hostname,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (device.isYou) Badge("YOU")
                if (device.isGateway) Badge("GW")
            }
            Text(
                buildString {
                    append(device.ip)
                    device.vendor?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            device.pingMs?.let { if (it < 10) "%.1f ms".format(it) else "${it.toInt()} ms" } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = pingColor(device.pingMs),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun pingColor(ms: Float?): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        ms == null -> scheme.onSurfaceVariant
        ms < 8f -> Color(0xFF34D399)
        ms < 25f -> scheme.primary
        ms < 80f -> Color(0xFFE7C56A)
        else -> scheme.error
    }
}

@Composable
private fun RadarPane(devices: List<LanDevice>, padding: PaddingValues, onSelect: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "RADAR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text("Distance by ping", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(320.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val maxR = size.minDimension / 2f
                drawCircle(primary.copy(alpha = 0.08f), maxR)
                listOf(0.3f, 0.55f, 0.8f, 1f).forEach { t ->
                    drawCircle(outline.copy(alpha = 0.45f), maxR * t, c, style = Stroke(2f))
                }
                devices.forEachIndexed { i, d ->
                    val r = maxR * min(0.88f, 0.18f + (d.pingMs ?: 20f) / 70f)
                    val a = (i / devices.size.coerceAtLeast(1).toFloat()) * Math.PI * 2 + 0.4
                    val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                    drawCircle(if (d.isYou) Color(0xFF7DD3FC) else primary, 10f, p)
                }
                drawCircle(primary, 6f, c)
            }
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .navigationBarsPadding(),
        ) {
            items(devices, key = { it.ip }) { d ->
                DeviceRow(d) { onSelect(d.ip) }
            }
        }
    }
}

@Composable
private fun DevicePane(
    device: LanDevice,
    portScan: com.lanpulse.app.model.PortScanProgress?,
    padding: PaddingValues,
    onClose: () -> Unit,
    onPing: () -> Unit,
    onQuick: () -> Unit,
    onFull: () -> Unit,
    onStopPorts: () -> Unit,
) {
    val clip = LocalClipboardManager.current
    val mine = portScan?.takeIf { it.ip == device.ip }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iconFor(device.kind), null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.hostname, style = MaterialTheme.typography.titleLarge)
                    Text(device.ip, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Ping", device.pingMs?.let { "%.1f ms".format(it) } ?: "—", Modifier.weight(1f))
                StatCard(
                    "Type",
                    device.kind.name.lowercase().replaceFirstChar { it.titlecase() },
                    Modifier.weight(1f),
                )
                StatCard("Ports", device.openPorts.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            InfoLine("MAC", device.mac ?: "No ARP entry")
            InfoLine("Vendor", device.vendor ?: "Unknown")
            InfoLine("Services", device.services.joinToString(" · ").ifBlank { "—" })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                device.openPorts.forEach { p ->
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("${p.port}/${p.service}", fontFamily = FontFamily.Monospace) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = onPing, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Sensors, "Ping")
                }
                OutlinedButton(onClick = onQuick, modifier = Modifier.weight(1f).height(48.dp)) { Text("Quick ports") }
                OutlinedButton(onClick = onFull, modifier = Modifier.weight(1f).height(48.dp)) { Text("Deep scan") }
            }
        }
        if (mine != null) {
            item {
                Text(
                    if (mine.running) "Scanning ${mine.scanned}/${mine.total}" else "Scan complete",
                    style = MaterialTheme.typography.labelSmall,
                )
                LinearProgressIndicator(
                    progress = { mine.scanned / mine.total.coerceAtLeast(1).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp)),
                )
                if (mine.running) {
                    OutlinedButton(onClick = onStopPorts) { Text("Stop") }
                }
            }
            items(mine.results, key = { it.port }) { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.port}  ${p.service}", fontFamily = FontFamily.Monospace)
                    Text(
                        p.rttMs?.let { "${it} ms" } ?: "open",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { clip.setText(AnnotatedString(device.ip)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("Copy IP")
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun iconFor(kind: DeviceKind): ImageVector = when (kind) {
    DeviceKind.GATEWAY, DeviceKind.AP -> Icons.Outlined.Router
    DeviceKind.YOU, DeviceKind.PHONE -> Icons.Outlined.Sensors
    else -> Icons.Outlined.Dns
}
