package com.noorzzz.nhspectrum.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noorzzz.nhspectrum.data.models.ScanSnapshot
import com.noorzzz.nhspectrum.data.repo.ScannerRepository
import com.noorzzz.nhspectrum.hardware.Esp32Bridge
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

    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val _espConnected = MutableStateFlow(false)
    val espConnected: StateFlow<Boolean> = _espConnected.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<String>>(listOf("> Waiting for ESP32..."))
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Collect repository data
        viewModelScope.launch {
            repo.snapshot.collect { _snapshot.value = it }
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
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive) {
                repo.startWifiScan()
                repo.scanCell()
                delay(8000) // scan every 8 seconds
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        repo.unregisterWifiReceiver()
    }

    fun connectEsp32() {
        esp32.connect()
        addTerminalLine("> Connecting to ESP32...")
    }

    fun disconnectEsp32() {
        esp32.disconnect()
        addTerminalLine("> ESP32 disconnected")
    }

    fun sendCommand(cmd: String) {
        addTerminalLine("> $cmd")
        esp32.send(cmd)
    }

    // Attack helpers
    fun deauth(bssid: String? = null) {
        esp32.deauth(bssid)
        addTerminalLine(if (bssid != null) "> deauth → $bssid" else "> deauth (broadcast)")
    }

    fun beaconSpam() {
        esp32.beaconSpam()
        addTerminalLine("> beacon spam started")
    }

    fun probeSniff() {
        esp32.probeSniff()
        addTerminalLine("> probe sniff started")
    }

    fun handshakeCapture() {
        esp32.handshake()
        addTerminalLine("> handshake / PMKID capture")
    }

    fun stopAttacks() {
        esp32.stopAll()
        addTerminalLine("> all attacks stopped")
    }

    private fun addTerminalLine(line: String) {
        _terminalLines.update { (it + line).takeLast(120) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        esp32.disconnect()
    }
}
