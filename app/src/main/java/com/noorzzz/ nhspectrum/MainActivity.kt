uupackage com.noorzzz.nhspectrum

import android.Manifest
import package com.noorzzz.nhspectrum

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
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
                val viewModel: ScanViewModel = viewModel()

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
                    hasPermissions = p.values.all { it }
                }

                LaunchedEffect(Unit) {
                    val required = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) required.add(Manifest.permission.POST_NOTIFICATIONS)
                    
                    val allGranted = required.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) hasPermissions = true else permissionLauncher.launch(required.toTypedArray())
                }

                if (!hasPermissions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Hardware permissions required.", color = Color.White)
                    }
                } else if (isBooting) {
                    BootScreen { isBooting = false }
                } else {
                    LaunchedEffect(Unit) {
                        val intent = Intent(this@MainActivity, ScannerService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                    MainAppUI(viewModel)
                }
            }
        }
    }
}

@Composable
fun BootScreen(onComplete: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val fullText = "> INITIALIZING KERNEL... [OK]\n> MOUNTING RF SUBSYSTEMS... [OK]\n> CALIBRATING WIFI & BLE... [OK]\n> DEVELOPER: NOORZZZ\n> SYSTEM ARMED AND READY."

    LaunchedEffect(Unit) {
        for (i in fullText.indices) { text = fullText.substring(0, i + 1); delay(15) }
        delay(1000); onComplete()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(id = R.drawable.logo), "Logo", modifier = Modifier.size(200.dp))
            Spacer(modifier = Modifier.height(30.dp))
            Text(text, color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
fun MainAppUI(viewModel: ScanViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("RADAR", "SCAN", "SATS", "MAP", "TERM")
    val icons = listOf(Icons.Default.Adjust, Icons.Default.SettingsInputAntenna, Icons.Default.Satellite, Icons.Default.Map, Icons.Default.Terminal)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F0F)) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(icons[index], contentDescription = title, tint = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray) },
                        label = { Text(title, color = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray, fontSize = 9.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFF050505))) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel)
                2 -> SatelliteScreen(viewModel)
                3 -> MapScreen(viewModel)
                4 -> TerminalScreen()
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: ScanViewModel) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    val drones by viewModel.droneList.collectAsState()
    var angle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) { withFrameNanos { angle = (angle + 0.03f) % (2 * Math.PI).toFloat() } }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatBadge("WI-FI", wifi.size, Color(0xFF00FFCC))
            StatBadge("BLE", ble.size, Color(0xFFFF9900))
            StatBadge("DRONE", drones.size, Color(0xFFFFFF00))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            for (i in 1..4) drawCircle(Color(0xFFFF1A1A).copy(alpha = 0.2f), radius * (i / 4f), center, style = Stroke(2f))
            val endX = center.x + radius * cos(angle)
            val endY = center.y + sin(angle) * radius
            drawLine(Color(0xFFFF1A1A), center, Offset(endX, endY), strokeWidth = 4f)
            
            wifi.forEach { net ->
                val dist = radius * (1 - (net.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (net.bssid.hashCode() % 360) * (Math.PI / 180)
                drawCircle(Color(0xFF00FFCC), 5f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
            ble.forEach { dev ->
                val dist = radius * (1 - (dev.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (dev.mac.hashCode() % 360) * (Math.PI / 180)
                drawCircle(Color(0xFFFF9900), 5f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
            drones.forEach { drone ->
                val netAngle = (drone.serial.hashCode() % 360) * (Math.PI / 180)
                drawCircle(Color(0xFFFFFF00), 7f, Offset(center.x + (radius * 0.5f) * cos(netAngle).toFloat(), center.y + (radius * 0.5f) * sin(netAngle).toFloat()))
            }
        }
    }
}

@Composable
fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text("$count", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 9.sp)
    }
}

@Composable
fun ScannerScreen(viewModel: ScanViewModel) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    val cell by viewModel.cellList.collectAsState()
    val drones by viewModel.droneList.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("WI-FI", "BLE", "CELL", "DRONE")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = Color(0xFF0F0F0F), edgePadding = 0.dp) {
            tabTitles.forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title, color = Color.White, fontSize = 12.sp) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            when (tabIndex) {
                0 -> items(wifi) { net -> ListItem(headlineContent = { Text(net.ssid, color = Color.White) }, supportingContent = { Text(net.bssid, color = Color.Gray) }, trailingContent = { Text("${net.rssi} dBm", color = Color(0xFF00FFCC)) }) }
                1 -> items(ble) { dev -> ListItem(headlineContent = { Text(dev.name, color = Color.White) }, supportingContent = { Text(dev.mac, color = Color.Gray) }, trailingContent = { Text("${dev.rssi} dBm", color = Color(0xFFFF9900)) }) }
                2 -> items(cell) { tower -> ListItem(headlineContent = { Text(tower.type, color = Color.White) }, supportingContent = { Text(tower.cid, color = Color.Gray) }, trailingContent = { Text("${tower.rssi} dBm", color = Color(0xFFFF00FF)) }) }
                3 -> items(drones) { drone -> ListItem(headlineContent = { Text(drone.serial, color = Color.White) }, supportingContent = { Text("Lat: ${drone.lat}, Lon: ${drone.lon}", color = Color.Gray) }, trailingContent = { Text("ALT: ${drone.alt}m", color = Color(0xFFFFFF00)) }) }
            }
        }
    }
}

