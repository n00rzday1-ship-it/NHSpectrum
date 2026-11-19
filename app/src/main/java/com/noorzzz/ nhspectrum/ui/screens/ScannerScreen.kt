package com.noorzzz.nhspectrum.ui.screens

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.noorzzz.nhspectrum.data.models.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlin.math.max

private data class Detail(val title: String, val category: String, val fields: List<Pair<String, String>>)

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val bleStatus by viewModel.bleStatus.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var wifiView by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Detail?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp)) {
            listOf("WI-FI", "BLE", "CELL", "DRONE", "SPECTRUM").forEachIndexed { i, title ->
                Text(title, color = if (tab == i) Accent else TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { tab = i }.padding(horizontal = 12.dp, vertical = 14.dp))
            }
        }
        if (tab == 0) {
            Row(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CHANNELS", "SPECTRUM", "SECURITY").forEachIndexed { i, label ->
                    Text(label, color = if (wifiView == i) Accent else TextMuted, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(if (wifiView == i) Panel2 else Bg, RoundedCornerShape(6.dp))
                            .clickable { wifiView = i }.padding(horizontal = 10.dp, vertical = 9.dp))
                }
            }
            when (wifiView) {
                1 -> WifiSpectrum(snapshot.wifi)
                2 -> WifiSecuritySummary(snapshot.wifi)
                else -> WifiChannelsSummary(snapshot.wifi) { s ->
                    selected = Detail(s.ssid.ifBlank { "Hidden Wi-Fi network" }, "WI-FI ACCESS POINT", listOf(
                        "SSID" to s.ssid.ifBlank { "Hidden" }, "BSSID" to s.bssid, "Vendor" to s.vendor,
                        "Frequency" to "${s.frequency} MHz", "Channel" to s.channel.toString(),
                        "Band" to s.band, "Channel width" to (s.channelWidthMhz?.let { "${it} MHz" } ?: "Not reported"),
                        "Security" to s.security, "RSSI" to "${s.rssi} dBm", "Last seen" to formatTimestamp(s.lastSeen)
                    ))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    1 -> {
                        item { Text(bleStatus, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) }
                        items(snapshot.ble, key = { it.id }) { s ->
                        SignalCard(s.name.ifBlank { "Unnamed BLE device" }, s.address, "${s.rssi} dBm", BleColor) {
                            selected = Detail(s.name.ifBlank { "Unnamed BLE device" }, "BLUETOOTH LE", listOf(
                                "Name" to s.name.ifBlank { "Not advertised" }, "Address" to s.address,
                                "RSSI" to "${s.rssi} dBm", "TX power" to (s.txPower?.let { "$it dBm" } ?: "Not advertised"),
                                "Advertised services" to s.serviceUuids.joinToString(", ").ifBlank { "None in advertisement" },
                                "Manufacturer data" to s.manufacturerData.entries.joinToString(" · ") { "ID ${it.key}: ${it.value}" }.ifBlank { "None in advertisement" },
                                "Connectable" to (s.connectable?.toString() ?: "Not exposed"),
                                "Last seen" to formatTimestamp(s.lastSeen)
                            ))
                        }
                    }
                    }
                    2 -> items(snapshot.cell.sortedWith(compareByDescending<CellSignal> { it.registered }.thenByDescending { if (it.rsrp == Int.MIN_VALUE) -200 else it.rsrp })) { s ->
                        SignalCard("${if (s.registered) "SERVING · " else ""}${s.type} · ${s.operator.ifBlank { "Operator unavailable" }}", "CID ${displayCell(s.cid)} · TAC ${displayCell(s.tac.toLong())} · PCI ${displayCell(s.pci.toLong())}", if (s.rsrp == Int.MIN_VALUE) "N/A" else "${s.rsrp} dBm", CellColor) {
                            selected = Detail("${s.type} cell", "CELLULAR", listOf(
                                "Technology" to s.type, "Operator" to s.operator.ifBlank { "Unavailable" },
                                "Cell ID" to displayCell(s.cid), "TAC" to displayCell(s.tac.toLong()),
                                "PCI" to displayCell(s.pci.toLong()), "Registered / serving" to if (s.registered) "Yes" else "No",
                                "Band" to s.band.ifBlank { "Not exposed by Android" },
                                "EARFCN / NR-ARFCN" to displayCell(s.channelNumber.toLong()),
                                "RSRP" to displaySignal(s.rsrp).let { if (it == "Unavailable") it else "${it} dBm" },
                                "RSRQ" to displaySignal(s.rsrq).let { if (it == "Unavailable") it else "${it} dB" },
                                "Last seen" to formatTimestamp(s.lastSeen)
                            ))
                        }
                    }
                    3 -> items(snapshot.drones, key = { it.id }) { s ->
                        SignalCard(s.model.ifBlank { "Unidentified broadcast" }, "ID ${s.serial}", "${s.rssi} dBm", DroneColor) {
                            selected = Detail(s.model.ifBlank { "Unidentified broadcast" }, "DRONE / REMOTE ID", listOf(
                                "Model" to s.model.ifBlank { "Not identified" }, "Serial / ID" to s.serial.ifBlank { "Not broadcast" },
                                "RSSI" to "${s.rssi} dBm", "Altitude" to (s.altitude?.let { "$it m" } ?: "Not broadcast"),
                                "Aircraft latitude" to (s.latitude?.toString() ?: "Not broadcast"),
                                "Aircraft longitude" to (s.longitude?.toString() ?: "Not broadcast"),
                                "Operator latitude" to (s.operatorLatitude?.toString() ?: "Not broadcast"),
                                "Operator longitude" to (s.operatorLongitude?.toString() ?: "Not broadcast"),
                                "Last seen" to formatTimestamp(s.lastSeen)
                            ))
                        }
                    }
                    4 -> item { UnifiedSpectrum(snapshot) }
                }
                val empty = when (tab) { 1 -> snapshot.ble.isEmpty(); 2 -> snapshot.cell.isEmpty(); 3 -> snapshot.drones.isEmpty(); else -> false }
                if (empty) item { Text("No observations yet. Check scan status and Android permissions.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp)) }
            }
        }
    }

    selected?.let { detail ->
        AlertDialog(onDismissRequest = { selected = null }, containerColor = Panel,
            title = { Column {
                Text(detail.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(detail.category, color = Accent, fontSize = 10.sp)
            } },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(detail.fields) { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = TextMuted, fontSize = 11.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("CLOSE", color = Accent) } })
    }
}


