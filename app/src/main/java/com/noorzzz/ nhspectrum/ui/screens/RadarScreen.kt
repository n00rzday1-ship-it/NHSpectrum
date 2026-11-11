package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var angle by remember { mutableFloatStateOf(0f) }

    // sweep animation
    LaunchedEffect(Unit) {
        while (true) {
            angle += 0.04f
            if (angle > 360f) angle = 0f
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("WI-FI", snapshot.wifi.size.toString(), WifiColor, Modifier.weight(1f))
            StatCard("BLE", snapshot.ble.size.toString(), BleColor, Modifier.weight(1f))
            StatCard("CELL", snapshot.cell.size.toString(), CellColor, Modifier.weight(1f))
            StatCard("DRONE", snapshot.drones.size.toString(), DroneColor, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Radar canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(280.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 - 8f

                // rings
                for (i in 1..4) {
                    drawCircle(
                        color = Accent.copy(alpha = if (i == 4) 0.35f else 0.08f),
                        radius = r * i / 4,
                        style = Stroke(width = if (i == 4) 2f else 1f)
                    )
                }

                // cross
                drawLine(Accent.copy(alpha = 0.08f), Offset(cx - r, cy), Offset(cx + r, cy), 1f)
                drawLine(Accent.copy(alpha = 0.08f), Offset(cx, cy - r), Offset(cx, cy + r), 1f)

                // sweep
                val rad = Math.toRadians(angle.toDouble())
                val sweepEnd = Offset(cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat())
                drawLine(Accent, Offset(cx, cy), sweepEnd, 2.5f)

                // signals as dots
                snapshot.wifi.forEachIndexed { i, sig ->
                    val a = (i * 37f) % 360f
                    val dist = r * (1f - (sig.rssi + 100f) / 70f).coerceIn(0.15f, 0.95f)
                    val rad2 = Math.toRadians(a.toDouble())
                    val x = cx + (cos(rad2) * dist).toFloat()
                    val y = cy + (sin(rad2) * dist).toFloat()
                    val color = if (sig.isEvilTwin) ThreatColor else WifiColor
                    drawCircle(color, 4f, Offset(x, y))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}
