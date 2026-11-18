package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.data.models.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class RadarKind { WIFI, BLE, CELL, DRONE }
private data class RadarTarget(
    val kind: RadarKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val signal: Int,
    val color: Color,
    val details: List<Pair<String, String>>
)

@Composable
fun RadarScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val context = LocalContext.current
    var angle by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<RadarTarget?>(null) }
    var locatingId by remember { mutableStateOf<String?>(null) }
    val locatorTone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    DisposableEffect(locatorTone) {
        onDispose { locatorTone.release() }
    }

    val targets = remember(snapshot) {
        buildList {
            snapshot.wifi.forEach { s -> add(RadarTarget(RadarKind.WIFI, "wifi:" + s.id, s.ssid.ifBlank { "Hidden network" },
                "Wi-Fi · ${s.rssi} dBm", s.rssi, WifiColor, listOf(
                    "SSID" to (s.ssid.ifBlank { "Hidden" }), "BSSID" to s.bssid, "Vendor" to s.vendor,
                    "Frequency" to "${s.frequency} MHz", "Channel" to s.channel.toString(),
                    "Band" to s.band, "Security" to s.security, "RSSI" to "${s.rssi} dBm"
                ))) }
            snapshot.ble.forEach { s -> add(RadarTarget(RadarKind.BLE, "ble:" + s.id, s.name.ifBlank { "Unnamed BLE device" },
                "BLE · ${s.rssi} dBm", s.rssi, BleColor, listOf(
                    "Name" to s.name.ifBlank { "Not advertised" }, "Address" to s.address,
                    "RSSI" to "${s.rssi} dBm", "TX power" to (s.txPower?.let { "$it dBm" } ?: "Not advertised"),
                    "Last seen" to s.lastSeen.toString()
                ))) }
            snapshot.cell.forEach { s -> add(RadarTarget(RadarKind.CELL, "cell:" + s.id, "${s.type} cell · ${s.operator}",
                "Cellular · ${if (s.rsrp == Int.MIN_VALUE) "signal unavailable" else "${s.rsrp} dBm"}", s.rsrp, CellColor, listOf(
                    "Technology" to s.type, "Operator" to s.operator.ifBlank { "Unavailable" }, "Registered / serving" to if (s.registered) "Yes" else "No", "Cell ID" to detailCell(s.cid),
                    "TAC" to detailCell(s.tac.toLong()), "PCI" to detailCell(s.pci.toLong()),
                    "EARFCN / NR-ARFCN" to detailCell(s.channelNumber.toLong()),
                    "Band" to s.band.ifBlank { "Not exposed by Android" },
                    "RSRP" to detailSignal(s.rsrp).let { if (it == "Unavailable") it else "$it dBm" }, "RSRQ" to detailSignal(s.rsrq).let { if (it == "Unavailable") it else "$it dB" }
                ))) }
            snapshot.drones.forEach { s -> add(RadarTarget(RadarKind.DRONE, "drone:" + s.id, s.model.ifBlank { "Unidentified Remote ID" },
                "Drone · ${s.rssi} dBm", s.rssi, DroneColor, listOf(
                    "Model" to s.model.ifBlank { "Not identified" }, "Serial / ID" to s.serial.ifBlank { "Not broadcast" },
                    "RSSI" to "${s.rssi} dBm", "Altitude" to (s.altitude?.let { "$it m" } ?: "Not broadcast"), "Aircraft latitude" to (s.latitude?.toString() ?: "Not broadcast"), "Aircraft longitude" to (s.longitude?.toString() ?: "Not broadcast"), "Operator latitude" to (s.operatorLatitude?.toString() ?: "Not broadcast"), "Operator longitude" to (s.operatorLongitude?.toString() ?: "Not broadcast")
                ))) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            angle = (angle + 1.8f) % 360f
            delay(32)
        }
    }

    LaunchedEffect(locatingId, targets) {
        val activeId = locatingId ?: return@LaunchedEffect
        while (true) {
            val target = targets.firstOrNull { it.id == activeId } ?: break
            val soundEnabled = context.getSharedPreferences("nhspectrum_prefs", 0)
                .getBoolean("locator_sound", true)
            if (soundEnabled) locatorTone.startTone(ToneGenerator.TONE_PROP_BEEP, 260)
            val interval = when {
                target.signal >= -55 -> 350L
                target.signal >= -68 -> 600L
                target.signal >= -80 -> 950L
                else -> 1400L
            }
            delay(interval)
        }
        locatingId = null
    }


    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("WI-FI", snapshot.wifi.size.toString(), WifiColor, Modifier.weight(1f))
            StatCard("BLE", snapshot.ble.size.toString(), BleColor, Modifier.weight(1f))
            StatCard("CELL", snapshot.cell.size.toString(), CellColor, Modifier.weight(1f))
            StatCard("DRONE", snapshot.drones.size.toString(), DroneColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LegendItem("Wi-Fi", WifiColor)
            LegendItem("BLE", BleColor)
            LegendItem("Cell", CellColor)
            LegendItem("Drone", DroneColor)
        }
        Spacer(Modifier.height(6.dp))
        Text("LIVE OBSERVATIONS · TAP A TARGET FOR DETAILS", color = TextMuted, fontSize = 9.sp, letterSpacing = 0.7.sp)
        locatingId?.let { id ->
            targets.firstOrNull { it.id == id }?.let { target ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp)
                        .background(Panel, RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SIGNAL SEEKER · ${target.title}", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${target.signal} dBm · ${proximityLabel(target.signal)} · relative signal only",
                            color = TextMuted, fontSize = 9.sp)
                    }
                    Text("STOP", color = ThreatColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { locatingId = null }.padding(6.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(targets) {
                detectTapGestures { tap ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = minOf(size.width, size.height) * 0.44f
                    val hit = targets.mapIndexedNotNull { index, target ->
                        val point = radarPoint(index, targets.size, target.signal, cx, cy, r)
                        val d = sqrt((point.x - tap.x) * (point.x - tap.x) + (point.y - tap.y) * (point.y - tap.y))
                        if (d < 30f) d to target else null
                    }.minByOrNull { it.first }
                    selected = hit?.second
                }
            }) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.44f
                for (i in 1..4) drawCircle(
                    color = Accent.copy(alpha = if (i == 4) 0.32f else 0.12f),
                    radius = r * i / 4f, center = Offset(cx, cy),
                    style = Stroke(width = if (i == 4) 1.6f else 1f)
                )
                drawLine(Border, Offset(cx-r,cy), Offset(cx+r,cy), 1f)
                drawLine(Border, Offset(cx,cy-r), Offset(cx,cy+r), 1f)
                val rad = Math.toRadians(angle.toDouble())
                drawLine(Accent.copy(alpha = .85f), Offset(cx,cy),
                    Offset(cx + cos(rad).toFloat()*r, cy + sin(rad).toFloat()*r), 2f)
                targets.forEachIndexed { index, target ->
                    val p = radarPoint(index, targets.size, target.signal, cx, cy, r)
                    drawCircle(target.color.copy(alpha = .18f), 13f, p)
                    when (target.kind) {
                        RadarKind.WIFI -> {
                            drawCircle(target.color, 2.2f, p)
                            for (arc in 1..3) {
                                val radius = 4f + arc * 3.2f
                                drawArc(target.color, 220f, 100f, false,
                                    Offset(p.x-radius, p.y-radius),
                                    androidx.compose.ui.geometry.Size(radius*2, radius*2),
                                    style = Stroke(1.8f))
                            }
                        }
                        RadarKind.BLE -> {
                            val pathR = 6f
                            drawLine(target.color, Offset(p.x, p.y-pathR), Offset(p.x, p.y+pathR), 2f)
                            drawLine(target.color, Offset(p.x, p.y-pathR), Offset(p.x+5f, p.y-2f), 2f)
                            drawLine(target.color, Offset(p.x+5f, p.y-2f), Offset(p.x, p.y+2f), 2f)
                            drawLine(target.color, Offset(p.x, p.y+2f), Offset(p.x+5f, p.y+pathR), 2f)
                        }
                        RadarKind.CELL -> {
                            drawLine(target.color, Offset(p.x, p.y-7f), Offset(p.x, p.y+7f), 2f)
                            drawLine(target.color, Offset(p.x-5f, p.y+7f), Offset(p.x+5f, p.y+7f), 2f)
                            drawArc(target.color, 210f, 120f, false, Offset(p.x-10f, p.y-10f), androidx.compose.ui.geometry.Size(20f,20f), style = Stroke(1.6f))
                            drawArc(target.color, 210f, 120f, false, Offset(p.x-15f, p.y-15f), androidx.compose.ui.geometry.Size(30f,30f), style = Stroke(1.6f))
                            drawCircle(target.color, 2f, Offset(p.x, p.y-6f))
                        }
                        RadarKind.DRONE -> {
                            val a = Offset(p.x, p.y-6f); val b = Offset(p.x-6f,p.y+5f); val c = Offset(p.x+6f,p.y+5f)
                            drawLine(target.color,a,b,2f); drawLine(target.color,b,c,2f); drawLine(target.color,c,a,2f)
                        }
                    }
                }
            }
        }
        Text("${targets.size} observations · Select a target for available metadata",
            color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp))
    }

    selected?.let { target ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Panel,
            title = { Text(target.title, color = target.color, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(target.subtitle, color = TextMuted, fontSize = 12.sp)
                    target.details.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { locatingId = target.id; selected = null }) { Text("LOCATE", color = Accent) }
                    TextButton(onClick = { selected = null }) { Text("CLOSE", color = TextMuted) }
                }
            }
        )
    }
}

private fun detailCell(value: Long): String = if (value < 0 || value == Int.MAX_VALUE.toLong() || value == Long.MAX_VALUE) "Unavailable" else value.toString()
private fun detailSignal(value: Int): String = if (value == Int.MAX_VALUE || value == Int.MIN_VALUE) "Unavailable" else value.toString()

private fun proximityLabel(rssi: Int): String = when {
    rssi >= -55 -> "Very strong"
    rssi >= -68 -> "Strong"
    rssi >= -80 -> "Moderate"
    else -> "Weak"
}

private fun radarPoint(index: Int, count: Int, rssi: Int, cx: Float, cy: Float, radius: Float): Offset {
    val angle = Math.toRadians(((index * 137.508f) % 360f).toDouble())
    val normalized = ((rssi + 110f) / 90f).coerceIn(.12f, .92f)
    val distance = radius * (1f - normalized * .72f)
    return Offset(cx + cos(angle).toFloat() * distance, cy + sin(angle).toFloat() * distance)
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(label, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Panel, RoundedCornerShape(10.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}