@Composable
private fun UnifiedSpectrum(snapshot: ScanSnapshot) {
    val wifi24 = snapshot.wifi.filter { it.frequency in 2400..2500 }
    val wifi5 = snapshot.wifi.filter { it.frequency in 4900..5900 }
    val wifi6 = snapshot.wifi.filter { it.frequency >= 5925 }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ALL-BAND RF OVERVIEW", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Wi-Fi: ${snapshot.wifi.size}", color = WifiColor, fontSize = 10.sp)
            Text("BLE: ${snapshot.ble.size}", color = BleColor, fontSize = 10.sp)
            Text("Cell: ${snapshot.cell.size}", color = CellColor, fontSize = 10.sp)
            Text("Drone: ${snapshot.drones.size}", color = DroneColor, fontSize = 10.sp)
        }
        Text("WI-FI CHANNEL DISTRIBUTION", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (snapshot.wifi.isEmpty()) Text("No Wi-Fi access points observed yet.", color = TextMuted, fontSize = 10.sp)
        else {
            if (wifi24.isNotEmpty()) SpectrumBand("2.4 GHz", wifi24, 1, 14, WifiColor)
            if (wifi5.isNotEmpty()) SpectrumBand("5 GHz", wifi5, 32, 177, BleColor)
            if (wifi6.isNotEmpty()) SpectrumBand("6 GHz", wifi6, 1, 233, CellColor)
        }
        Text("BLE ADVERTISEMENTS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (snapshot.ble.isEmpty()) Text("No BLE advertisements currently received. Nearby phones may not advertise while idle.", color = TextMuted, fontSize = 10.sp)
        snapshot.ble.take(12).forEach { s ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.name.ifBlank { s.address }, color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("${s.rssi} dBm", color = BleColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("CELLULAR OBSERVATIONS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        snapshot.cell.take(8).forEach { s ->
            Text("${if (s.registered) "Serving" else "Neighbour"} · ${s.type} · PCI ${displayCell(s.pci.toLong())} · ${displaySignal(s.rsrp)} dBm",
                color = TextDim, fontSize = 10.sp)
        }
        if (snapshot.cell.isEmpty()) Text("No cellular observations currently available.", color = TextMuted, fontSize = 10.sp)
    }
}

private fun formatTimestamp(value: Long): String = try {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
} catch (_: Exception) { value.toString() }

@Composable
private fun WifiChannelsSummary(networks: List<WifiSignal>, onSelect: (WifiSignal) -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("${networks.size} access points detected", color = TextMuted, fontSize = 11.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(networks, key = { it.id }) { s ->
                WifiCard(s) { onSelect(s) }
            }
        }
    }
}

@Composable
private fun WifiSpectrum(networks: List<WifiSignal>) {
    val band24 = networks.filter { it.frequency in 2400..2500 }
    val band5 = networks.filter { it.frequency in 4900..5900 }
    val band6 = networks.filter { it.frequency >= 5925 }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SPECTRUM", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${networks.size} APs", color = Accent, fontSize = 11.sp)
        }
        Text("Each arch = one AP · width = reported channel bandwidth · height = RSSI", color = TextMuted, fontSize = 10.sp)
        if (networks.isEmpty()) {
            Text("No Wi-Fi observations yet.", color = TextMuted, fontSize = 12.sp)
        } else {
            SpectrumBand("2.4 GHz", band24, 1, 14, WifiColor)
            SpectrumBand("5 GHz", band5, 32, 177, BleColor)
            if (band6.isNotEmpty()) SpectrumBand("6 GHz", band6, 1, 233, CellColor)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(networks, key = { it.id }) { s ->
                Text("${s.ssid} · CH ${s.channel} · ${s.channelWidthMhz?.let { "$it MHz" } ?: "width n/a"} · ${s.rssi} dBm",
                    color = TextDim, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SpectrumBand(title: String, networks: List<WifiSignal>, channelMin: Int, channelMax: Int, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text("$title · ${networks.size} APs", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(390.dp)) {
            val left = 18f
            val right = size.width - 12f
            val baseY = size.height - 18f
            drawLine(Border, Offset(left, baseY), Offset(right, baseY), 1f)
            for (i in 0..4) {
                val x = left + (right-left)*i/4f
                drawLine(Border.copy(alpha = .45f), Offset(x, 8f), Offset(x, baseY), 1f)
            }
            val labelBounds = mutableListOf<android.graphics.RectF>()
            networks.sortedWith(compareBy<WifiSignal> { it.channel }.thenByDescending { it.rssi }).forEach { ap ->
                val channel = ap.channel.coerceIn(channelMin, channelMax)
                val x = left + ((channel-channelMin).toFloat() / max(1, channelMax-channelMin)) * (right-left)
                val strength = ((ap.rssi + 100f) / 65f).coerceIn(.12f, 1f)
                val h = 48f + strength * 245f
                val measuredWidth = ap.channelWidthMhz ?: 20
                val halfWidth = (measuredWidth * 0.24f).coerceIn(8f, 45f)
                val path = Path().apply {
                    moveTo(x-halfWidth, baseY)
                    cubicTo(x-halfWidth*.72f, baseY-h, x+halfWidth*.72f, baseY-h, x+halfWidth, baseY)
                }
                drawPath(path, color.copy(alpha = .22f))
                drawPath(path, color.copy(alpha = .9f), style = Stroke(width = 2f))
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    textSize = with(drawContext.density) { 12.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                val fullName = ap.ssid.ifBlank { "Hidden network" }
                val label = if (fullName.length > 22) fullName.take(20) + "…" else fullName
                val labelWidth = labelPaint.measureText(label)
                val labelX = x.coerceIn(left + labelWidth / 2f, right - labelWidth / 2f)
                var labelY = (baseY - h - 9f).coerceAtLeast(18f)
                var candidate = android.graphics.RectF(
                    labelX - labelWidth / 2f, labelY - 14f,
                    labelX + labelWidth / 2f, labelY + 3f
                )
                var attempts = 0
                while (labelBounds.any { android.graphics.RectF.intersects(it, candidate) } && attempts < 12) {
                    labelY = (labelY - 18f).coerceAtLeast(16f)
                    candidate = android.graphics.RectF(
                        labelX - labelWidth / 2f, labelY - 14f,
                        labelX + labelWidth / 2f, labelY + 3f
                    )
                    attempts++
                }
                labelBounds += candidate
                drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
            }
        }
        Text("Channel position · arch height represents received signal strength", color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun WifiSecuritySummary(networks: List<WifiSignal>) {
    val groups = networks.groupingBy { it.security }.eachCount().toList().sortedByDescending { it.second }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SECURITY DISTRIBUTION", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        groups.forEach { (security, count) ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(security, color = TextDim, fontSize = 12.sp)
                Text(count.toString(), color = Accent, fontWeight = FontWeight.Bold)
            }
        }
        Text("Security labels are read from Android scan capabilities; this does not test network configuration or exploitability.", color = TextMuted, fontSize = 10.sp)
    }
}

private fun displayCell(value: Long): String =
    if (value < 0 || value == Int.MAX_VALUE.toLong() || value == Long.MAX_VALUE) "Unavailable" else value.toString()

private fun displaySignal(value: Int): String =
    if (value == Int.MAX_VALUE || value == Int.MIN_VALUE) "Unavailable" else value.toString()


@Composable
private fun SpectrumPanel(aps: List<WifiSignal>) {
    val visible = aps.filter { it.channel > 0 }
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WI-FI CHANNEL SPECTRUM", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("AP channel occupancy · channel width · received signal strength", color = TextMuted, fontSize = 10.sp)
        visible.forEach { ap ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(9.dp)).border(1.dp, Border, RoundedCornerShape(9.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ap.ssid.ifBlank { "Hidden network" }, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${ap.rssi} dBm", color = WifiColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("${ap.band} · CH ${ap.channel} · ${ap.channelWidthMhz ?: 20} MHz", color = TextMuted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp).background(Border, RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth(((ap.rssi + 100f) / 65f).coerceIn(.04f, 1f)).fillMaxHeight().background(WifiColor, RoundedCornerShape(5.dp)))
                }
            }
        }
        if (visible.isEmpty()) Text("No channel data available yet.", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SignalCard(title: String, detail: String, metric: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp))
        .border(1.dp, Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(metric, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WifiCard(sig: WifiSignal, onClick: () -> Unit) {
    val accent = if (sig.isEvilTwin) ThreatColor else WifiColor
    Row(modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp))
        .border(1.dp, Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(Panel2, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("⌁", color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sig.ssid.ifBlank { "Hidden network" }, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sig.bssid} · CH ${sig.channel} · ${sig.security}", color = TextMuted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${sig.rssi} dBm", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
