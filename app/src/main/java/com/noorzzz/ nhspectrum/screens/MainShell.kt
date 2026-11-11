package com.noorzzz.nhspectrum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    // Start real scanning when main screen appears
    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NH SPECTRUM",
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Core online\nRadar • Scanner • Map • Ops coming next",
                color = TextDim,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}
