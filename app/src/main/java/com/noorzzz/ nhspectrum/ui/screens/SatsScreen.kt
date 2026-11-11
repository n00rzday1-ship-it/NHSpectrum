package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class SatInfo(val prn: String, val snr: Int, val el: Int, val az: Int)

@Composable
fun SatsScreen(viewModel: ScannerViewModel) {
    // Simulated skyplot for now (real GNSS status needs GnssStatus.Callback)
    val sats = remember {
        List(12) { i ->
            SatInfo(
                prn = "G${(i + 1).toString().padStart(2, '0')}",
                snr = Random.nextInt(18, 48),
                el = Random.nextInt(5, 85),
                az = Random.nextInt(0, 360)
            )
        }.sortedByDescending { it.snr }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "GNSS SKYPLOT",
            color = SatColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(16.dp))

        // Skyplot
        Canvas(modifier = Modifier.size(240.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2 - 12f

            for (i in 1..3) {
                drawCircle(
                    color = SatColor.copy(alpha = 0.14f),
                    radius = r * i / 3,
                    style = Stroke(1f)
                )
            }
            drawLine(SatColor.copy(alpha = 0.14f), Offset(cx - r, cy), Offset(cx + r, cy), 1f)
            drawLine(SatColor.copy(alpha = 0.14f), Offset(cx, cy - r), Offset(cx, cy + r), 1f)

            sats.forEach { sat ->
                val elFrac = 1f - (sat.el / 90f)
                val dist = r * elFrac
                val rad = Math.toRadians(sat.az.toDouble())
                val x = cx + (cos(rad) * dist).toFloat()
                val y = cy + (sin(rad) * dist).toFloat()
                val color = when {
                    sat.snr > 40 -> SatColor
                    sat.snr > 30 -> Color(0xFFFACC15)
                    else -> BleColor
                }
                drawCircle(color.copy(alpha = 0.7f), 6f, Offset(x, y))
                drawCircle(color, 6f, Offset(x, y), style = Stroke(1.5f))
            }
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sats) { sat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(8.dp))
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sat.prn, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        "EL ${sat.el}°  AZ ${sat.az}°",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        "${sat.snr} dB",
                        color = SatColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
