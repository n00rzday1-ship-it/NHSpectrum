package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun OpsScreen(viewModel: ScannerViewModel) {
    val espConnected by viewModel.espConnected.collectAsState()
    var log by remember { mutableStateOf(listOf("System ready. Select a tool.")) }

    fun addLog(msg: String) {
        log = (log + msg).takeLast(40)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "ACTIVE OPERATIONS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))

        // Tool grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("⚡", "Deauth Flood", "Force clients off AP", Modifier.weight(1f)) {
                viewModel.deauth()
                addLog("Deauth flood started")
            }
            ToolCard("📡", "Beacon Spam", "Flood fake SSIDs", Modifier.weight(1f)) {
                viewModel.beaconSpam()
                addLog("Beacon spam active")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("🎭", "Evil Twin", "Rogue AP clone", Modifier.weight(1f)) {
                viewModel.sendCommand("attack -t evil")
                addLog("Evil Twin queued")
            }
            ToolCard("🔍", "Probe Sniff", "Capture probe requests", Modifier.weight(1f)) {
                viewModel.probeSniff()
                addLog("Probe sniff started")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("🔑", "Handshake Cap", "WPA capture", Modifier.weight(1f)) {
                viewModel.handshakeCapture()
                addLog("Handshake capture armed")
            }
            ToolCard("⏹", "Stop All", "Kill all attacks", Modifier.weight(1f)) {
                viewModel.stopAttacks()
                addLog("All attacks stopped")
            }
        }

        Spacer(Modifier.height(24.dp))

        // ESP status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (espConnected) SuccessColor else TextMuted,
                        RoundedCornerShape(50)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (espConnected) "ESP32 CONNECTED" else "ESP32 DISCONNECTED",
                color = if (espConnected) SuccessColor else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (espConnected) "DISCONNECT" else "CONNECT",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (espConnected) viewModel.disconnectEsp32()
                    else viewModel.connectEsp32()
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "OPERATION LOG",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            log.takeLast(12).forEach { line ->
                Text(
                    text = line,
                    color = TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: String,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = TextMuted, fontSize = 10.sp)
    }
}
