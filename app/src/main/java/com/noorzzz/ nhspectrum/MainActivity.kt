package com.noorzzz.nhspectrum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
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
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF050505), primary = Color(0xFFFF1A1A))) {
                var hasPermissions by remember { mutableStateOf(false) }
                var isBooting by remember { mutableStateOf(true) }
                val viewModel: ScannerViewModel = viewModel()

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
                    hasPermissions = p.values.all { it }
                }

                LaunchedEffect(Unit) {
                    val req = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.READ_PHONE_STATE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) req.add(Manifest.permission.POST_NOTIFICATIONS)
                    val allGranted = req.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) hasPermissions = true else permissionLauncher.launch(req.toTypedArray())
                }

                if (!hasPermissions) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Permissions Required", color = Color.White) }
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
        for (i in fullText.indices) { text = fullText.substring(0, i + 1); delay(10) }
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
fun MainAppUI(viewModel: ScannerViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var selectedWifi by remember { mutableStateOf<WifiNetwork?>(null) }
    var selectedSat by remember { mutableStateOf<Satellite?>(null) }
    var selectedBle by remember { mutableStateOf<BleDevice?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F0F)) {
                listOf("RADAR", "SCAN", "MAP", "SATS", "TERM").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(listOf(Icons.Default.Adjust, Icons.Default.List, Icons.Default.Map, Icons.Default.Star, Icons.Default.Settings).get(index), contentDescription = title, tint = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray) },
                        label = { Text(title, color = if (currentTab == index) Color(0xFFFF1A1A) else Color.Gray, fontSize = 9.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFF050505))) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel, onWifiClick = { selectedWifi = it }, onBleClick = { selectedBle = it })
                2 -> MapScreen(viewModel)
                3 -> SatelliteScreen(viewModel, onSatClick = { selectedSat = it })
                4 -> TerminalScreen(viewModel)
            }
        }
    }

    // DETAIL BOTTOM SHEETS
    if (selectedWifi != null) WifiDetailSheet(selectedWifi!!) { selectedWifi = null }
    if (selectedBle != null) BleDetailSheet(selectedBle!!) { selectedBle = null }
    if (selectedSat != null) SatDetailSheet(selectedSat!!) { selectedSat = null }
}

@Composable
fun RadarScreen(viewModel: ScannerViewModel) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { angle = (angle + 0.03f) % (2 * Math.PI).toFloat() } } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatBadge("WI-FI", wifi.size, Color(0xFF00FFCC))
            StatBadge("BLE", ble.size, Color(0xFFFF9900))
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
fun ScannerScreen(viewModel: ScannerViewModel, onWifiClick: (WifiNetwork) -> Unit, onBleClick: (BleDevice) -> Unit) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = Color(0xFF0F0F0F), edgePadding = 0.dp) {
            listOf("WI-FI", "BLE", "CELL", "DRONE").forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title, color = Color.White, fontSize = 12.sp) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (tabIndex == 0) {
                items(wifi) { net -> 
                    ListItem(
                        modifier = Modifier.clickable { onWifiClick(net) },
                        headlineContent = { Text(net.ssid, color = Color.White) }, 
                        supportingContent = { Text(net.bssid, color = Color.Gray) }, 
                        trailingContent = { Text("${net.rssi} dBm", color = Color(0xFF00FFCC)) }
                    ) 
                }
            } else if (tabIndex == 1) {
                items(ble) { dev -> 
                    ListItem(
                        modifier = Modifier.clickable { onBleClick(dev) },
                        headlineContent = { Text(dev.name, color = Color.White) }, 
                        supportingContent = { Text(dev.mac, color = Color.Gray) }, 
                        trailingContent = { Text("${dev.rssi} dBm", color = Color(0xFFFF9900)) }
                    ) 
                }
            }
        }
    }
}