@Composable
fun SatelliteScreen(viewModel: ScanViewModel) {
    val sats by viewModel.satList.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GNSS SKYPLOT", color = Color(0xFF00FF00), fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Canvas(modifier = Modifier.size(280.dp).background(Color.Black, RoundedCornerShape(140.dp))) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10

            for (i in 1..3) drawCircle(Color(0x3300FF00), radius * (i / 3f), center, style = Stroke(2f))
            drawLine(Color(0x3300FF00), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
            drawLine(Color(0x3300FF00), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)

            sats.forEach { sat ->
                val dist = radius * (1 - (sat.el / 90f))
                val a = (sat.az * Math.PI / 180).toFloat()
                val x = center.x + dist * cos(a)
                val y = center.y + dist * sin(a)
                
                val color = when {
                    sat.snr > 35 -> Color(0xFF00FF00)
                    sat.snr > 20 -> Color(0xFFFFFF00)
                    else -> Color(0xFFFF9900)
                }
                drawCircle(color, 8f, Offset(x, y))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(sats) { sat ->
                ListItem(
                    headlineContent = { Text("PRN ${sat.prn}", color = Color.White) },
                    supportingContent = { Text("AZ ${sat.az.toInt()}° EL ${sat.el.toInt()}°", color = Color.Gray) },
                    trailingContent = { Text("${sat.snr} dB", color = Color(0xFF00FF00)) }
                )
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: ScanViewModel) {
    val location by viewModel.location.collectAsState()
    val defaultLoc = LatLng(37.7749, -122.4194)
    val camState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultLoc, 15f) }

    LaunchedEffect(location) {
        location?.let { camState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 17f) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camState, uiSettings = MapUiSettings(zoomControlsEnabled = true))
        Button(
            onClick = { },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1A1A))
        ) { Text("START WARDRIVE") }
    }
}

@Composable
fun TerminalScreen() {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("// ESP32 MARAUDER BRIDGE", color = Color(0xFFFF1A1A), fontWeight = FontWeight.Bold)
        Text("> Waiting for USB connection...", color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Command (scanap, deauth)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF1A1A))
        )
    }
}
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, ScannerService::class.java))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF050505), primary = Color(0xFFFF1A1A))) {
                var hasPermissions by remember { mutableStateOf(false) }
                var isBooting by remember { mutableStateOf(true) }
                val viewModel: ScanViewModel = viewModel()

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
                    hasPermissions = p.values.all { it }
                }

                LaunchedEffect(Unit) {
                    val required = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) required.add(Manifest.permission.POST_NOTIFICATIONS)
                    
                    val allGranted = required.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) hasPermissions = true else permissionLauncher.launch(required.toTypedArray())
                }

                if (!hasPermissions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Hardware permissions required.", color = Color.White)
                    }
                } else if (isBooting) {
                    BootScreen { isBooting = false }
                } else {
                    MainAppUI(viewModel)
                }
            }
        }
    }
}

