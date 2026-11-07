package com.noorzzz.nhspectrum

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiNetwork(val ssid: String, val bssid: String, val rssi: Int, val channel: Int)
data class BleDevice(val name: String, val mac: String, val rssi: Int)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _wifiList = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiList: StateFlow<List<WifiNetwork>> = _wifiList.asStateFlow()

    private val _bleList = MutableStateFlow<List<BleDevice>>(emptyList())
    val bleList: StateFlow<List<BleDevice>> = _bleList.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    init {
        startLocationUpdates()
        startHardwareScans()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(1f)
            .build()
        
        fusedLocationClient.requestLocationUpdates(request, context.mainExecutor) { loc ->
            _location.value = loc
        }
    }

    @SuppressLint("MissingPermission")
    private fun startHardwareScans() {
        // REAL WI-FI
        try {
            wifiManager.startScan()
            val results = wifiManager.scanResults
            _wifiList.value = results.map {
                WifiNetwork(it.SSID.ifEmpty { "Hidden Network" }, it.BSSID, it.level, it.frequency)
            }.sortedByDescending { it.rssi }
        } catch (e: Exception) {}

        // REAL BLE
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            bluetoothManager.adapter.bluetoothLeScanner?.startScan(null, settings, bleCallback)
        } catch (e: Exception) {}
    }

    private val bleCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "Unknown Device"
            val newDevice = BleDevice(name, result.device.address, result.rssi)
            
            val currentList = _bleList.value.toMutableList()
            val index = currentList.indexOfFirst { it.mac == newDevice.mac }
            if (index != -1) {
                currentList[index] = newDevice
            } else {
                currentList.add(newDevice)
            }
            _bleList.value = currentList.sortedByDescending { it.rssi }
        }
    }
}
