package com.noorzzz.nhspectrum

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.bluetooth.*
import android.bluetooth.le.*
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat

class ScannerService : Service() {
    private lateinit var wifiManager: WifiManager
    private var bleScanner: BluetoothLeScanner? = null

    override fun onCreate() {
        super.onCreate()
        
        val channel = NotificationChannel("nh_scan", "RF Scanner", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(this, "nh_scan")
            .setContentTitle("NHSpectrum Active")
            .setContentText("Scanning RF Hardware...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
            
        startForeground(1, notification)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleScanner = bluetoothManager.adapter.bluetoothLeScanner
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            wifiManager.startScan()
            val realNetworks = wifiManager.scanResults
            for (net in realNetworks) {
                println("REAL WIFI: ${net.SSID} | ${net.BSSID} | ${net.level} dBm")
            }
        } catch (e: Exception) { }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            bleScanner?.startScan(null, settings, bleCallback)
        } catch (e: Exception) { }

        return START_STICKY
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            println("REAL BLE: ${result.device.address} | ${result.rssi} dBm")
        }
    }

    override fun onDestroy() {
        try { bleScanner?.stopScan(bleCallback) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
