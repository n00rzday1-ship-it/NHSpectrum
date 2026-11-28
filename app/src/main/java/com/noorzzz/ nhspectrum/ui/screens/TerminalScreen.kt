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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    val lines by viewModel.terminalLines.collectAsState()
    val espConnected by viewModel.espConnected.collectAsState()
    val espConnecting by viewModel.espConnecting.collectAsState()
    val espReady by viewModel.espReady.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (input.isNotBlank()) {
            viewModel.sendCommand(input)
            input = ""
            keyboard?.hide()
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Panel2).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("// HUNTWARE · ESP32 TELEMETRY", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        espReady -> "HUNTWARE LINK READY · PROTOCOL VERIFIED"
                        espConnected -> "USB OPEN · VERIFYING FIRMWARE"
                        espConnecting -> "WAITING FOR USB PERMISSION"
                        else -> "DISCONNECTED · CONNECT ESP32"
                    },
                    color = if (espReady) SuccessColor else TextMuted, fontSize = 9.sp
                )
            }
            Text(
                when { espConnected -> "DISCONNECT"; espConnecting -> "WAITING…"; else -> "CONNECT" },
                color = if (espConnected) ThreatColor else Accent,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Panel, RoundedCornerShape(6.dp))
                    .border(1.dp, if (espConnected) ThreatColor else Accent, RoundedCornerShape(6.dp))
                    .clickable(enabled = !espConnecting) {
                        if (espConnected) viewModel.disconnectEsp32() else viewModel.connectEsp32()
                    }.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF050807)).padding(12.dp)
        ) {
            items(lines) { line ->
                Text(line, color = when {
                    line.startsWith(">") -> Accent
                    line.contains("ERROR", true) || line.contains("FATAL", true) -> ThreatColor
                    line.contains("PASS", true) || line.contains("READY", true) || line.contains("OBSERVED", true) -> SuccessColor
                    else -> Color(0xFFA3E4D7)
                }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Panel2).navigationBarsPadding().imePadding().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrect = false),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (input.isEmpty()) Text("Type /help · status · scan start · devices", color = TextMuted, fontSize = 11.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).background(Panel, RoundedCornerShape(8.dp))
                    .border(1.dp, Accent.copy(alpha = .55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "SEND", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Accent, RoundedCornerShape(8.dp))
                    .clickable(enabled = input.isNotBlank()) { submit() }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }
    }
}
