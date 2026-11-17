package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun OpsScreen(viewModel: ScannerViewModel) {
    val connected by viewModel.espConnected.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FIELD HARDWARE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Authorized USB telemetry and passive diagnostics only.", color = TextDim, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp)) {
            Box(Modifier.size(8.dp).background(if (connected) SuccessColor else TextMuted, RoundedCornerShape(50)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (connected) "ESP32 CONNECTED" else "ESP32 DISCONNECTED", color = if (connected) SuccessColor else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("USB serial · 115200 baud", color = TextMuted, fontSize = 10.sp)
            }
            Text(if (connected) "DISCONNECT" else "CONNECT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                if (connected) viewModel.disconnectEsp32() else viewModel.connectEsp32()
            })
        }
        if (connected) {
            DiagnosticCard("DEVICE STATUS", "Request firmware and connection status") { viewModel.requestDeviceStatus() }
            DiagnosticCard("PASSIVE AP SCAN", "Request a passive access-point inventory") { viewModel.requestPassiveApScan() }
        }
        Text("Security boundary", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Active disruption, rogue access-point creation, credential capture and arbitrary serial commands are not exposed by this build. Use a separately controlled lab toolchain for authorized testing.", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun DiagnosticCard(title: String, description: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(description, color = TextMuted, fontSize = 10.sp)
    }
}
