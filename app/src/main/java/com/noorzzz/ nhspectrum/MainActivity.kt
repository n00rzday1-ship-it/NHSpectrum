package com.noorzzz.nhspectrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF050505), primary = Color(0xFFFF1A1A))) {
                var hasPermissions by remember { mutableStateOf(false) }
                var isBooting by remember { mutableStateOf(true) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasPermissions = permissions.values.all { it }
                }

                LaunchedEffect(Unit) {
                    val required = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        required.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    
                    val allGranted = required.all { 
                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED 
                    }
                    
                    if (allGranted) {
                        hasPermissions = true
                    } else {
                        permissionLauncher.launch(required.toTypedArray())
                    }
                }

                if (!hasPermissions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Hardware permissions required to scan RF.", color = Color.White)
                    }
                } else if (isBooting) {
                    BootScreen(onBootComplete = { 
                        isBooting = false 
                        // Start background hardware scanner
                        startForegroundService(Intent(this@MainActivity, ScannerService::class.java))
                    })
                } else {
                    RadarDashboard()
                }
            }
        }
    }
}

@Composable
fun BootScreen(onBootComplete: () -> Unit) {
    var terminalText by remember { mutableStateOf("") }
    val fullText = "> INITIALIZING KERNEL... [OK]\n> MOUNTING RF SUBSYSTEMS... [OK]\n> CALIBRATING WIFI MODULE... [OK]\n> CALIBRATING BLE MODULE... [OK]\n> BYPASSING SCAN THROTTLE... [OK]\n> DEVELOPER: NOORZZZ\n> SYSTEM ARMED AND READY."

    LaunchedEffect(Unit) {
        for (i in fullText.indices) {
            terminalText = fullText.substring(0, i + 1)
            delay(20) // Typing speed
        }
        delay(1000)
        onBootComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = terminalText,
                color = Color(0xFF00FFCC),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun RadarDashboard() {
    var angle by remember { mutableFloatStateOf(0f) }
    
    // Animate the radar sweep
    LaunchedEffect(Unit) {
        while(true) {
            withFrameNanos { angle += 0.05f }
            if (angle > (2 * Math.PI).toFloat()) angle = 0f
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050505)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NHSpectrum Dashboard", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Real Hardware Scanning Active", color = Color(0xFF00FFCC), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(20.dp))
            
            // RADAR CANVAS
            Canvas(modifier = Modifier.size(300.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2
                
                // Draw Rings
                for (i in 1..4) {
                    drawCircle(
                        color = Color(0xFFFF1A1A).copy(alpha = 0.2f),
                        radius = radius * (i / 4f),
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
                
                // Draw Sweep Line
                val endX = center.x + radius * cos(angle)
                val endY = center.y + radius * sin(angle)
                drawLine(
                    color = Color(0xFFFF1A1A),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 4f
                )
                
                // Draw Center
                drawCircle(color = Color(0xFFFF1A1A), radius = 8f, center = center)
            }
        }
    }
}
