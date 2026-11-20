package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float?,
    val basebandCn0: Float?
)

@Composable
fun SatsScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    var satellites by remember { mutableStateOf<List<LiveSatellite>>(emptyList()) }
    var status by remember { mutableStateOf("Waiting for GNSS status") }
    var firstFixMs by remember { mutableStateOf<Int?>(null) }
    var receiverLocation by remember { mutableStateOf<String?>(null) }
    var receiverAccuracy by remember { mutableStateOf<Float?>(null) }
    var selectedSatellite by remember { mutableStateOf<LiveSatellite?>(null) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        var callback: GnssStatus.Callback? = null
        val locationListener = LocationListener { location ->
            if (!location.latitude.isFinite() || !location.longitude.isFinite()) return@LocationListener
            receiverLocation = String.format(java.util.Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
            receiverAccuracy = location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() && it > 0f }
        }

        if (!fineGranted) {
            status = "Precise location permission required"
        } else {
            val gpsEnabled = try { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
            if (!gpsEnabled) {
                status = "Phone location is off or GPS provider is disabled"
            } else {
                var lastUiSnapshotAt = 0L
                val gnssCallback = object : GnssStatus.Callback() {
                    override fun onStarted() { status = "GNSS started · acquiring satellite measurements" }
                    override fun onStopped() { status = "GNSS stopped · check Location and battery restrictions" }
                    override fun onFirstFix(ttffMillis: Int) {
                        firstFixMs = ttffMillis
                        status = "GNSS first fix acquired"
                    }
                    override fun onSatelliteStatusChanged(gnssStatus: GnssStatus) {
                        // Vendor receivers can emit frequent status callbacks; throttle UI work
                        // and discard invalid measurement values before drawing the sky plot.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUiSnapshotAt < 1000L) return
                        lastUiSnapshotAt = now
                        try {
                            val rows = (0 until gnssStatus.satelliteCount).mapNotNull { i ->
                                val cn0 = gnssStatus.getCn0DbHz(i)
                                val elevation = gnssStatus.getElevationDegrees(i)
                                val azimuth = gnssStatus.getAzimuthDegrees(i)
                                // Exclude below-horizon satellites and zero/invalid signal readings.
                                if (!cn0.isFinite() || cn0 <= 0f ||
                                    !elevation.isFinite() || elevation <= 0f ||
                                    !azimuth.isFinite()
                                ) return@mapNotNull null
                                LiveSatellite(
                                    id = gnssStatus.getSvid(i).toString(),
                                    constellation = constellationName(gnssStatus.getConstellationType(i)),
                                    cn0 = cn0.coerceAtLeast(0f),
                                    elevation = elevation.coerceIn(0f, 90f),
                                    azimuth = ((azimuth % 360f) + 360f) % 360f,
                                    usedInFix = gnssStatus.usedInFix(i),
                                    carrierFrequencyHz = if (gnssStatus.hasCarrierFrequencyHz(i)) gnssStatus.getCarrierFrequencyHz(i) else null,
                                    basebandCn0 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && gnssStatus.hasBasebandCn0DbHz(i))
                                        runCatching { gnssStatus.getBasebandCn0DbHz(i) }.getOrNull() else null
                                )
                            }
                                // Some vendor GNSS stacks report one space vehicle more than once
                                // (for example, separate signal-frequency entries). Collapse duplicates
                                // before LazyColumn: duplicate stable keys can crash Compose.
                                .groupBy { "${it.constellation}-${it.id}" }
                                .mapNotNull { (_, sameSatellite) -> sameSatellite.maxByOrNull { it.cn0 } }
                                .sortedWith(compareByDescending<LiveSatellite> { it.usedInFix }.thenByDescending { it.cn0 })
                            if (rows != satellites) satellites = rows
                            val nextStatus = if (rows.isEmpty()) "GPS active · waiting for satellite measurements" else "Live GNSS · ${rows.size} satellites in view"
                            if (status != nextStatus) status = nextStatus
                        } catch (e: Exception) {
                            // Vendor GNSS implementations can expose incomplete optional measurements.
                            // Keep the screen alive and preserve the last valid satellite snapshot.
                            status = "GNSS measurements temporarily unavailable"
                            Log.w("NHSpectrum", "Could not read GNSS satellite status", e)
                        }
                    }
                }
                callback = gnssCallback
                try {
                    val registered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        manager.registerGnssStatusCallback(context.mainExecutor, gnssCallback)
                    } else {
                        @Suppress("DEPRECATION")
                        manager.registerGnssStatusCallback(gnssCallback)
                    }
                    if (registered) {
                        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, context.mainLooper)
                        status = "GPS receiver active · searching for satellites"
                    } else {
                        status = "This device could not register a GNSS status callback"
                    }
                } catch (_: SecurityException) {
                    status = "Location permission denied by Android"
                } catch (e: Exception) {
                    status = "Could not start GPS receiver: ${e.message ?: "provider unavailable"}"
                }
            }
        }
        onDispose {
            try { manager.removeUpdates(locationListener) } catch (_: Exception) {}
            callback?.let { try { manager.unregisterGnssStatusCallback(it) } catch (_: Exception) {} }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "gnss_header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIVE GNSS SKY VIEW", color = SatColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(6.dp))
                Text(status, color = TextMuted, fontSize = 10.sp)
                firstFixMs?.let { Text("GNSS time to first fix: ${it} ms", color = TextDim, fontSize = 10.sp) }
                receiverLocation?.let { Text("Phone GPS fix · " + it, color = Accent, fontSize = 10.sp) }
                receiverAccuracy?.let { Text("Position accuracy · ±${it.toInt()} m", color = TextDim, fontSize = 10.sp) }
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
                Text("Tap a satellite row for its GNSS system and receiver measurements.", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        if (satellites.isEmpty()) {
            item(key = "gnss_empty") {
                Text("Satellite values appear here when the phone GNSS receiver reports them. Outdoor sky view improves reception.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            }
        } else {
            items(satellites, key = { "${it.constellation}-${it.id}" }, contentType = { "satellite" }) { sat ->
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp))
                        .border(1.dp, Border, RoundedCornerShape(8.dp)).clickable { selectedSatellite = sat }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${sat.constellation} · SVID ${sat.id}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("EL ${sat.elevation.toInt()}° · AZ ${sat.azimuth.toInt()}°", color = TextMuted, fontSize = 10.sp)
                    }
                    Text(String.format(java.util.Locale.US, "%.1f dB-Hz", sat.cn0), color = SatColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sat.usedInFix) "FIX" else "VIEW", color = if (sat.usedInFix) SuccessColor else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    selectedSatellite?.let { sat ->
        AlertDialog(
            onDismissRequest = { selectedSatellite = null },
            containerColor = Panel,
            title = { Text("${sat.constellation} · SVID ${sat.id}", color = Accent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SatelliteDetail("GNSS system / region", constellationRegion(sat.constellation))
                    SatelliteDetail("Signal C/N₀", String.format("%.1f dB-Hz", sat.cn0))
                    SatelliteDetail("Elevation", String.format("%.1f°", sat.elevation))
                    SatelliteDetail("Azimuth", String.format("%.1f°", sat.azimuth))
                    SatelliteDetail("Used in position fix", if (sat.usedInFix) "Yes" else "No")
                    SatelliteDetail("Carrier frequency", sat.carrierFrequencyHz?.let { String.format("%.3f MHz", it / 1_000_000f) } ?: "Not exposed by receiver")
                    SatelliteDetail("Baseband C/N₀", sat.basebandCn0?.let { String.format("%.1f dB-Hz", it) } ?: "Not exposed by receiver")
                }
            },
            confirmButton = { TextButton(onClick = { selectedSatellite = null }) { Text("CLOSE", color = Accent) } }
        )
    }
}

@Composable
private fun SatelliteDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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

private fun constellationRegion(name: String): String = when (name) {
    "GPS" -> "Global Navigation Satellite System · United States"
    "GLONASS" -> "Global Navigation Satellite System · Russian Federation"
    "GALILEO" -> "Global Navigation Satellite System · European Union"
    "BEIDOU" -> "Global Navigation Satellite System · China"
    "QZSS" -> "Regional navigation system · Japan"
    "NAVIC" -> "Regional navigation system · India"
    "SBAS" -> "Satellite-based augmentation system · regional"
    else -> "GNSS constellation not identified"
}
