package com.noorzzz.nhspectrum.data.repo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.ParcelUuid
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BleScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.noorzzz.nhspectrum.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class ScannerRepository(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    @Volatile private var bleScanning = false
    private val _bleStatus = MutableStateFlow("BLE scanner not started")
    val bleStatus: StateFlow<String> = _bleStatus.asStateFlow()

    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val wifiCache = ConcurrentHashMap<String, WifiSignal>()
    private val bleCache = ConcurrentHashMap<String, BleSignal>()

    // ---------- BLE passive discovery ----------
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (bleScanning) return

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            _bleStatus.value = "BLE is not supported by this device"
            return
        }
        val scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!scanGranted) { _bleStatus.value = "Bluetooth scan permission required"; return }
        val adapter = bluetoothManager.adapter
        if (adapter == null) { _bleStatus.value = "Bluetooth adapter unavailable"; return }
        val enabled = try { adapter.isEnabled } catch (_: SecurityException) { null }
        if (enabled == false) { _bleStatus.value = "Turn Bluetooth on to scan nearby BLE advertisements"; return }
        val scanner = try { adapter.bluetoothLeScanner } catch (_: SecurityException) { null }
        if (scanner == null) { _bleStatus.value = "BLE scanner unavailable"; return }
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0L).build()
            scanner.startScan(null, settings, bleCallback)
            bleScanning = true
            _bleStatus.value = "Scanning for nearby BLE advertisements"
        } catch (e: SecurityException) {
            _bleStatus.value = "Bluetooth permission denied by Android"
            Log.e("NHSpectrum", "BLE permission rejected", e)
        } catch (e: Exception) {
            _bleStatus.value = "BLE scan could not start: " + (e.message ?: "unknown error")
            Log.e("NHSpectrum", "BLE scan could not start", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!bleScanning) return
        try { bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(bleCallback) } catch (_: Exception) {}
        bleScanning = false
        _bleStatus.value = "BLE scan paused"
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BleScanResult) {
            try {
                val record = result.scanRecord
                val exposedAddress = try { result.device.address } catch (_: SecurityException) { null }
                val address = exposedAddress ?: "BLE-ANON-${System.identityHashCode(result.device).toString(16)}"
                val displayAddress = exposedAddress ?: "Not exposed by Android"
                val manufacturer = buildMap {
                    val data = record?.manufacturerSpecificData
                    if (data != null) for (i in 0 until data.size()) {
                        val id = data.keyAt(i)
                        val bytes = data.valueAt(i) ?: continue
                        put(id, bytes.joinToString("") { "%02X".format(it) })
                    }
                }
                val services = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
                val now = System.currentTimeMillis()
                bleCache[address] = BleSignal(
                    id = address, name = record?.deviceName.orEmpty(), address = displayAddress, rssi = result.rssi,
                    txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }, lastSeen = now,
                    manufacturerData = manufacturer, serviceUuids = services,
                    connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null
                )

                // OpenDroneID BLE advertisements: service UUID 0xFFFA, app code 0x0D.
                val ridUuid = ParcelUuid.fromString("0000fffa-0000-1000-8000-00805f9b34fb")
                val rid = record?.getServiceData(ridUuid)
                if (rid != null && rid.size >= 27 && (rid[0].toInt() and 0xFF) == 0x0D) {
                    val message = rid.copyOfRange(2, 27)
                    // ASTM/OpenDroneID message type is the high nibble of the message header.
                    val messageType = (message[0].toInt() and 0xF0) ushr 4
                    val droneId = "BLE-RID:" + address
                    val previous = _snapshot.value.drones.firstOrNull { it.id == droneId }
                    val payload = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN)
                    var serial = previous?.serial.orEmpty()
                    var latitude = previous?.latitude
                    var longitude = previous?.longitude
                    var altitude = previous?.altitude
                    var operatorLatitude = previous?.operatorLatitude
                    var operatorLongitude = previous?.operatorLongitude
                    val model = when (messageType) {
                        0 -> {
                            serial = message.copyOfRange(2, 22).toString(Charsets.US_ASCII)
                                .trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                            "OpenDroneID · Basic ID"
                        }
                        1 -> {
                            val lat = payload.getInt(5) * 1e-7
                            val lon = payload.getInt(9) * 1e-7
                            if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
                                latitude = lat
                                longitude = lon
                            }
                            val rawAltitude = payload.getShort(15).toInt() and 0xFFFF
                            if (rawAltitude != 0xFFFF) altitude = rawAltitude / 2.0 - 1000.0
                            "OpenDroneID · Location"
                        }
                        4 -> {
                            val lat = payload.getInt(2) * 1e-7
                            val lon = payload.getInt(6) * 1e-7
                            if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
                                operatorLatitude = lat
                                operatorLongitude = lon
                            }
                            "OpenDroneID · System"
                        }
                        5 -> "OpenDroneID · Operator ID"
                        else -> "OpenDroneID · Remote ID"
                    }
                    val drone = DroneSignal(
                        id = droneId, model = model, serial = serial, rssi = result.rssi,
                        altitude = altitude, lastSeen = now, latitude = latitude, longitude = longitude,
                        operatorLatitude = operatorLatitude, operatorLongitude = operatorLongitude
                    )
                    _snapshot.value = _snapshot.value.copy(drones = _snapshot.value.drones.filterNot { it.id == droneId } + drone)
                }
                bleCache.entries.removeIf { it.value.lastSeen < now - 180_000L }
                emitSnapshot()
                _bleStatus.value = "BLE live · " + bleCache.size + " devices observed"
            } catch (e: SecurityException) {
                _bleStatus.value = "BLE data access blocked · grant Nearby devices permission"
                Log.w("NHSpectrum", "Could not read BLE scan result", e)
            } catch (e: Exception) {
                Log.e("NHSpectrum", "Could not parse BLE scan result", e)
            }
        }

        override fun onBatchScanResults(results: MutableList<BleScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(errorCode: Int) {
            bleScanning = false
            _bleStatus.value = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "BLE scan already active"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "BLE scanner registration failed"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning is unsupported"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Android BLE scanner internal error"
                ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "BLE scanner out of hardware resources"
                else -> "BLE scan failed · error " + errorCode
            }
            Log.e("NHSpectrum", _bleStatus.value)
        }
    }
    // ---------- WiFi ----------
    @SuppressLint("MissingPermission")
    fun startWifiScan() {
        val fineLocationGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineLocationGranted) {
            Log.w("NHSpectrum", "Wi-Fi scan skipped: precise location permission is not granted")
            return
        }
        try {
            val success = wifiManager.startScan()
            if (!success) Log.w("NHSpectrum", "Wi-Fi scan was throttled or rejected by Android; cached observations are retained")
        } catch (e: SecurityException) {
            Log.w("NHSpectrum", "Wi-Fi scan permission rejected", e)
        } catch (e: Exception) {
            Log.e("NHSpectrum", "Wi-Fi scan failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private val wifiReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) readWifiScanResults()
        }
    }

    @SuppressLint("MissingPermission")
    private fun readWifiScanResults() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("NHSpectrum", "Wi-Fi scan results withheld: precise location permission is not granted")
            return
        }
        try {
            processWifiResults(wifiManager.scanResults)
        } catch (e: SecurityException) {
            Log.w("NHSpectrum", "Android denied access to Wi-Fi scan results", e)
        }
    }

    private var wifiReceiverRegistered = false

    fun registerWifiReceiver() {
        if (wifiReceiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") context.registerReceiver(wifiReceiver, filter)
            wifiReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("NHSpectrum", "Wi-Fi receiver registration failed", e)
        }
    }

    fun unregisterWifiReceiver() {
        if (!wifiReceiverRegistered) return
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        finally { wifiReceiverRegistered = false }
    }

    private fun processWifiResults(results: List<ScanResult>) {
        try {
            val now = System.currentTimeMillis()
            results.forEach { r ->
                val bssid = r.BSSID ?: return@forEach
                val channel = RadioMetadata.channelFromFrequency(r.frequency)
                val band = when {
                    r.frequency in 2400..2500 -> "2.4 GHz"
                    r.frequency in 4900..5924 -> "5 GHz"
                    r.frequency >= 5925 -> "6 GHz"
                    else -> "Other"
                }
                val security = r.capabilities ?: ""
                val vendor = guessVendor(bssid)
                val isEvil = detectEvilTwin(r.SSID.orEmpty(), bssid, vendor)

                val signal = WifiSignal(
                    id = bssid,
                    ssid = r.SSID?.ifBlank { "<hidden>" } ?: "<hidden>",
                    bssid = bssid,
                    rssi = r.level,
                    frequency = r.frequency,
                    channel = channel,
                    band = band,
                    security = RadioMetadata.securityFromCapabilities(security),
                    vendor = vendor,
                    isEvilTwin = isEvil,
                    lastSeen = now,
                    channelWidthMhz = when (r.channelWidth) {
                        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                        else -> null
                    }
                )
                wifiCache[bssid] = signal
            }

            // Retain only recently heard access points; the age is visible in lastSeen.
            val cutoff = now - 120_000
            wifiCache.entries.removeIf { it.value.lastSeen < cutoff }
            emitSnapshot()
        } catch (e: SecurityException) {
            Log.w("NHSpectrum", "Android denied access to Wi-Fi scan results", e)
        } catch (e: Exception) {
            Log.e("NHSpectrum", "Could not parse Wi-Fi scan results", e)
        }
    }

    // ---------- Cellular (read-only serving/neighbor cell observations) ----------
    @SuppressLint("MissingPermission")
    fun scanCell() {
        val locationGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneStateGranted = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (!locationGranted || !phoneStateGranted) {
            Log.w("NHSpectrum", "Cell scan skipped: location and phone-state permissions are required")
            return
        }
        try {
            val cells = telephonyManager.allCellInfo.orEmpty()
            val list = mutableListOf<CellSignal>()
            cells.forEach { info ->
                when (info) {
                    is CellInfoLte -> {
                        val identity = info.cellIdentity
                        val strength = info.cellSignalStrength
                        val ci = identity.ci.takeIf { it != Int.MAX_VALUE && it >= 0 }?.toLong() ?: -1L
                        val tac = identity.tac.takeIf { it != Int.MAX_VALUE && it >= 0 } ?: -1
                        val pci = identity.pci.takeIf { it != Int.MAX_VALUE && it >= 0 } ?: -1
                        val earfcn = identity.earfcn.takeIf { it >= 0 } ?: Int.MIN_VALUE
                        val operator = identity.operatorAlphaShort?.toString().orEmpty()
                            .ifBlank { identity.operatorAlphaLong?.toString().orEmpty() }
                        val fallbackId = "LTE:$tac:$pci:$earfcn:$operator:${info.isRegistered}"
                        val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try { identity.bands.filter { it > 0 }.joinToString(",") { "B$it" } } catch (_: Exception) { "" }
                        } else ""
                        list.add(
                            CellSignal(
                                id = if (ci >= 0) "LTE:$ci" else fallbackId,
                                type = "LTE",
                                operator = operator,
                                cid = ci,
                                tac = tac,
                                pci = pci,
                                rsrp = strength.rsrp.takeIf { it != Int.MAX_VALUE } ?: Int.MIN_VALUE,
                                rsrq = strength.rsrq.takeIf { it != Int.MAX_VALUE } ?: Int.MIN_VALUE,
                                band = bands,
                                lastSeen = System.currentTimeMillis(),
                                channelNumber = earfcn,
                                registered = info.isRegistered
                            )
                        )
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val identity = info.cellIdentity
                            val strength = info.cellSignalStrength
                            val nci = readNumericProperty(identity, "getNci", -1L).takeIf { it >= 0 && it != Long.MAX_VALUE } ?: -1L
                            val tac = readNumericProperty(identity, "getTac", -1L).toInt().takeIf { it >= 0 && it != Int.MAX_VALUE } ?: -1
                            val pci = readNumericProperty(identity, "getPci", -1L).toInt().takeIf { it >= 0 && it != Int.MAX_VALUE } ?: -1
                            val arfcn = readNumericProperty(identity, "getNrarfcn", Int.MIN_VALUE.toLong()).toInt()
                            val operator = identity.operatorAlphaShort?.toString().orEmpty()
                                .ifBlank { identity.operatorAlphaLong?.toString().orEmpty() }
                            val fallbackId = "NR:$tac:$pci:$arfcn:$operator:${info.isRegistered}"
                            val bands = readIntArrayProperty(identity, "getBands").filter { it > 0 }.joinToString(",") { "n$it" }
                            val rsrp = readNumericProperty(strength, "getSsRsrp", Int.MAX_VALUE.toLong()).toInt()
                            val rsrq = readNumericProperty(strength, "getSsRsrq", Int.MAX_VALUE.toLong()).toInt()
                            list.add(
                                CellSignal(
                                    id = if (nci >= 0) "NR:$nci" else fallbackId,
                                    type = "NR 5G",
                                    operator = operator,
                                    cid = nci,
                                    tac = tac,
                                    pci = pci,
                                    rsrp = rsrp.takeIf { it != Int.MAX_VALUE } ?: Int.MIN_VALUE,
                                    rsrq = rsrq.takeIf { it != Int.MAX_VALUE } ?: Int.MIN_VALUE,
                                    band = bands,
                                    lastSeen = System.currentTimeMillis(),
                                    channelNumber = arfcn,
                                    registered = info.isRegistered
                                )
                            )
                        }
                    }
                }
            }
            _snapshot.value = _snapshot.value.copy(cell = list)
        } catch (e: SecurityException) {
            Log.w("NHSpectrum", "Android denied access to cellular information", e)
        } catch (e: Exception) {
            Log.e("NHSpectrum", "Cell scan failed", e)
        }
    }

    // ---------- helpers ----------
    private fun emitSnapshot() {
        _snapshot.value = _snapshot.value.copy(
            wifi = wifiCache.values.sortedByDescending { it.rssi },
            ble = bleCache.values.sortedByDescending { it.rssi }
        )
    }

    private fun guessVendor(bssid: String): String {
        val oui = bssid.take(8).uppercase()
        return when {
            oui.startsWith("00:1A:2B") || oui.startsWith("00:17:F2") -> "Cisco"
            oui.startsWith("00:15:6D") || oui.startsWith("24:A4:3C") -> "Ubiquiti"
            oui.startsWith("50:C7:BF") || oui.startsWith("C0:25:E9") -> "TP-Link"
            oui.startsWith("00:1E:2A") || oui.startsWith("A0:63:91") -> "Netgear"
            oui.startsWith("00:1D:0F") || oui.startsWith("04:D4:C4") -> "Asus"
            else -> "Unknown"
        }
    }

    private fun detectEvilTwin(ssid: String, bssid: String, vendor: String): Boolean {
        // An SSID keyword or AP vendor alone is not evidence of an evil twin.
        return false
    }

    // Reflection keeps this optional NR metadata compatible across vendor/API SDK stubs.
    private fun readNumericProperty(target: Any, getter: String, fallback: Long): Long {
        return try {
            (target.javaClass.getMethod(getter).invoke(target) as? Number)?.toLong() ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun readIntArrayProperty(target: Any, getter: String): IntArray {
        return try {
            target.javaClass.getMethod(getter).invoke(target) as? IntArray ?: intArrayOf()
        } catch (_: Exception) {
            intArrayOf()
        }
    }

    fun clearCaches() {
        wifiCache.clear()
        bleCache.clear()
        emitSnapshot()
    }
}
