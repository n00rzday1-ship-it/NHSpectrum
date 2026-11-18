package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

@Composable
fun OpsScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    val connected by viewModel.espConnected.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val bleStatus by viewModel.bleStatus.collectAsState()
    val api = Build.VERSION.SDK_INT
    val model = (Build.MANUFACTURER + " " + Build.MODEL).trim()
    val fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val phoneState = granted(context, Manifest.permission.READ_PHONE_STATE)
    val bleScanPermission = if (api >= 31) granted(context, Manifest.permission.BLUETOOTH_SCAN) else fineLocation
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val gpsEnabled = remember { runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) }
    val hasGps = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    val hasWifi = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val wifiEnabled = remember {
        runCatching { (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }
            .getOrDefault(false)
    }
    val bluetoothEnabled = remember {
        runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true }
            .getOrDefault(false)
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString() }
            .getOrDefault("Unknown")
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FIELD HARDWARE", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Local capability and permission diagnostics · read-only", color = TextMuted, fontSize = 10.sp)

        OpsSection("DEVICE") {
            DiagnosticRow("Device", model)
            DiagnosticRow("Android", "API " + api)
            DiagnosticRow("NHSpectrum", "Version " + version)
            DiagnosticRow("GPS hardware", if (hasGps) "Available" else "Not reported")
            DiagnosticRow("GPS provider", if (gpsEnabled) "Enabled" else "Disabled")
            DiagnosticRow("Precise location", if (fineLocation) "Granted" else "Not granted", fineLocation)
            DiagnosticRow("Phone state", if (phoneState) "Granted" else "Not granted", phoneState)
        }

        OpsSection("WIRELESS CAPABILITIES") {
            DiagnosticRow("Wi-Fi hardware", if (hasWifi) "Available" else "Not reported")
            DiagnosticRow("Wi-Fi radio", if (wifiEnabled) "Enabled" else "Disabled / unavailable", wifiEnabled)
            DiagnosticRow("BLE hardware", if (hasBle) "Available" else "Not reported")
            DiagnosticRow("Bluetooth radio", if (bluetoothEnabled) "Enabled" else "Disabled / unavailable", bluetoothEnabled)
            DiagnosticRow("BLE scan permission", if (bleScanPermission) "Granted" else "Not granted", bleScanPermission)
            DiagnosticRow("BLE scan state", bleStatus)
            DiagnosticRow("Current observations", "Wi-Fi " + snapshot.wifi.size + " · BLE " + snapshot.ble.size + " · Cell " + snapshot.cell.size + " · Drone " + snapshot.drones.size)
        }

        OpsSection("ESP32 USB TELEMETRY") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(8.dp).background(if (connected) SuccessColor else TextMuted, RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "ESP32 CONNECTED" else "ESP32 DISCONNECTED", color = if (connected) SuccessColor else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("USB serial · 115200 baud · manual connection", color = TextMuted, fontSize = 9.sp)
                }
                Text(
                    if (connected) "DISCONNECT" else "CONNECT",
                    color = if (connected) ThreatColor else Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (connected) viewModel.disconnectEsp32() else viewModel.connectEsp32()
                    }.padding(8.dp)
                )
            }
            if (connected) {
                DiagnosticCard("DEVICE STATUS", "Request firmware and connection status") { viewModel.requestDeviceStatus() }
                DiagnosticCard("PASSIVE AP SCAN", "Request passive access-point telemetry from the attached board") { viewModel.requestPassiveApScan() }
            }
        }

        OpsSection("SAFETY") {
            Text("NHSpectrum performs read-only phone-side RF observations. ESP32 commands are restricted to the passive diagnostic allowlist. Location-tagged sessions are stored locally and should be exported only to an authorized destination.", color = TextMuted, fontSize = 10.sp)
        }
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun OpsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp))
        .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        content()
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, positive: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = when (positive) { true -> SuccessColor; false -> WarningColor; null -> TextPrimary },
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagnosticCard(title: String, description: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(8.dp))
        .border(1.dp, Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(10.dp)) {
        Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(description, color = TextMuted, fontSize = 9.sp)
    }
}