@Composable
fun BootScreen(onComplete: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val fullText = "> INITIALIZING KERNEL... [OK]\n> MOUNTING RF SUBSYSTEMS... [OK]\n> CALIBRATING WIFI & BLE... [OK]\n> DEVELOPER: NOORZZZ\n> SYSTEM ARMED AND READY."

    LaunchedEffect(Unit) {
        for (i in fullText.indices) { text = fullText.substring(0, i + 1); delay(15) }
        delay(1000); onComplete()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(id = R.drawable.logo), "Logo", modifier = Modifier.size(200.dp))
            Spacer(modifier = Modifier.height(30.dp))
            Text(text, color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
fun MainAppUI(viewModel: ScanViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("RADAR", "SCANNER", "MAP", "TERMINAL")
    val icons = listOf(Icons.Default.Radar, Icons.Default.SettingsInputAntenna, Icons.Default.Map, Icons.Default.Terminal)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F0F)) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(icons[index], contentDescription = title, tint = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray) },
                        label = { Text(title, color = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFF050505))) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel)
                2 -> MapScreen(viewModel)
                3 -> TerminalScreen()
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: ScanViewModel) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var angle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { angle = (angle + 0.03f) % (2 * Math.PI).toFloat() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatBadge("WI-FI", wifi.size, Color(0xFF00FFCC))
            StatBadge("BLE", ble.size, Color(0xFFFF9900))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Rings
            for (i in 1..4) drawCircle(Color(0xFFFF1A1A).copy(alpha = 0.2f), radius * (i / 4f), center, style = Stroke(2f))
            
            // Sweep
            val endX = center.x + radius * cos(angle)
            val endY = center.y + sin(angle) * radius
            drawLine(Color(0xFFFF1A1A), center, Offset(endX, endY), strokeWidth = 4f)
            
            // Real Data Dots (Simulated position based on RSSI)
            wifi.forEach { net ->
                val dist = radius * (1 - (net.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (net.bssid.hashCode() % 360) * (Math.PI / 180)
                drawCircle(Color(0xFF00FFCC), 5f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
            ble.forEach { dev ->
                val dist = radius * (1 - (dev.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (dev.mac.hashCode() % 360) * (Math.PI / 180)
                drawCircle(Color(0xFFFF9900), 5f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
        }
    }
}

@Composable
fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text("$count", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun ScannerScreen(viewModel: ScanViewModel) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var isWifiTab by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (isWifiTab) 0 else 1, containerColor = Color(0xFF0F0F0F)) {
            Tab(selected = isWifiTab, onClick = { isWifiTab = true }, text = { Text("WI-FI", color = Color.White) })
            Tab(selected = !isWifiTab, onClick = { isWifiTab = false }, text = { Text("BLE", color = Color.White) })
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (isWifiTab) {
                items(wifi) { net ->
                    ListItem(headlineContent = { Text(net.ssid, color = Color.White) }, supportingContent = { Text("${net.bssid} | ${net.channel}MHz", color = Color.Gray) }, trailingContent = { Text("${net.rssi} dBm", color = Color(0xFF00FFCC)) })
                }
            } else {
                items(ble) { dev ->
                    ListItem(headlineContent = { Text(dev.name, color = Color.White) }, supportingContent = { Text(dev.mac, color = Color.Gray) }, trailingContent = { Text("${dev.rssi} dBm", color = Color(0xFFFF9900)) })
                }
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: ScanViewModel) {
    val location by viewModel.location.collectAsState()
    val defaultLoc = LatLng(37.7749, -122.4194)
    val camState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultLoc, 15f) }

    LaunchedEffect(location) {
        location?.let { camState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 17f) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true)
        )
        Button(
            onClick = { },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1A1A))
        ) {
            Text("START WARDRIVE")
        }
    }
}

@Composable
fun TerminalScreen() {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("// ESP32 MARAUDER BRIDGE", color = Color(0xFFFF1A1A), fontWeight = FontWeight.Bold)
        Text("> Waiting for USB connection...", color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Command (scanap, deauth)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF1A1A))
        )
    }
}
