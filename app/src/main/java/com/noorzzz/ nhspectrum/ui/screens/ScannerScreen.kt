package com.noorzzz.nhspectrum.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.data.models.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

private data class Detail(val title: String, val category: String, val fields: List<Pair<String, String>>)

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Detail?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp)) {
            listOf("WI-FI", "BLE", "CELL", "DRONE").forEachIndexed { i, title ->
                Text(title, color = if (tab == i) Accent else TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { tab = i }.padding(horizontal = 12.dp, vertical = 14.dp))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> items(snapshot.wifi, key = { it.id }) { s ->
                    WifiCard(s, onClick = { selected = Detail(s.ssid.ifBlank { "Hidden Wi-Fi network" }, "WI-FI", listOf(
                        "SSID" to s.ssid.ifBlank { "Hidden" }, "BSSID" to s.bssid, "Manufacturer" to s.vendor,
                        "Frequency" to "${s.frequency} MHz", "Channel" to s.channel.toString(), "Band" to s.band,
                        "Security" to s.security, "Signal" to "${s.rssi} dBm", "Last seen" to s.lastSeen.toString()
                    )) })
                }
                1 -> items(snapshot.ble, key = { it.id }) { s ->
                    SignalCard(s.name.ifBlank { "Unnamed BLE device" }, s.address, "${s.rssi} dBm", BleColor) {
                        selected = Detail(s.name.ifBlank { "Unnamed BLE device" }, "BLUETOOTH LE", listOf(
                            "Name" to s.name.ifBlank { "Not advertised" }, "Address" to s.address,
                            "RSSI" to "${s.rssi} dBm", "TX power" to (s.txPower?.let { "$it dBm" } ?: "Not advertised"),
                            "Last seen" to s.lastSeen.toString()
                        ))
                    }
                }
                2 -> items(snapshot.cell, key = { it.id }) { s ->
                    SignalCard("${s.type} · ${s.operator}", "CID ${s.cid} · TAC ${s.tac} · PCI ${s.pci}", "${s.rsrp} dBm", CellColor) {
                        selected = Detail("${s.type} cell", "CELLULAR", listOf(
                            "Technology" to s.type, "Operator" to s.operator, "Cell ID" to s.cid.toString(),
                            "TAC" to s.tac.toString(), "PCI" to s.pci.toString(), "Band" to s.band.ifBlank { "Not exposed" },
                            "RSRP" to "${s.rsrp} dBm", "RSRQ" to "${s.rsrq} dB", "Last seen" to s.lastSeen.toString()
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
            val empty = when (tab) { 0 -> snapshot.wifi.isEmpty(); 1 -> snapshot.ble.isEmpty(); 2 -> snapshot.cell.isEmpty(); else -> snapshot.drones.isEmpty() }
            if (empty) item { Text("No observations yet. Check scan status and Android permissions.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp)) }
        }
    }

    selected?.let { detail ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Panel,
            title = {
                Column {
                    Text(detail.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(detail.category, color = Accent, fontSize = 10.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.fields.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("CLOSE", color = Accent) } }
        )
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
