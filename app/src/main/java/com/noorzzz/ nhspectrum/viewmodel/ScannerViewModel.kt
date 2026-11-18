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
    val espConnecting: StateFlow<Boolean> = esp32.isConnecting

    private val _terminalLines = MutableStateFlow<List<String>>(listOf("ESP32 disconnected · press CONNECT when the hardware is attached"))
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
        repo.startBleScan()
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
        repo.stopBleScan()
    }

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
        super.onCleared()
        stopScanning()
        esp32.release()
    }
}
