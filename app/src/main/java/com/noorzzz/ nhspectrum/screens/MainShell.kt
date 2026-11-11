package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun MainShell(viewModel: ScannerViewModel) {
    var currentTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel)
                2 -> PlaceholderScreen("MAP", "Wardrive map coming next")
                3 -> PlaceholderScreen("SATS", "GNSS skyplot coming next")
                4 -> OpsScreen(viewModel)
                5 -> TerminalScreen(viewModel)
            }
        }

        // Bottom Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem("◎", "RADAR", currentTab == 0) { currentTab = 0 }
            NavItem("📡", "SCAN", currentTab == 1) { currentTab = 1 }
            NavItem("🗺️", "MAP", currentTab == 2) { currentTab = 2 }
            NavItem("🛰️", "SATS", currentTab == 3) { currentTab = 3 }
            NavItem("⚔", "OPS", currentTab == 4) { currentTab = 4 }
            NavItem("_", "TERM", currentTab == 5) { currentTab = 5 }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            color = if (selected) Accent else TextMuted
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = if (selected) Accent else TextMuted
        )
    }
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
    }
}
