package com.lanpulse.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanpulse.app.BuildConfig
import com.lanpulse.app.data.Accent
import com.lanpulse.app.data.ThemeMode
import com.lanpulse.app.ui.i18n.AppLanguages
import com.lanpulse.app.ui.i18n.LocalUiText
import com.lanpulse.app.ui.theme.accentSwatch

@Composable
fun SettingsPane(
    languageTag: String,
    themeMode: ThemeMode,
    accent: Accent,
    padding: PaddingValues,
    onLanguage: (String) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
) {
    val t = LocalUiText.current
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(t.settings, style = MaterialTheme.typography.headlineSmall)
        Text(t.language, style = MaterialTheme.typography.titleSmall)
        Text(t.languageHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguages.forEach { lang ->
                val label = if (lang.tag.isEmpty()) t.languageSystem else lang.nativeName
                FilterChip(
                    selected = languageTag == lang.tag,
                    onClick = { onLanguage(lang.tag) },
                    label = { Text(label) },
                )
            }
        }

        Text(t.appearance, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = themeMode == ThemeMode.SYSTEM, onClick = { onThemeMode(ThemeMode.SYSTEM) }, label = { Text(t.appearanceSystem) })
            FilterChip(selected = themeMode == ThemeMode.LIGHT, onClick = { onThemeMode(ThemeMode.LIGHT) }, label = { Text(t.appearanceLight) })
            FilterChip(selected = themeMode == ThemeMode.DARK, onClick = { onThemeMode(ThemeMode.DARK) }, label = { Text(t.appearanceDark) })
        }

        Text(t.color, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dynamicOk) {
                AccentDot(
                    selected = accent == Accent.DYNAMIC,
                    label = t.colorDynamic,
                    dynamic = true,
                    color = accentSwatch(Accent.TEAL),
                    onClick = { onAccent(Accent.DYNAMIC) },
                )
            }
            listOf(
                Accent.TEAL to t.colorTeal,
                Accent.RASPBERRY to t.colorRaspberry,
                Accent.INDIGO to t.colorIndigo,
                Accent.AMBER to t.colorAmber,
                Accent.FOREST to t.colorForest,
            ).forEach { (a, label) ->
                AccentDot(
                    selected = accent == a,
                    label = label,
                    dynamic = false,
                    color = accentSwatch(a),
                    onClick = { onAccent(a) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(t.settingsStayOnPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "LanPulse ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccentDot(
    selected: Boolean,
    label: String,
    dynamic: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (dynamic) {
                        Modifier.background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFC51A4A),
                                    Color(0xFF3F51B5),
                                    Color(0xFF0F7A7A),
                                    Color(0xFFF5C518),
                                    Color(0xFFC51A4A),
                                ),
                            ),
                        )
                    } else {
                        Modifier.background(color)
                    },
                )
                .border(if (selected) 3.dp else 1.dp, border, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}
