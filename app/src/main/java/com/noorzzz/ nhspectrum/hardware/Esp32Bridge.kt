package com.noorzzz.nhspectrum.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Esp32Bridge(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var ioExecutor: ExecutorService? = null
    private var receiverRegistered = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    private val actionUsbPermission = "com.noorzzz.nhspectrum.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != actionUsbPermission) return
            val device = getUsbDevice(intent) ?: run {
                _isConnecting.value = false
                _incoming.tryEmit("USB permission response did not contain a device")
                return
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (!granted) {
                _isConnecting.value = false
                _incoming.tryEmit("USB permission denied")
                return
            }
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                .firstOrNull { it.device.deviceId == device.deviceId }
            if (driver == null) {
                _isConnecting.value = false
                _incoming.tryEmit("No supported USB serial driver for the selected device")
                return
            }
            openPort(driver)
        }
    }

    init {
        val filter = IntentFilter(actionUsbPermission)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(permissionReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e("Esp32Bridge", "USB permission receiver registration failed", e)
        }
    }

    fun connect() {
        if (_isConnected.value || _isConnecting.value) return
        _isConnecting.value = true
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
        if (driver == null) {
            _isConnecting.value = false
            _incoming.tryEmit("No USB serial device detected. Connect the ESP32 and try again.")
            return
        }

        if (!usbManager.hasPermission(driver.device)) {
            try {
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    driver.device.deviceId,
                    Intent(actionUsbPermission).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                usbManager.requestPermission(driver.device, permissionIntent)
                _incoming.tryEmit("Waiting for Android USB permission…")
            } catch (e: Exception) {
                _isConnecting.value = false
                _incoming.tryEmit("USB permission request failed: ${e.message ?: "unknown error"}")
            }
            return
        }
        openPort(driver)
    }

    private fun getUsbDevice(intent: Intent): UsbDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openPort(driver: UsbSerialDriver) {
        if (_isConnected.value) {
            _isConnecting.value = false
            return
        }
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: throw IOException("Android could not open the USB device")
            val selectedPort = driver.ports.firstOrNull()
                ?: throw IOException("USB serial driver exposes no ports")
            selectedPort.open(connection)
            selectedPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            port = selectedPort
            val manager = SerialInputOutputManager(selectedPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    val text = String(data, Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) _incoming.tryEmit(text)
                }

                override fun onRunError(e: Exception) {
                    Log.e("Esp32Bridge", "Serial I/O error", e)
                    _incoming.tryEmit("USB serial connection lost: ${e.message ?: "I/O error"}")
                    disconnect()
                }
            })
            ioManager = manager
            ioExecutor = Executors.newSingleThreadExecutor().also { it.submit(manager) }
            _isConnected.value = true
            _isConnecting.value = false
            _incoming.tryEmit("ESP32 USB serial connected at 115200 baud")
            Log.i("Esp32Bridge", "USB serial connected")
        } catch (e: Exception) {
            Log.e("Esp32Bridge", "USB serial open failed", e)
            _incoming.tryEmit("USB serial connection failed: ${e.message ?: "unknown error"}")
            disconnect()
        }
    }

    fun send(command: String): Boolean {
        if (!_isConnected.value) return false
        return try {
            val data = (command.trim() + "\n").toByteArray(Charsets.UTF_8)
            port?.write(data, 1000) ?: return false
            true
        } catch (e: IOException) {
            Log.e("Esp32Bridge", "Serial write failed", e)
            _incoming.tryEmit("USB serial write failed: ${e.message ?: "I/O error"}")
            false
        }
    }

    // Fixed passive diagnostics only; no arbitrary active attack commands.
    fun scanAp() = send("scanap")
    fun status() = send("status")

    fun disconnect() {
        _isConnecting.value = false
        try { ioManager?.stop() } catch (_: Exception) {}
        try { port?.close() } catch (_: Exception) {}
        ioManager = null
        port = null
        try { ioExecutor?.shutdownNow() } catch (_: Exception) {}
        ioExecutor = null
        _isConnected.value = false
    }

    fun release() {
        disconnect()
        if (receiverRegistered) {
            try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }
}
