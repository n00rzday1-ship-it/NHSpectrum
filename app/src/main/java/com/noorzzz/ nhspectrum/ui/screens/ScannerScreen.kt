package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp)) {
            listOf("WI-FI", "BLE", "CELL", "DRONE").forEachIndexed { i, title ->
                Text(title, color = if (tab == i) Accent else TextMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp).clickable { tab = i })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> items(snapshot.wifi) { WifiCard(it) }
                1 -> items(snapshot.ble) { signal -> SignalCard(signal.name.ifBlank { "Unnamed BLE device" }, signal.address, "${signal.rssi} dBm", BleColor) }
                2 -> items(snapshot.cell) { signal -> SignalCard("${signal.type} · ${signal.operator}", "CID ${signal.cid} · TAC ${signal.tac} · PCI ${signal.pci}", "${signal.rsrp} dBm", CellColor) }
                3 -> items(snapshot.drones) { signal -> SignalCard(signal.model.ifBlank { "Unidentified broadcast" }, "Serial ${signal.serial}", "${signal.rssi} dBm", DroneColor) }
            }
            val empty = when (tab) { 0 -> snapshot.wifi.isEmpty(); 1 -> snapshot.ble.isEmpty(); 2 -> snapshot.cell.isEmpty(); else -> snapshot.drones.isEmpty() }
            if (empty) item { Text("No observations yet. Start a scan and check Android permissions.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp)) }
        }
    }
}

@Composable
private fun SignalCard(title: String, detail: String, metric: String, accent: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(metric, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WifiCard(sig: WifiSignal) {
    val accent = if (sig.isEvilTwin) ThreatColor else WifiColor
    Row(modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(Panel2, RoundedCornerShape(8.dp)).border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("W", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sig.ssid, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sig.bssid} · CH ${sig.channel} · ${sig.security}", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${sig.rssi} dBm", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