@Composable
fun SatelliteScreen(viewModel: ScannerViewModel, onSatClick: (Satellite) -> Unit) {
    val sats by viewModel.satList.collectAsState()
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(280.dp).padding(16.dp).background(Color.Black, RoundedCornerShape(140.dp))) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10
            for (i in 1..3) drawCircle(Color(0x3300FF00), radius * (i / 3f), center, style = Stroke(2f))
            drawLine(Color(0x3300FF00), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
            drawLine(Color(0x3300FF00), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)

            sats.forEach { sat ->
                val dist = radius * (1 - (sat.el / 90f))
                val a = (sat.az * Math.PI / 180).toFloat()
                val color = when { sat.snr > 35 -> Color(0xFF00FF00); sat.snr > 20 -> Color(0xFFFFFF00); else -> Color(0xFFFF9900) }
                drawCircle(color, 8f, Offset(center.x + dist * cos(a), center.y + dist * sin(a)))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(sats) { sat ->
                ListItem(
                    modifier = Modifier.clickable { onSatClick(sat) },
                    headlineContent = { Text("${sat.constellation} PRN ${sat.prn}", color = Color.White) },
                    supportingContent = { Text("AZ ${sat.az.toInt()}° EL ${sat.el.toInt()}°", color = Color.Gray) },
                    trailingContent = { Text("${sat.snr} dB", color = Color(0xFF00FF00)) }
                )
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val location by viewModel.location.collectAsState()
    val route by viewModel.wardriveRoute.collectAsState()
    val isWardriving by viewModel.isWardriving.collectAsState()
    val camState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 15f) }

    LaunchedEffect(location) {
        location?.let { camState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 17f) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camState) {
            if (route.isNotEmpty()) {
                Polyline(points = route, color = Color(0xFFFF1A1A), width = 10f)
            }
        }
        Button(
            onClick = { viewModel.isWardriving.value = !isWardriving },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if(isWardriving) Color(0xFF0F0F0F) else Color(0xFFFF1A1A), contentColor = if(isWardriving) Color(0xFFFF1A1A) else Color.White)
        ) { Text(if(isWardriving) "STOP WARDRIVE" else "START WARDRIVE") }
    }
}

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("// ESP32 MARAUDER BRIDGE", color = Color(0xFFFF1A1A), fontWeight = FontWeight.Bold)
        Button(onClick = { viewModel.esp32Bridge.connectToMarauder() }, modifier = Modifier.padding(vertical = 8.dp)) { Text("CONNECT ESP32") }
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Command (scanap, deauth)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF1A1A))
        )
        Button(onClick = { viewModel.esp32Bridge.sendCommand(input); input = "" }, modifier = Modifier.padding(top = 8.dp)) { Text("SEND") }
    }
}

// ================= DETAIL SHEETS =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetailSheet(wifi: WifiNetwork, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0F0F0F)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(wifi.ssid, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("WI-FI ACCESS POINT", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            DetailRow("BSSID", wifi.bssid, Color(0xFFFF1A1A))
            DetailRow("Security", wifi.security, Color(0xFF00FFCC))
            DetailRow("Channel", "${wifi.channel} MHz", Color.White)
            DetailRow("RSSI", "${wifi.rssi} dBm", Color(0xFF00FFCC))
            DetailRow("Vendor", wifi.vendor, Color.White)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDetailSheet(ble: BleDevice, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0F0F0F)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(ble.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("BLUETOOTH LE DEVICE", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            DetailRow("MAC Address", ble.mac, Color(0xFFFF1A1A))
            DetailRow("RSSI", "${ble.rssi} dBm", Color(0xFFFF9900))
            DetailRow("TX Power", "${ble.txPower} dBm", Color.White)
            DetailRow("Connectable", if(ble.connectable) "YES" else "NO", Color.White)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatDetailSheet(sat: Satellite, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0F0F0F)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("${sat.constellation} PRN ${sat.prn}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("SATELLITE", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            DetailRow("Signal (SNR)", "${sat.snr} dB-Hz", Color(0xFF00FF00))
            DetailRow("Elevation", "${sat.el}°", Color.White)
            DetailRow("Azimuth", "${sat.az}°", Color.White)
            DetailRow("In Fix", "YES", Color(0xFF00FF00))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DetailRow(key: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Divider(color = Color(0xFF222222))
}
