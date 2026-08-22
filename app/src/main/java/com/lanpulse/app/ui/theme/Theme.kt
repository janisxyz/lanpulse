package com.lanpulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val Teal = Color(0xFF5EEAD4)
private val OnTeal = Color(0xFF042F2E)
private val Bg = Color(0xFF0B1211)
private val Surface = Color(0xFF121A19)
private val SurfaceHi = Color(0xFF182221)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = OnTeal,
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Teal,
    secondary = Color(0xFF99F6E4),
    onSecondary = OnTeal,
    background = Bg,
    onBackground = Color(0xFFE7F3EF),
    surface = Surface,
    onSurface = Color(0xFFE7F3EF),
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = Color(0xFF8AA39C),
    outline = Color(0xFF3F4F4B),
    error = Color(0xFFF07178),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF10201C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10201C),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun LanPulseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context).copy(background = Bg, surface = Surface)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
