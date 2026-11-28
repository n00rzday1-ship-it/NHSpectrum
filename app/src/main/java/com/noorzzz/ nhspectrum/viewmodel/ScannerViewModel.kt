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
import org.json.JSONObject

data class EspObservedDevice(
    val id: String,
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val channel: Int,
    val lastSeenUptimeMs: Long
)

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
    val espReady: StateFlow<Boolean> = esp32.isReady

    private val _espDevices = MutableStateFlow<List<EspObservedDevice>>(emptyList())
    val espDevices: StateFlow<List<EspObservedDevice>> = _espDevices.asStateFlow()
    private val _selectedEspDevice = MutableStateFlow<EspObservedDevice?>(null)
    val selectedEspDevice: StateFlow<EspObservedDevice?> = _selectedEspDevice.asStateFlow()

    private val _terminalLines = MutableStateFlow(listOf(
        "HUNTWARE // NHSpectrum hardware console",
        "USB companion: not connected",
        "Type /help for local commands and supported ESP32 operations."
    ))
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            repo.snapshot.collect { current ->
                _snapshot.value = current
                wardrive.recordSnapshot(current)
            }
        }
        viewModelScope.launch { esp32.isConnected.collect { _espConnected.value = it } }
        viewModelScope.launch {
            esp32.incoming.collect { line -> handleEspLine(line) }
        }
        viewModelScope.launch {
            esp32.isReady.collect { ready ->
                if (ready) {
                    addTerminalLine(" ")
                    addTerminalLine("H U N T W A R E  //  LINK ESTABLISHED")
                    addTerminalLine("ESP32 protocol v1 verified · USB serial is responding")
                    addTerminalLine("[SELF-CHECK] Requesting firmware diagnostics…")
                    esp32.send("self_test")
                    esp32.send("status")
                }
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
                repo.startBleScan()
                repo.scanCell()
                delay(30_000)
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        repo.unregisterWifiReceiver()
        repo.stopBleScan()
    }

    fun startWardriving(latitude: Double, longitude: Double, accuracyMeters: Float, timestamp: Long): Boolean {
        val app = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !latitude.isFinite() || !longitude.isFinite() ||
            !accuracyMeters.isFinite() || accuracyMeters <= 0f || accuracyMeters > 25f || timestamp <= 0L
        ) return false
        if (wardrive.state.value.isRecording) return true
        wardrive.start()
        wardrive.updateFix(latitude, longitude, accuracyMeters, timestamp)
        return true
    }

    fun stopWardriving() = wardrive.stop()
    fun clearWardriveSession(): Boolean = wardrive.clearSession()

    fun updateWardriveLocation(
        latitude: Double, longitude: Double, accuracyMeters: Float, timestamp: Long,
        speedMetersPerSecond: Float? = null, speedAccuracyMetersPerSecond: Float? = null
    ) = wardrive.updateFix(latitude, longitude, accuracyMeters, timestamp, speedMetersPerSecond, speedAccuracyMetersPerSecond)

    fun exportWardriveGeoJson(): String = wardrive.exportGeoJson()

    fun connectEsp32() {
        if (esp32.isConnected.value || esp32.isConnecting.value) return
        addTerminalLine("> USB device connection requested")
        esp32.connect()
    }

    fun disconnectEsp32() {
        esp32.disconnect()
        addTerminalLine("> ESP32 USB link closed")
    }

    /** Local help/device commands never require a connected board. */
    fun sendCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank()) return
        val normalized = command.lowercase()
        addTerminalLine("> $command")

        when {
            normalized in setOf("/help", "help", "?") -> printHelp()
            normalized == "clear" -> _terminalLines.value = listOf("HUNTWARE // console cleared", "Type /help for commands.")
            normalized in setOf("devices", "device list") -> printDevices()
            normalized.startsWith("select ") -> selectDevice(command.substringAfter(' ').trim())
            normalized.startsWith("inspect ") -> inspectDevice(command.substringAfter(' ').trim())
            normalized == "about" -> {
                addTerminalLine("NHSpectrum · HUNTWARE companion · protocol v1")
                addTerminalLine("ESP32-S3 passive Wi-Fi management/AP observation profile")
            }
            else -> sendFirmwareCommand(normalized)
        }
    }

    private fun sendFirmwareCommand(command: String) {
        if (!esp32.isConnected.value) {
            addTerminalLine("ERROR: no USB serial port. Connect the ESP32 first.")
            return
        }
        if (!esp32.isReady.value && command != "hello") {
            addTerminalLine("WAIT: firmware handshake not verified yet. Try status after HUNTWARE LINK READY.")
            return
        }
        val firmwareCommand = when (command) {
            "status" -> "status"
            "self-test", "selftest", "test" -> "self_test"
            "hello" -> "hello"
            "scan start", "monitor start", "scanap", "scan_start_passive" -> "scan_start_passive"
            "scan stop", "monitor stop", "stopscan", "scan_stop" -> "scan_stop"
            else -> null
        }
        if (firmwareCommand == null) {
            addTerminalLine("ERROR: unknown command. Type /help.")
            return
        }
        if (!esp32.send(firmwareCommand)) addTerminalLine("ERROR: USB write failed.")
    }

    private fun printHelp() {
        listOf(
            "HUNTWARE // COMMAND REFERENCE",
            "LOCAL COMMANDS (work without ESP32):",
            "  /help              Show this command list",
            "  clear              Clear terminal output",
            "  devices            List ESP32-observed access points",
            "  select <number>    Select an observed device (1-based)",
            "  inspect <number>   Show details for an observed device",
            "  about              Show bridge and firmware profile",
            "FIRMWARE COMMANDS (requires HUNTWARE LINK READY):",
            "  status             Read firmware and monitor status",
            "  self-test          Run firmware-side health checks",
            "  scan start         Start passive Wi-Fi management observation",
            "  scan stop          Stop passive observation",
            "  hello              Re-run protocol handshake",
            "ALIASES: scanap = scan start · stopscan = scan stop",
            "SAFETY: receive-only diagnostics; no deauth, injection or jamming."
        ).forEach(::addTerminalLine)
    }

    private fun printDevices() {
        val devices = _espDevices.value
        if (devices.isEmpty()) {
            addTerminalLine("No ESP32 AP observations yet. Run 'scan start' and wait for nearby beacons.")
            return
        }
        addTerminalLine("ESP32 OBSERVED ACCESS POINTS · ${devices.size}")
        devices.forEachIndexed { index, device ->
            addTerminalLine("[${index + 1}] ${device.ssid.ifBlank { "<hidden>" }} · ${device.bssid} · ${device.rssiDbm} dBm · CH ${device.channel}")
        }
    }

    private fun selectDevice(token: String) {
        val devices = _espDevices.value
        val selected = token.toIntOrNull()?.let { devices.getOrNull(it - 1) }
            ?: devices.firstOrNull { it.bssid.equals(token, true) || it.id.equals(token, true) }
        if (selected == null) {
            addTerminalLine("No matching observed device. Use 'devices' to list valid indexes/BSSIDs.")
            return
        }
        _selectedEspDevice.value = selected
        addTerminalLine("SELECTED [${devices.indexOf(selected) + 1}] ${selected.ssid.ifBlank { "<hidden>" }} · ${selected.bssid}")
    }

    private fun inspectDevice(token: String) {
        if (token.isNotBlank()) selectDevice(token)
        val device = _selectedEspDevice.value
        if (device == null) {
            addTerminalLine("No device selected. Use 'select <number>' first.")
            return
        }
        addTerminalLine("DEVICE DETAIL · ${device.ssid.ifBlank { "<hidden>" }}")
        addTerminalLine("BSSID: ${device.bssid} · RSSI: ${device.rssiDbm} dBm · CHANNEL: ${device.channel}")
        addTerminalLine("Last firmware uptime: ${device.lastSeenUptimeMs} ms")
        addTerminalLine("Observation only · signal strength is not a calibrated distance estimate.")
    }

    // Compatibility entry points used by the existing FIELD HARDWARE screen.
    fun requestDeviceStatus() = sendCommand("status")
    fun requestPassiveApScan() = sendCommand("scan start")

    private fun handleEspLine(line: String) {
        if (!line.startsWith("{")) {
            addTerminalLine(line)
            return
        }
        val json = runCatching { JSONObject(line) }.getOrNull() ?: run {
            addTerminalLine("Malformed firmware frame ignored")
            return
        }
        when (json.optString("type")) {
            "hello_ack" -> {
                addTerminalLine("Firmware: ${json.optString("firmware", "unknown")} · ${json.optString("chip", "ESP32")}")
                val caps = json.optJSONArray("capabilities")
                if (caps != null) addTerminalLine("Capabilities: " + (0 until caps.length()).joinToString(", ") { caps.optString(it) })
            }
            "self_test" -> {
                val passed = json.optBoolean("ok")
                addTerminalLine("[SELF-CHECK] " + if (passed) "PASS" else "CHECK REQUIRED")
                addTerminalLine("Radio: ${json.optString("wifi", "unknown")} · queue: ${json.optString("queue", "unknown")} · heap: ${json.optInt("free_heap_bytes")} bytes")
            }
            "status" -> addTerminalLine(
                "STATUS · monitor ${json.optString("wifi_scan_state")} · CH ${json.optInt("wifi_channel")} · AP events ${json.optInt("aps_seen")} · deauth observed ${json.optInt("deauth_events")} · disassoc observed ${json.optInt("disassoc_events")} · dropped ${json.optInt("dropped_events")}"
            )
            "wifi_ap_observation" -> {
                val device = EspObservedDevice(
                    id = json.optString("bssid"),
                    ssid = json.optString("ssid"),
                    bssid = json.optString("bssid"),
                    rssiDbm = json.optInt("rssi_dbm"),
                    channel = json.optInt("channel"),
                    lastSeenUptimeMs = json.optLong("uptime_ms")
                )
                _espDevices.update { old ->
                    (listOf(device) + old.filterNot { it.bssid.equals(device.bssid, true) }).take(64)
                }
                addTerminalLine("AP OBSERVED · ${device.ssid.ifBlank { "<hidden>" }} · ${device.bssid} · ${device.rssiDbm} dBm · CH ${device.channel}")
            }
            "wifi_management_event" -> addTerminalLine(
                "802.11 ${json.optString("subtype")} OBSERVED · TX ${json.optString("transmitter")} · BSSID ${json.optString("bssid")} · reason ${json.optInt("reason_code")} · ${json.optInt("rssi_dbm")} dBm"
            )
            "ack" -> addTerminalLine("OK · ${json.optString("command")} · ${if (json.optBoolean("ok")) "accepted" else "failed"}")
            "error" -> addTerminalLine("FIRMWARE ERROR · ${json.optString("error")}")
            "fatal" -> addTerminalLine("FIRMWARE FATAL · ${json.optString("error")}")
            else -> addTerminalLine("FIRMWARE · ${line.take(240)}")
        }
    }

    private fun addTerminalLine(line: String) {
        _terminalLines.update { (it + line).takeLast(240) }
    }

    override fun onCleared() {
        stopScanning()
        esp32.release()
        super.onCleared()
    }
}
