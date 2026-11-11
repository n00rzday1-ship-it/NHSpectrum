package com.noorzzz.nhspectrum.data.repo

import android.annotation.SuppressLint
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

    private val _snapshot = MutableStateFlow(ScanSnapshot())
    val snapshot: StateFlow<ScanSnapshot> = _snapshot.asStateFlow()

    private val wifiCache = ConcurrentHashMap<String, WifiSignal>()
    private val bleCache = ConcurrentHashMap<String, BleSignal>()

    // ---------- WiFi ----------
    @SuppressLint("MissingPermission")
    fun startWifiScan() {
        val success = wifiManager.startScan()
        if (!success) Log.w("NHSpectrum", "WiFi startScan returned false")
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processWifiResults(wifiManager.scanResults)
            }
        }
    }

    fun registerWifiReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiReceiver, filter)
    }

    fun unregisterWifiReceiver() {
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
    }

    private fun processWifiResults(results: List<ScanResult>) {
        val now = System.currentTimeMillis()
        results.forEach { r ->
            val channel = freqToChannel(r.frequency)
            val band = if (r.frequency < 3000) "2.4GHz" else "5GHz"
            val security = r.capabilities ?: ""
            val vendor = guessVendor(r.BSSID)
            val isEvil = detectEvilTwin(r.SSID, r.BSSID, vendor)

            val signal = WifiSignal(
                id = r.BSSID,
                ssid = r.SSID.ifBlank { "<hidden>" },
                bssid = r.BSSID,
                rssi = r.level,
                frequency = r.frequency,
                channel = channel,
                band = band,
                security = simplifySecurity(security),
                vendor = vendor,
                isEvilTwin = isEvil,
                lastSeen = now
            )
            wifiCache[r.BSSID] = signal
        }

        // remove old entries (> 30s)
        val cutoff = now - 30_000
        wifiCache.entries.removeIf { it.value.lastSeen < cutoff }

        emitSnapshot()
    }

    // ---------- Cell ----------
    @SuppressLint("MissingPermission")
    fun scanCell() {
        try {
            val cells = telephonyManager.allCellInfo ?: return
            val list = mutableListOf<CellSignal>()

            cells.forEach { info ->
                when (info) {
                    is CellInfoLte -> {
                        val id = info.cellIdentity
                        val sig = info.cellSignalStrength
                        list.add(
                            CellSignal(
                                id = "${id.ci}",
                                type = "LTE",
                                operator = id.operatorAlphaShort?.toString() ?: "Unknown",
                                cid = id.ci.toLong(),
                                tac = id.tac,
                                pci = id.pci,
                                rsrp = sig.rsrp,
                                rsrq = sig.rsrq,
                                band = "B${id.bandwidth}"
                            )
                        )
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val id = info.cellIdentity
                            val sig = info.cellSignalStrength
                            list.add(
                                CellSignal(
                                    id = "${id.nci}",
                                    type = "NR 5G",
                                    operator = id.operatorAlphaShort?.toString() ?: "Unknown",
                                    cid = id.nci,
                                    tac = id.tac,
                                    pci = id.pci,
                                    rsrp = sig.dbm,
                                    rsrq = 0,
                                    band = "n?"
                                )
                            )
                        }
                    }
                }
            }
            _snapshot.value = _snapshot.value.copy(cell = list)
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

    fun clearCaches() {
        wifiCache.clear()
        bleCache.clear()
        emitSnapshot()
    }
}
