package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    val lines by viewModel.terminalLines.collectAsState()
    val espConnected by viewModel.espConnected.collectAsState()
    val espConnecting by viewModel.espConnecting.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Panel2).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("// ESP32 TELEMETRY BRIDGE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    when { espConnected -> "USB SERIAL CONNECTED"; espConnecting -> "WAITING FOR USB PERMISSION / CONNECTION"; else -> "DISCONNECTED · MANUAL CONNECTION" },
                    color = if (espConnected) SuccessColor else TextMuted, fontSize = 9.sp
                )
            }
            Text(
                when { espConnected -> "DISCONNECT"; espConnecting -> "WAITING…"; else -> "CONNECT" },
                color = if (espConnected) ThreatColor else Accent,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Panel, RoundedCornerShape(6.dp))
                    .border(1.dp, if (espConnected) ThreatColor else Accent, RoundedCornerShape(6.dp))
                    .clickable(enabled = !espConnecting) { if (espConnected) viewModel.disconnectEsp32() else viewModel.connectEsp32() }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF050807)).padding(12.dp)
        ) {
            items(lines) { line ->
                Text(line, color = when {
                    line.startsWith(">") -> Accent
                    line.contains("ERROR", true) || line.contains("REJECTED", true) -> ThreatColor
                    line.contains("OK", true) || line.contains("CONNECTED", true) -> SuccessColor
                    else -> Color(0xFFA3E4D7)
                }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(Panel2).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (espConnected) input = it },
                enabled = espConnected,
                textStyle = TextStyle(color = if (espConnected) TextPrimary else TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (input.isEmpty()) Text(if (espConnected) "Enter supported diagnostic command" else "Connect ESP32 to enable terminal",
                            color = TextMuted, fontSize = 11.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).background(Panel, RoundedCornerShape(8.dp))
                    .border(1.dp, if (espConnected) Accent.copy(alpha = .5f) else Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 13.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "SEND",
                color = if (espConnected) Color.White else TextMuted,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (espConnected) Accent else Border, RoundedCornerShape(8.dp))
                    .clickable(enabled = espConnected && input.isNotBlank()) {
                        viewModel.sendCommand(input.trim())
                        input = ""
                    }.padding(horizontal = 18.dp, vertical = 13.dp)
            )
        }
    }
}
