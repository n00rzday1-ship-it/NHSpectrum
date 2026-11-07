package com.noorzzz.nhspectrum

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiNetwork(val ssid: String, val bssid: String, val rssi: Int, val channel: Int)
data class BleDevice(val name: String, val mac: String, val rssi: Int)
data class CellTower(val operator: String, val type: String, val cid: String, val rssi: Int)
data class DroneDevice(val serial: String, val lat: Double, val lon: Double, val alt: Double)
data class Satellite(val prn: Int, val az: Float, val el: Float, val snr: Float)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _wifiList = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiList: StateFlow<List<WifiNetwork>> = _wifiList.asStateFlow()

    private val _bleList = MutableStateFlow<List<BleDevice>>(emptyList())
    val bleList: StateFlow<List<BleDevice>> = _bleList.asStateFlow()

    private val _cellList = MutableStateFlow<List<CellTower>>(emptyList())
    val cellList: StateFlow<List<CellTower>> = _cellList.asStateFlow()

    private val _droneList = MutableStateFlow<List<DroneDevice>>(emptyList())
    val droneList: StateFlow<List<DroneDevice>> = _droneList.asStateFlow()

    private val _satList = MutableStateFlow<List<Satellite>>(emptyList())
    val satList: StateFlow<List<Satellite>> = _satList.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    init {
        startLocationUpdates()
        startHardwareScans()
        startGnssUpdates()
        startCellUpdates()
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
    private fun startGnssUpdates() {
        try {
            locationManager.registerGnssStatusCallback(context.mainExecutor, object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val sats = mutableListOf<Satellite>()
                    for (i in 0 until status.satelliteCount) {
                        sats.add(Satellite(status.getSvid(i), status.getAzimuthDegrees(i), status.getElevationDegrees(i), status.getCn0DbHz(i)))
                    }
                    _satList.value = sats
                }
            })
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startCellUpdates() {
        try {
            val cells = telephonyManager.allCellInfo
            val towers = mutableListOf<CellTower>()
            for (cell in cells) {
                val type = when (cell) {
                    is android.telephony.CellInfoLte -> "LTE"
                    is android.telephony.CellInfoGsm -> "GSM"
                    is android.telephony.CellInfoWcdma -> "3G"
                    is android.telephony.CellInfoNr -> "5G"
                    else -> "UNKNOWN"
                }
                val cid = cell.cellIdentity.toString().take(20)
                towers.add(CellTower(telephonyManager.networkOperatorName ?: "Unknown", type, cid, -90))
            }
            _cellList.value = towers
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startHardwareScans() {
        try {
            wifiManager.startScan()
            val results = wifiManager.scanResults
            _wifiList.value = results.map {
                WifiNetwork(it.SSID.ifEmpty { "Hidden" }, it.BSSID, it.level, it.frequency)
            }.sortedByDescending { it.rssi }
        } catch (e: Exception) {}

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            bluetoothManager.adapter.bluetoothLeScanner?.startScan(null, settings, bleCallback)
        } catch (e: Exception) {}
    }

    private val bleCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "Unknown"
            
            if (name.contains("Remote ID", true) || result.scanRecord?.manufacturerSpecificData != null) {
                val currentDrones = _droneList.value.toMutableList()
                if (currentDrones.none { it.serial == result.device.address }) {
                    currentDrones.add(DroneDevice("DRN-${result.device.address.takeLast(5)}", _location.value?.latitude ?: 0.0, _location.value?.longitude ?: 0.0, 120.0))
                    _droneList.value = currentDrones
                }
            }

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
