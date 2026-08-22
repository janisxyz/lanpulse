package com.lanpulse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SshPane(
    ssh: SshUi,
    padding: PaddingValues,
    onClose: () -> Unit,
    onConnect: (user: String, password: String, port: Int, remember: Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onSend: (String) -> Unit,
) {
    var user by remember(ssh.ip) { mutableStateOf(ssh.user) }
    var password by remember(ssh.ip) { mutableStateOf(ssh.password) }
    var port by remember(ssh.ip) { mutableStateOf(ssh.port.toString()) }
    var rememberPw by remember(ssh.ip) { mutableStateOf(ssh.remember) }
    var showPw by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(ssh.output) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ssh.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ssh.user}@${ssh.ip}:${ssh.port}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
        }
        Spacer(Modifier.height(12.dp))
        if (!ssh.connected) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onConnect(user, password, port.toIntOrNull() ?: 22, rememberPw)
                    },
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rememberPw, onCheckedChange = { rememberPw = it })
                Text("Remember password on this phone", style = MaterialTheme.typography.bodySmall)
            }
            ssh.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { onConnect(user, password, port.toIntOrNull() ?: 22, rememberPw) },
                enabled = !ssh.connecting && user.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (ssh.connecting) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (ssh.connecting) "Connecting…" else "Connect")
            }
        } else {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    ssh.output.ifBlank { "Connected. Type a command below." },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Ctrl-C" to "\u0003", "Tab" to "\t", "Esc" to "\u001b", "↑" to "\u001b[A").forEach { (label, seq) ->
                    FilledTonalButton(
                        onClick = { onSend(seq) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.height(36.dp)) { Text("Hang up") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = line,
                onValueChange = { line = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Ascii),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSend(line + "\n")
                        line = ""
                    },
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSend(line + "\n")
                            line = ""
                        },
                    ) { Icon(Icons.Outlined.Terminal, "Send") }
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
