package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    val lines by viewModel.terminalLines.collectAsState()
    val espConnected by viewModel.espConnected.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "// ESP32 MARAUDER BRIDGE",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (espConnected) "CONNECTED" else "CONNECT",
                color = if (espConnected) SuccessColor else Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Panel, RoundedCornerShape(6.dp))
                    .border(1.dp, if (espConnected) SuccessColor else Accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickableSafely {
                        if (espConnected) viewModel.disconnectEsp32()
                        else viewModel.connectEsp32()
                    }
            )
        }

        // Terminal body
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF050607))
                .padding(12.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = when {
                        line.startsWith(">") -> Accent
                        line.contains("ERROR", true) -> ThreatColor
                        line.contains("OK", true) || line.contains("CONNECTED", true) -> SuccessColor
                        else -> Color(0xFFA3E4D7)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel2)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .background(Panel, RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "SEND",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickableSafely {
                        if (input.isNotBlank()) {
                            viewModel.sendCommand(input.trim())
                            input = ""
                        }
                    }
            )
        }
    }
}

// small helper to avoid needing extra imports
@Composable
private fun Modifier.clickableSafely(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable { onClick() }
    )
}
