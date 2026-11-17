package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlin.math.cos
import kotlin.math.sin

private data class LiveSatellite(
    val id: String,
    val constellation: String,
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean
)

@Composable
fun SatsScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    var satellites by remember { mutableStateOf<List<LiveSatellite>>(emptyList()) }
    var status by remember { mutableStateOf("Waiting for GNSS status") }
    var firstFixMs by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            status = "Precise location permission required"
        } else {
            val callback = object : GnssStatus.Callback() {
                override fun onStarted() { status = "GNSS started · acquiring satellites" }
                override fun onStopped() { status = "GNSS stopped" }
                override fun onFirstFix(ttffMillis: Int) {
                    firstFixMs = ttffMillis
                    status = "GNSS first fix acquired"
                }
                override fun onSatelliteStatusChanged(gnssStatus: GnssStatus) {
                    val rows = (0 until gnssStatus.satelliteCount).map { i ->
                        LiveSatellite(
                            id = gnssStatus.getSvid(i).toString(),
                            constellation = constellationName(gnssStatus.getConstellationType(i)),
                            cn0 = gnssStatus.getCn0DbHz(i),
                            elevation = gnssStatus.getElevationDegrees(i),
                            azimuth = gnssStatus.getAzimuthDegrees(i),
                            usedInFix = gnssStatus.usedInFix(i)
                        )
                    }.sortedWith(compareByDescending<LiveSatellite> { it.usedInFix }.thenByDescending { it.cn0 })
                    satellites = rows
                    status = if (rows.isEmpty()) "No satellites reported by device" else "Live GNSS · ${rows.size} satellites in view"
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manager.registerGnssStatusCallback(context.mainExecutor, callback)
                else @Suppress("DEPRECATION") manager.registerGnssStatusCallback(callback)
            } catch (_: SecurityException) {
                status = "GNSS permission unavailable"
            }
            onDispose { try { manager.unregisterGnssStatusCallback(callback) } catch (_: Exception) {} }
        }
        onDispose { }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LIVE GNSS SKY VIEW", color = SatColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        Text(status, color = TextMuted, fontSize = 10.sp)
        firstFixMs?.let { Text("Time to first fix: ${it} ms", color = TextDim, fontSize = 10.sp) }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.size(240.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f - 12f
            for (i in 1..3) drawCircle(SatColor.copy(alpha = 0.18f), r * i / 3f, style = Stroke(1f))
            drawLine(SatColor.copy(alpha = 0.18f), Offset(cx-r,cy), Offset(cx+r,cy), 1f)
            drawLine(SatColor.copy(alpha = 0.18f), Offset(cx,cy-r), Offset(cx,cy+r), 1f)
            satellites.forEach { sat ->
                val distance = r * (1f - sat.elevation.coerceIn(0f,90f) / 90f)
                val angle = Math.toRadians((sat.azimuth - 90f).toDouble())
                val point = Offset(cx + cos(angle).toFloat() * distance, cy + sin(angle).toFloat() * distance)
                val color = if (sat.usedInFix) SuccessColor else if (sat.cn0 >= 25f) SatColor else WarningColor
                drawCircle(color, if (sat.usedInFix) 6f else 4.5f, point)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("IN VIEW: ${satellites.size}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("USED IN FIX: ${satellites.count { it.usedInFix }}", color = SuccessColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(satellites, key = { "${it.constellation}-${it.id}" }) { sat ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).border(1.dp, Border, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sat.constellation} · SVID ${sat.id}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("EL ${sat.elevation.toInt()}° · AZ ${sat.azimuth.toInt()}°", color = TextMuted, fontSize = 10.sp)
                    }
                    Text(String.format("%.1f dB-Hz", sat.cn0), color = SatColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sat.usedInFix) "FIX" else "VIEW", color = if (sat.usedInFix) SuccessColor else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (satellites.isEmpty()) item { Text("Satellite values appear here when the phone GNSS receiver reports them. Indoor reception and disabled location may produce no data.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
        }
    }
}

private fun constellationName(type: Int): String = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
    GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_IRNSS -> "NAVIC"
    else -> "OTHER"
}
