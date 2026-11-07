package com.noorzzz.nhspectrum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isBooting by remember { mutableStateOf(true) }
            var bootText by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                val lines = listOf("> INIT KERNEL", "> LOAD SCANNERS", "> NOORZZZ", "> SYSTEM READY.")
                for (line in lines) {
                    bootText += "$line\n"
                    delay(600)
                }
                delay(1000)
                isBooting = false
            }

            if (isBooting) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bootText,
                        color = Color(0xFFFF1A1A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF050505)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NHSpectrum Dashboard\nScanner Engine Online",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
