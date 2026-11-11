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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.data.models.WifiSignal
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var tab by remember { mutableStateOf(0) } // 0=wifi 1=ble 2=cell 3=drone

    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 8.dp)
        ) {
            listOf("WI-FI", "BLE", "CELL", "DRONE").forEachIndexed { i, title ->
                val selected = tab == i
                Text(
                    text = title,
                    color = if (selected) Accent else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .clickable { tab = i }
                )
            }
        }

        // List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                0 -> items(snapshot.wifi) { sig -> WifiCard(sig) }
                1 -> items(snapshot.ble) { /* simple for now */ }
                2 -> items(snapshot.cell) { /* simple for now */ }
                3 -> items(snapshot.drones) { /* simple for now */ }
            }
        }
    }
}

@Composable
private fun WifiCard(sig: WifiSignal) {
    val borderColor = if (sig.isEvilTwin) ThreatColor else WifiColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Panel2, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("W", color = borderColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sig.ssid,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "\( {sig.bssid}  ·  CH \){sig.channel}  ·  ${sig.security}",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${sig.rssi}",
            color = borderColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
