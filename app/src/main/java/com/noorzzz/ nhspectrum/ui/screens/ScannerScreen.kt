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
import com.noorzzz.nhspectrum.data.models.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlin.math.max

private data class Detail(val title: String, val category: String, val fields: List<Pair<String, String>>)

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
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
                else -> WifiChannelsSummary(snapshot.wifi)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    1 -> items(snapshot.ble, key = { it.id }) { s ->
                        SignalCard(s.name.ifBlank { "Unnamed BLE device" }, s.address, "${s.rssi} dBm", BleColor) {
                            selected = Detail(s.name.ifBlank { "Unnamed BLE device" }, "BLUETOOTH LE", listOf(
                                "Name" to s.name.ifBlank { "Not advertised" }, "Address" to s.address,
                                "RSSI" to "${s.rssi} dBm", "TX power" to (s.txPower?.let { "$it dBm" } ?: "Not advertised"),
                                "Last seen" to s.lastSeen.toString()
                            ))
                        }
                    }
                    2 -> items(snapshot.cell) { s ->
                        SignalCard("${s.type} · ${s.operator}", "CID ${displayCell(s.cid)} · TAC ${displayCell(s.tac.toLong())} · PCI ${displayCell(s.pci.toLong())}", "${s.rsrp} dBm", CellColor) {
                            selected = Detail("${s.type} cell", "CELLULAR", listOf(
                                "Technology" to s.type, "Operator" to s.operator.ifBlank { "Unavailable" },
                                "Cell ID" to displayCell(s.cid), "TAC" to displayCell(s.tac.toLong()),
                                "PCI" to displayCell(s.pci.toLong()), "Band" to s.band.ifBlank { "Not exposed by Android" },
                                "RSRP" to "${displaySignal(s.rsrp)} dBm", "RSRQ" to "${displaySignal(s.rsrq)} dB",
                                "Last seen" to s.lastSeen.toString()
                            ))
                        }
                    }
                    3 -> items(snapshot.drones, key = { it.id }) { s ->
                        SignalCard(s.model.ifBlank { "Unidentified broadcast" }, "ID ${s.serial}", "${s.rssi} dBm", DroneColor) {
                            selected = Detail(s.model.ifBlank { "Unidentified broadcast" }, "DRONE / REMOTE ID", listOf(
                                "Model" to s.model.ifBlank { "Not identified" }, "Serial / ID" to s.serial.ifBlank { "Not broadcast" },
                                "RSSI" to "${s.rssi} dBm", "Altitude" to (s.altitude?.let { "$it m" } ?: "Not broadcast"),
                                "Last seen" to s.lastSeen.toString()
                            ))
                        }
                    }
                }
                val empty = when (tab) { 1 -> snapshot.ble.isEmpty(); 2 -> snapshot.cell.isEmpty(); else -> snapshot.drones.isEmpty() }
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
private fun WifiChannelsSummary(networks: List<WifiSignal>) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("${networks.size} access points detected", color = TextMuted, fontSize = 11.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(networks, key = { it.id }) { s ->
                WifiCard(s) {}
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
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val left = 18f
            val right = size.width - 12f
            val baseY = size.height - 18f
            drawLine(Border, Offset(left, baseY), Offset(right, baseY), 1f)
            for (i in 0..4) {
                val x = left + (right-left)*i/4f
                drawLine(Border.copy(alpha = .45f), Offset(x, 8f), Offset(x, baseY), 1f)
            }
            networks.forEach { ap ->
                val channel = ap.channel.coerceIn(channelMin, channelMax)
                val x = left + ((channel-channelMin).toFloat() / max(1, channelMax-channelMin)) * (right-left)
                val strength = ((ap.rssi + 100f) / 65f).coerceIn(.12f, 1f)
                val h = 28f + strength * 85f
                val measuredWidth = ap.channelWidthMhz ?: 20
                val halfWidth = (measuredWidth * 0.24f).coerceIn(8f, 45f)
                val path = Path().apply {
                    moveTo(x-halfWidth, baseY)
                    cubicTo(x-halfWidth*.72f, baseY-h, x+halfWidth*.72f, baseY-h, x+halfWidth, baseY)
                }
                drawPath(path, color.copy(alpha = .22f))
                drawPath(path, color.copy(alpha = .9f), style = Stroke(width = 2f))
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
