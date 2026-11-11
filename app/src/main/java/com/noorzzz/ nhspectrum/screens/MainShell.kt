package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun MainShell(viewModel: ScannerViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel)
                2 -> PlaceholderScreen("MAP")
                3 -> PlaceholderScreen("SATS")
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
            NavItem("◎", "RADAR", selectedTab == 0) { selectedTab = 0 }
            NavItem("📡", "SCAN", selectedTab == 1) { selectedTab = 1 }
            NavItem("🗺️", "MAP", selectedTab == 2) { selectedTab = 2 }
            NavItem("🛰️", "SATS", selectedTab == 3) { selectedTab = 3 }
            NavItem("⚔", "OPS", selectedTab == 4) { selectedTab = 4 }
            NavItem("_", "TERM", selectedTab == 5) { selectedTab = 5 }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
            color = if (selected) Accent else TextMuted,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
