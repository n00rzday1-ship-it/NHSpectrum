package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BootScreen(onFinished: () -> Unit) {
    val lines = listOf(
        "> INITIALIZING KERNEL" to "OK",
        "> MOUNTING RF FRONTEND" to "OK",
        "> WIFI / BLE CALIBRATION" to "OK",
        "> LOADING EVIL TWIN SIGNATURES" to "OK",
        "> ESP32 MARAUDER BRIDGE" to "WAIT",
        "> OPS MODULE ONLINE" to "OK",
        "> GNSS ENGINE READY" to "OK",
        "> DEVELOPER  NOORZZZ" to "",
        "> ALL SYSTEMS ARMED" to "OK"
    )

    var visibleCount by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Logo pulse
    val infinite = rememberInfiniteTransition(label = "boot")
    val glowAlpha by infinite.animateFloat(
        0.4f, 0.85f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow"
    )
    val ringAngle by infinite.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "ring"
    )

    LaunchedEffect(Unit) {
        delay(600)
        lines.forEachIndexed { index, _ ->
            visibleCount = index + 1
            progress = (index + 1).toFloat() / lines.size
            delay(if (index == 4) 280L else 160L)
        }
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030405)),
        contentAlignment = Alignment.Center
    ) {
        // soft red glow behind logo
        Canvas(modifier = Modifier.size(280.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = glowAlpha * 0.35f), Color.Transparent)
                ),
                radius = size.minDimension / 1.6f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            // Logo
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.5.dp.toPx()
                    val radius = size.minDimension / 2.2f

                    // rotating dashed ring
                    drawArc(
                        color = Accent.copy(alpha = 0.9f),
                        startAngle = ringAngle,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // thin inner ring
                    drawCircle(
                        color = Accent.copy(alpha = 0.2f),
                        radius = radius * 0.82f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    // center dot
                    drawCircle(color = Accent.copy(alpha = 0.7f), radius = 4.dp.toPx())
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NH",
                        color = Color(0xFFE0E0E0),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "SPECTRUM",
                        color = Color(0xFFC8C8C8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 6.sp
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Terminal lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.Top
            ) {
                lines.take(visibleCount).forEach { (text, status) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = text,
                            color = TextDim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        if (status.isNotEmpty()) {
                            Text(
                                text = status,
                                color = when (status) {
                                    "OK" -> SuccessColor
                                    "WAIT" -> WarningColor
                                    else -> TextPrimary
                                },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(2.dp)
                    .background(Accent.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(Accent, Color(0xFFFF6B6B)))
                        )
                )
            }
        }
    }
}
