package com.noorzzz.nhspectrum

import android.Manifest
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

val DarkBg = androidx.compose.ui.graphics.Color(0xFF050505)
val PanelBg = androidx.compose.ui.graphics.Color(0xFF0F0F0F)
val BorderGray = androidx.compose.ui.graphics.Color(0xFF222222)
val CyberRed = androidx.compose.ui.graphics.Color(0xFFFF1A1A)
val WifiCyan = androidx.compose.ui.graphics.Color(0xFF00FFCC)
val BleOrange = androidx.compose.ui.graphics.Color(0xFFFF9900)
val DroneYellow = androidx.compose.ui.graphics.Color(0xFFFFFF00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().load(applicationContext, androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, primary = CyberRed)) {
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Permissions Required", color = androidx.compose.ui.graphics.Color.White) }
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
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(id = R.drawable.logo), "Logo", modifier = Modifier.size(200.dp))
            Spacer(modifier = Modifier.height(30.dp))
            Text(text, color = WifiCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(PanelBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(id = R.drawable.logo), "Logo", modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("NHSPECTRUM", color = CyberRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("by Noorzzz", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 10.sp)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = PanelBg) {
                listOf("RADAR", "SCAN", "MAP", "SATS", "TERM").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(listOf(Icons.Default.Adjust, Icons.Default.List, Icons.Default.Map, Icons.Default.Star, Icons.Default.Settings).get(index), contentDescription = title, tint = if (currentTab == index) CyberRed else androidx.compose.ui.graphics.Color.Gray) },
                        label = { Text(title, color = if (currentTab == index) CyberRed else androidx.compose.ui.graphics.Color.Gray, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(DarkBg)) {
            when (currentTab) {
                0 -> RadarScreen(viewModel)
                1 -> ScannerScreen(viewModel, onWifiClick = { selectedWifi = it }, onBleClick = { selectedBle = it })
                2 -> MapScreen(viewModel)
                3 -> SatelliteScreen(viewModel, onSatClick = { selectedSat = it })
                4 -> TerminalScreen(viewModel)
            }
        }
    }

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBadge("WI-FI", wifi.size, WifiCyan)
            StatBadge("BLE", ble.size, BleOrange)
            StatBadge("DRONE", 0, DroneYellow)
        }
        Spacer(modifier = Modifier.height(30.dp))
        Canvas(modifier = Modifier.size(320.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            for (i in 1..4) drawCircle(CyberRed.copy(alpha = 0.2f), radius * (i / 4f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            val endX = center.x + radius * cos(angle)
            val endY = center.y + sin(angle) * radius
            drawLine(CyberRed, center, Offset(endX, endY), strokeWidth = 4f)
            
            wifi.forEach { net ->
                val dist = radius * (1 - (net.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (net.bssid.hashCode() % 360) * (Math.PI / 180)
                drawCircle(WifiCyan, 6f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
            ble.forEach { dev ->
                val dist = radius * (1 - (dev.rssi.coerceIn(-100, -30) + 100) / 70f)
                val netAngle = (dev.mac.hashCode() % 360) * (Math.PI / 180)
                drawCircle(BleOrange, 6f, Offset(center.x + dist * cos(netAngle).toFloat(), center.y + dist * sin(netAngle).toFloat()))
            }
        }
    }
}

@Composable
fun StatBadge(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp, BorderGray, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text("$count", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun ScannerScreen(viewModel: ScannerViewModel, onWifiClick: (WifiNetwork) -> Unit, onBleClick: (BleDevice) -> Unit) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex, containerColor = PanelBg, contentColor = CyberRed) {
            listOf("WI-FI", "BLE").forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (tabIndex == 0) {
                items(wifi) { net -> SignalCard(net.ssid, net.bssid, "${net.rssi} dBm", WifiCyan) { onWifiClick(net) } }
            } else if (tabIndex == 1) {
                items(ble) { dev -> SignalCard(dev.name, dev.mac, "${dev.rssi} dBm", BleOrange) { onBleClick(dev) } }
            }
        }
    }
}

@Composable
fun SignalCard(title: String, sub: String, rssi: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).border(1.dp, color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text(if (color == WifiCyan) "W" else "B", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp)
            }
            Text(rssi, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SatelliteScreen(viewModel: ScannerViewModel, onSatClick: (Satellite) -> Unit) {
    val sats by viewModel.satList.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GNSS SKYPLOT", color = androidx.compose.ui.graphics.Color(0xFF00FF00), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        Canvas(modifier = Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(130.dp)).border(1.dp, BorderGray, RoundedCornerShape(130.dp))) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10
            for (i in 1..3) drawCircle(androidx.compose.ui.graphics.Color(0x3300FF00), radius * (i / 3f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawLine(androidx.compose.ui.graphics.Color(0x3300FF00), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
            drawLine(androidx.compose.ui.graphics.Color(0x3300FF00), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)

            sats.forEach { sat ->
                val dist = radius * (1 - (sat.el / 90f))
                val a = (sat.az * Math.PI / 180).toFloat()
                val color = when { sat.snr > 35 -> androidx.compose.ui.graphics.Color(0xFF00FF00); sat.snr > 20 -> androidx.compose.ui.graphics.Color(0xFFFFFF00); else -> BleOrange }
                drawCircle(color, 8f, Offset(center.x + dist * cos(a), center.y + dist * sin(a)))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sats) { sat ->
                ListItem(
                    modifier = Modifier.clickable { onSatClick(sat) },
                    headlineContent = { Text("${sat.constellation} PRN ${sat.prn}", color = androidx.compose.ui.graphics.Color.White) },
                    supportingContent = { Text("AZ ${sat.az.toInt()}° EL ${sat.el.toInt()}°", color = androidx.compose.ui.graphics.Color.Gray) },
                    trailingContent = { Text("${sat.snr} dB", color = androidx.compose.ui.graphics.Color(0xFF00FF00), fontWeight = FontWeight.Bold) }
                )
                Divider(color = BorderGray)
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val location by viewModel.location.collectAsState()
    val route by viewModel.wardriveRoute.collectAsState()
    val isWardriving by viewModel.isWardriving.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(XYTileSource("CartoDark", 0, 19, 256, ".png", arrayOf("https://a.basemaps.cartocdn.com/dark_all/")))
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            if (location != null) controller.setCenter(GeoPoint(location!!.latitude, location!!.longitude))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(location) {
        location?.let { mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
    }

    LaunchedEffect(route) {
        mapView.overlays.clear()
        if (route.isNotEmpty()) {
            val polyline = Polyline()
            polyline.setColor(android.graphics.Color.RED)
            polyline.setWidth(10f)
            polyline.setPoints(route.map { GeoPoint(it.latitude, it.longitude) })
            mapView.overlays.add(polyline)
            mapView.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        
        Button(
            onClick = { viewModel.isWardriving.value = !isWardriving },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if(isWardriving) PanelBg else CyberRed, contentColor = if(isWardriving) CyberRed else androidx.compose.ui.graphics.Color.White),
            shape = RoundedCornerShape(8.dp)
        ) { Text(if(isWardriving) "■ STOP WARDRIVE" else "▶ START WARDRIVE", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("// ESP32 MARAUDER BRIDGE", color = CyberRed, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.esp32Bridge.connectToMarauder() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00FF00), contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("CONNECT ESP32", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(16.dp))
        Text("> Waiting for USB connection...", color = WifiCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Command (scanap, deauth)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberRed)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.esp32Bridge.sendCommand(input); input = "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CyberRed)) { Text("SEND", fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetailSheet(wifi: WifiNetwork, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
            Text(wifi.ssid, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("WI-FI ACCESS POINT", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow("BSSID", wifi.bssid, CyberRed)
            DetailRow("Security", wifi.security, WifiCyan)
            DetailRow("Channel", "${wifi.channel} MHz", androidx.compose.ui.graphics.Color.White)
            DetailRow("RSSI", "${wifi.rssi} dBm", WifiCyan)
            DetailRow("Vendor", wifi.vendor, androidx.compose.ui.graphics.Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDetailSheet(ble: BleDevice, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
            Text(ble.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("BLUETOOTH LE DEVICE", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow("MAC Address", ble.mac, CyberRed)
            DetailRow("RSSI", "${ble.rssi} dBm", BleOrange)
            DetailRow("TX Power", "${ble.txPower} dBm", androidx.compose.ui.graphics.Color.White)
            DetailRow("Connectable", if(ble.connectable) "YES" else "NO", androidx.compose.ui.graphics.Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatDetailSheet(sat: Satellite, onDismiss: () -> Unit) {
    ModalBolick: (BleDevice) -> Unit) {
    val wifi by viewModel.wifiList.collectAsState()
    val ble by viewModel.bleList.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex, containerColor = PanelBg, contentColor = CyberRed) {
            listOf("WI-FI", "BLE").forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (tabIndex == 0) {
                items(wifi) { net -> SignalCard(net.ssid, net.bssid, "${net.rssi} dBm", WifiCyan) { onWifiClick(net) } }
            } else if (tabIndex == 1) {
                items(ble) { dev -> SignalCard(dev.name, dev.mac, "${dev.rssi} dBm", BleOrange) { onBleClick(dev) } }
            }
        }
    }
}

@Composable
fun SignalCard(title: String, sub: String, rssi: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).border(1.dp, color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text(if (color == WifiCyan) "W" else "B", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp)
            }
            Text(rssi, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SatelliteScreen(viewModel: ScannerViewModel, onSatClick: (Satellite) -> Unit) {
    val sats by viewModel.satList.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GNSS SKYPLOT", color = androidx.compose.ui.graphics.Color(0xFF00FF00), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        Canvas(modifier = Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(130.dp)).border(1.dp, BorderGray, RoundedCornerShape(130.dp))) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10
            for (i in 1..3) drawCircle(androidx.compose.ui.graphics.Color(0x3300FF00), radius * (i / 3f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawLine(androidx.compose.ui.graphics.Color(0x3300FF00), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
            drawLine(androidx.compose.ui.graphics.Color(0x3300FF00), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)

            sats.forEach { sat ->
                val dist = radius * (1 - (sat.el / 90f))
                val a = (sat.az * Math.PI / 180).toFloat()
                val color = when { sat.snr > 35 -> androidx.compose.ui.graphics.Color(0xFF00FF00); sat.snr > 20 -> androidx.compose.ui.graphics.Color(0xFFFFFF00); else -> BleOrange }
                drawCircle(color, 8f, Offset(center.x + dist * cos(a), center.y + dist * sin(a)))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sats) { sat ->
                ListItem(
                    modifier = Modifier.clickable { onSatClick(sat) },
                    headlineContent = { Text("${sat.constellation} PRN ${sat.prn}", color = androidx.compose.ui.graphics.Color.White) },
                    supportingContent = { Text("AZ ${sat.az.toInt()}° EL ${sat.el.toInt()}°", color = androidx.compose.ui.graphics.Color.Gray) },
                    trailingContent = { Text("${sat.snr} dB", color = androidx.compose.ui.graphics.Color(0xFF00FF00), fontWeight = FontWeight.Bold) }
                )
                Divider(color = BorderGray)
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val location by viewModel.location.collectAsState()
    val route by viewModel.wardriveRoute.collectAsState()
    val isWardriving by viewModel.isWardriving.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(XYTileSource("CartoDark", 0, 19, 256, ".png", arrayOf("https://a.basemaps.cartocdn.com/dark_all/")))
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            if (location != null) controller.setCenter(GeoPoint(location!!.latitude, location!!.longitude))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(location) {
        location?.let { mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
    }

    LaunchedEffect(route) {
        mapView.overlays.clear()
        if (route.isNotEmpty()) {
            val polyline = Polyline()
            polyline.setColor(android.graphics.Color.RED)
            polyline.setWidth(10f)
            polyline.setPoints(route.map { GeoPoint(it.latitude, it.longitude) })
            mapView.overlays.add(polyline)
            mapView.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        
        Button(
            onClick = { viewModel.isWardriving.value = !isWardriving },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if(isWardriving) PanelBg else CyberRed, contentColor = if(isWardriving) CyberRed else androidx.compose.ui.graphics.Color.White),
            shape = RoundedCornerShape(8.dp)
        ) { Text(if(isWardriving) "■ STOP WARDRIVE" else "▶ START WARDRIVE", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun TerminalScreen(viewModel: ScannerViewModel) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("// ESP32 MARAUDER BRIDGE", color = CyberRed, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.esp32Bridge.connectToMarauder() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00FF00), contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("CONNECT ESP32", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(16.dp))
        Text("> Waiting for USB connection...", color = WifiCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Command (scanap, deauth)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberRed)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.esp32Bridge.sendCommand(input); input = "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CyberRed)) { Text("SEND", fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetailSheet(wifi: WifiNetwork, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
            Text(wifi.ssid, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("WI-FI ACCESS POINT", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow("BSSID", wifi.bssid, CyberRed)
            DetailRow("Security", wifi.security, WifiCyan)
            DetailRow("Channel", "${wifi.channel} MHz", androidx.compose.ui.graphics.Color.White)
            DetailRow("RSSI", "${wifi.rssi} dBm", WifiCyan)
            DetailRow("Vendor", wifi.vendor, androidx.compose.ui.graphics.Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDetailSheet(ble: BleDevice, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
            Text(ble.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("BLUETOOTH LE DEVICE", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow("MAC Address", ble.mac, CyberRed)
            DetailRow("RSSI", "${ble.rssi} dBm", BleOrange)
            DetailRow("TX Power", "${ble.txPower} dBm", androidx.compose.ui.graphics.Color.White)
            DetailRow("Connectable", if(ble.connectable) "YES" else "NO", androidx.compose.ui.graphics.Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatDetailSheet(sat: Satellite, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
            Text("${sat.constellation} PRN ${sat.prn}", color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("SATELLITE", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow("Signal (SNR)", "${sat.snr} dB-Hz", androidx.compose.ui.graphics.Color(0xFF00FF00))
            DetailRow("Elevation", "${sat.el}°", androidx.compose.ui.graphics.Color.White)
            DetailRow("Azimuth", "${sat.az}°", androidx.compose.ui.graphics.Color.White)
            DetailRow("In Fix", "YES", androidx.compose.ui.graphics.Color(0xFF00FF00))
        }
    }
}

@Composable
fun DetailRow(key: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
    Divider(color = BorderGray)
}
