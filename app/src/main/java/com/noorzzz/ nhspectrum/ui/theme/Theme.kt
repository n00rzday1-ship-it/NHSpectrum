package com.noorzzz.nhspectrum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// NHSpectrum dark field-console palette. Accent is runtime-selectable in Settings.
var Accent by mutableStateOf(Color(0xFF20D6B2))
    private set

fun setAccentColor(color: Color) {
    Accent = color
}

val AccentGlow = Color(0x5520D6B2)
val Bg = Color(0xFF070D10)
val Panel = Color(0xFF0D171B)
val Panel2 = Color(0xFF122127)
val Border = Color(0xFF20363A)
val TextPrimary = Color(0xFFE7F3F1)
val TextDim = Color(0xFF9AB0AD)
val TextMuted = Color(0xFF637B78)

val WifiColor = Color(0xFF36C9E8)
val BleColor = Color(0xFFFFB86B)
val CellColor = Color(0xFFB69CFF)
val DroneColor = Color(0xFFE7D36C)
val SatColor = Color(0xFF75D99B)
val ThreatColor = Color(0xFFFF667A)
val SuccessColor = Color(0xFF55D69E)
val WarningColor = Color(0xFFFFC46B)

@Composable
fun NHSpectrumTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFF00231D),
        secondary = WifiColor,
        onSecondary = Color(0xFF00212A),
        background = Bg,
        onBackground = TextPrimary,
        surface = Panel,
        onSurface = TextPrimary,
        surfaceVariant = Panel2,
        onSurfaceVariant = TextDim,
        outline = Border,
        error = ThreatColor
    )
    MaterialTheme(colorScheme = colors, content = content)
}
