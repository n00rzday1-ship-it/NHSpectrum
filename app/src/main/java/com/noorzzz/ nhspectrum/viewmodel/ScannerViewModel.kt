package com.noorzzz.nhspectrum.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
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
     * Start recording using the latest accepted fix from MapScreen's single GPS stream.
     * The map owns the one LocationManager subscription; the recorder must not register
     * a second listener with a different timestamp cadence.
     */
    fun startWardriving(latitude: Double, longitude: Double, accuracyMeters: Float, timestamp: Long): Boolean {
        val app = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(
            app, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !latitude.isFinite() || !longitude.isFinite() ||
            !accuracyMeters.isFinite() || accuracyMeters <= 0f || accuracyMeters > 25f || timestamp <= 0L
        ) return false
        if (wardrive.state.value.isRecording) return true
        wardrive.start()
        wardrive.updateFix(latitude, longitude, accuracyMeters, timestamp)
        return true
    }

    fun stopWardriving() {
        wardrive.stop()
    }

    fun clearWardriveSession(): Boolean = wardrive.clearSession()

    fun updateWardriveLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        timestamp: Long,
        speedMetersPerSecond: Float? = null,
        speedAccuracyMetersPerSecond: Float? = null
    ) = wardrive.updateFix(
        latitude, longitude, accuracyMeters, timestamp, speedMetersPerSecond, speedAccuracyMetersPerSecond
    )

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
        esp32.release()
        super.onCleared()
    }
}
