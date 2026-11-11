package com.noorzzz.nhspectrum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Exact colors from the HTML demo
val Accent = Color(0xFFFF2D2D)
val AccentGlow = Color(0x59FF2D2D)
val Bg = Color(0xFF07080A)
val Panel = Color(0xFF0E1014)
val Panel2 = Color(0xFF14171D)
val Border = Color(0xFF1E232C)
val TextPrimary = Color(0xFFE8EAED)
val TextDim = Color(0xFF8B939E)
val TextMuted = Color(0xFF5C6570)

val WifiColor = Color(0xFF22D3EE)
val BleColor = Color(0xFFFB923C)
val CellColor = Color(0xFFE879F9)
val DroneColor = Color(0xFFFACC15)
val SatColor = Color(0xFF4ADE80)
val ThreatColor = Color(0xFFEF4444)
val SuccessColor = Color(0xFF22C55E)
val WarningColor = Color(0xFFF59E0B)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextDim,
    outline = Border,
    error = ThreatColor
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFDC2626),
    onPrimary = Color.White,
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF111318),
    surface = Color.White,
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF8F9FB),
    onSurfaceVariant = Color(0xFF5A6270),
    outline = Color(0xFFE2E5EB),
    error = Color(0xFFDC2626)
)

@Composable
fun NHSpectrumTheme(
    darkTheme: Boolean = true, // force dark by default like the demo
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
