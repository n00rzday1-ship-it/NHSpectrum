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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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
                    LaunchedEffect(Unit) {
                        delay(3000)
                        isBooting = false
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "NHSpectrum Logo",
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("NHSPECTRUM V.01", color = Color(0xFFFF1A1A), fontWeight = FontWeight.Bold)
                            Text("by Noorzzz", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LaunchedEffect(Unit) {
                        startForegroundService(Intent(this@MainActivity, ScannerService::class.java))
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Hardware Scanners Active.\nRadar UI Coming Next.", color = Color.White)
                    }
                }
            }
        }
    }
}
