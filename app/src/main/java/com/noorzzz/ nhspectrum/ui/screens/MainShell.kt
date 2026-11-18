package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun MainShell(viewModel: ScannerViewModel) {
    var currentTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.startScanning() }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel)
                2 -> MapScreen(viewModel)
                3 -> SatsScreen(viewModel)
                4 -> OpsScreen(viewModel)
                5 -> TerminalScreen(viewModel)
                6 -> SettingsScreen()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Panel).padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem("◎", "RADAR", currentTab == 0) { currentTab = 0 }
            NavItem("⌁", "SCAN", currentTab == 1) { currentTab = 1 }
            NavItem("🗺", "MAP", currentTab == 2) { currentTab = 2 }
            NavItem("🛰", "SATS", currentTab == 3) { currentTab = 3 }
            NavItem("⌘", "OPS", currentTab == 4) { currentTab = 4 }
            NavItem(">_", "TERM", currentTab == 5) { currentTab = 5 }
            NavItem("⚙", "SET", currentTab == 6) { currentTab = 6 }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Text(icon, fontSize = 15.sp, color = if (selected) Accent else TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.35.sp, color = if (selected) Accent else TextMuted)
    }
}
