package com.noorzzz.nhspectrum.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noorzzz.nhspectrum.data.models.ScanSnapshot
import com.noorzzz.nhspectrum.data.repo.ScannerRepository
import com.noorzzz.nhspectrum.hardware.Esp32Bridge
import com.noorzzz.nhspectrum.data.wardrive.WardriveRecorder
import com.noorzzz.nhspectrum.data.wardrive.WardriveSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ScannerRepository(application)
    private val esp32 = Esp32Bridge(application)
    private val wardrive = WardriveRecorder(application)
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(application)
    private var wardriveLocationCallback: LocationCallback? = null
    val wardriveState: StateFlow<WardriveSessionState> = wardrive.state
    val bleStatus: StateFlow<String> = repo.bleStatus

    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val _espConnected = MutableStateFlow(false)
    val espConnected: StateFlow<Boolean> = _espConnected.asStateFlow()
    val espConnecting: StateFlow<Boolean> = esp32.isConnecting

    private val _terminalLines = MutableStateFlow<List<String>>(listOf("ESP32 disconnected · press CONNECT when the hardware is attached"))
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Collect repository data
        viewModelScope.launch {
            repo.snapshot.collect { snapshot ->
                _snapshot.value = snapshot
                wardrive.recordSnapshot(snapshot)
            }
        }

        // Collect ESP32 connection state
        viewModelScope.launch {
            esp32.isConnected.collect { _espConnected.value = it }
        }

        // Collect ESP32 serial output
        viewModelScope.launch {
            esp32.incoming.collect { line ->
                addTerminalLine(line)
            }
        }
    }

    fun startScanning() {
        repo.registerWifiReceiver()
        repo.startBleScan()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive) {
                repo.startWifiScan()
                repo.startBleScan() // Retry when the user grants Nearby Devices permission after app launch.
                repo.scanCell()
                delay(30_000) // Foreground Wi-Fi scans are rate-limited by Android; avoid hammering startScan().
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        repo.unregisterWifiReceiver()
        repo.stopBleScan()
    }

    /**
     * Start a recording-owned GPS subscription. It lives in the shared ViewModel,
     * not MapScreen, so changing tabs does not pause route capture.
     */
    fun startWardriving(latitude: Double, longitude: Double, accuracyMeters: Float, timestamp: Long): Boolean {
        val app = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return false
        if (wardrive.state.value.isRecording) return true

        wardrive.start()
        wardrive.updateFix(latitude, longitude, accuracyMeters, timestamp)

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { fix ->
                    if (fix.hasAccuracy() && fix.accuracy.isFinite() && fix.accuracy > 0f) {
                        wardrive.updateFix(fix.latitude, fix.longitude, fix.accuracy, fix.time)
                    }
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(750L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()
        return try {
            wardriveLocationCallback = callback
            fusedLocation.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (wardriveLocationCallback === callback) {
                        wardriveLocationCallback = null
                        wardrive.stop()
                    }
                }
            true
        } catch (_: SecurityException) {
            wardriveLocationCallback = null
            wardrive.stop()
            false
        }
    }

    fun stopWardriving() {
        wardriveLocationCallback?.let { fusedLocation.removeLocationUpdates(it) }
        wardriveLocationCallback = null
        wardrive.stop()
    }

    fun clearWardriveSession(): Boolean = wardrive.clearSession()

    fun updateWardriveLocation(latitude: Double, longitude: Double, accuracyMeters: Float, timestamp: Long) =
        wardrive.updateFix(latitude, longitude, accuracyMeters, timestamp)

    fun exportWardriveGeoJson(): String = wardrive.exportGeoJson()

    fun connectEsp32() {
        if (esp32.isConnected.value || esp32.isConnecting.value) return
        addTerminalLine("> Manual USB connection requested")
        esp32.connect()
    }

    fun disconnectEsp32() {
        esp32.disconnect()
        addTerminalLine("> ESP32 disconnected")
    }

    // Only allow fixed, passive diagnostic commands over the USB bridge.
    fun sendCommand(cmd: String) {
        if (!esp32.isConnected.value) {
            addTerminalLine("> Rejected: ESP32 is not connected")
            return
        }
        val safeCommand = cmd.trim().lowercase()
        if (safeCommand !in setOf("status", "scanap", "stopscan")) {
            addTerminalLine("> Rejected: command is not in the passive diagnostic allowlist")
            return
        }
        addTerminalLine("> $safeCommand")
        esp32.send(safeCommand)
    }

    fun requestDeviceStatus() {
        esp32.status()
        addTerminalLine("> status requested")
    }

    fun requestPassiveApScan() {
        esp32.scanAp()
        addTerminalLine("> passive AP scan requested")
    }

    private fun addTerminalLine(line: String) {
        _terminalLines.update { (it + line).takeLast(120) }
    }

    override fun onCleared() {
        stopScanning()
        wardriveLocationCallback?.let { fusedLocation.removeLocationUpdates(it) }
        wardriveLocationCallback = null
        esp32.release()
        super.onCleared()
    }
}
