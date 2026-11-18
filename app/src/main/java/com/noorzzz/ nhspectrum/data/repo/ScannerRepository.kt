package com.noorzzz.nhspectrum.data.repo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
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

    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val wifiCache = ConcurrentHashMap<String, WifiSignal>()
    private val bleCache = ConcurrentHashMap<String, BleSignal>()

    // ---------- BLE passive discovery ----------
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (bleScanning) return
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) {
            Log.w("NHSpectrum", "BLE scan permission not granted")
            return
        }
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleCallback)
            bleScanning = true
        } catch (e: Exception) {
            Log.e("NHSpectrum", "BLE scan could not start", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!bleScanning) return
        try { bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(bleCallback) } catch (_: Exception) {}
        bleScanning = false
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BleScanResult) {
            val address = try { result.device.address ?: return } catch (_: SecurityException) { return }
            val name = try { result.scanRecord?.deviceName ?: result.device.name ?: "" } catch (_: SecurityException) { "" }
            bleCache[address] = BleSignal(
                id = address,
                name = name,
                address = address,
                rssi = result.rssi,
                txPower = result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
                lastSeen = System.currentTimeMillis()
            )
            emitSnapshot()
        }

        override fun onScanFailed(errorCode: Int) {
            bleScanning = false
            Log.e("NHSpectrum", "BLE scan failed: $errorCode")
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

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processWifiResults(wifiManager.scanResults)
            }
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
                val channel = freqToChannel(r.frequency)
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
                    security = simplifySecurity(security),
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
                        val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try { identity.bands.filter { it > 0 }.joinToString(",") { "B$it" } } catch (_: Exception) { "" }
                        } else ""
                        list.add(
                            CellSignal(
                                id = if (ci >= 0) ci.toString() else "Unavailable",
                                type = "LTE",
                                operator = identity.operatorAlphaShort?.toString().orEmpty(),
                                cid = ci,
                                tac = tac,
                                pci = pci,
                                rsrp = strength.rsrp,
                                rsrq = strength.rsrq,
                                band = bands,
                                lastSeen = System.currentTimeMillis(),
                                channelNumber = identity.earfcn.takeIf { it >= 0 } ?: Int.MIN_VALUE
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
                            val bands = readIntArrayProperty(identity, "getBands").filter { it > 0 }.joinToString(",") { "n$it" }
                            val rsrp = readNumericProperty(strength, "getSsRsrp", Int.MAX_VALUE.toLong()).toInt()
                            val rsrq = readNumericProperty(strength, "getSsRsrq", Int.MAX_VALUE.toLong()).toInt()
                            list.add(
                                CellSignal(
                                    id = if (nci >= 0) nci.toString() else "Unavailable",
                                    type = "NR 5G",
                                    operator = identity.operatorAlphaShort?.toString().orEmpty(),
                                    cid = nci,
                                    tac = tac,
                                    pci = pci,
                                    rsrp = rsrp,
                                    rsrq = rsrq,
                                    band = bands,
                                    lastSeen = System.currentTimeMillis(),
                                    channelNumber = arfcn
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

    private fun freqToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else -> 0
    }

    private fun simplifySecurity(cap: String): String = when {
        cap.contains("WPA3") -> "WPA3"
        cap.contains("WPA2") -> "WPA2"
        cap.contains("WPA") -> "WPA"
        cap.contains("WEP") -> "WEP"
        else -> "OPEN"
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
        val suspicious = listOf("starbucks", "marriott", "airport", "free", "guest")
        val lower = ssid.lowercase()
        if (suspicious.any { lower.contains(it) } && vendor == "TP-Link") return true
        // add more heuristics later
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
