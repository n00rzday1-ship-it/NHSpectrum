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
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** USB CDC/UART bridge. Physical port-open and firmware handshake are separate states. */
class Esp32Bridge(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var ioExecutor: ExecutorService? = null
    private var receiverRegistered = false
    private val receiveLine = ByteArrayOutputStream()
    private val receiveLock = Any()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 128)
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
            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                _isConnecting.value = false
                _incoming.tryEmit("USB permission denied")
                return
            }
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                .firstOrNull { it.device.deviceId == device.deviceId }
            if (driver == null) {
                _isConnecting.value = false
                _incoming.tryEmit("No supported USB serial driver for selected device")
                return
            }
            openPort(driver)
        }
    }

    init {
        try {
            ContextCompat.registerReceiver(
                context, permissionReceiver, IntentFilter(actionUsbPermission),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "USB permission receiver registration failed", e)
        }
    }

    fun connect() {
        if (_isConnected.value || _isConnecting.value) return
        _isConnecting.value = true
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
        if (driver == null) {
            _isConnecting.value = false
            _incoming.tryEmit("No supported USB serial device detected. Check OTG and the USB cable.")
            return
        }
        if (!usbManager.hasPermission(driver.device)) {
            try {
                val permissionIntent = PendingIntent.getBroadcast(
                    context, driver.device.deviceId,
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

    private fun getUsbDevice(intent: Intent): UsbDevice? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    } catch (_: Exception) { null }

    private fun openPort(driver: UsbSerialDriver) {
        if (_isConnected.value) {
            _isConnecting.value = false
            return
        }
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: throw IOException("Android could not open USB device")
            val selectedPort = driver.ports.firstOrNull()
                ?: throw IOException("USB serial driver exposes no ports")
            selectedPort.open(connection)
            selectedPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            synchronized(receiveLock) { receiveLine.reset() }
            port = selectedPort
            val manager = SerialInputOutputManager(selectedPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) = consumeBytes(data)
                override fun onRunError(e: Exception) {
                    Log.e(TAG, "Serial I/O error", e)
                    _incoming.tryEmit("USB serial connection lost: ${e.message ?: "I/O error"}")
                    disconnect()
                }
            })
            ioManager = manager
            ioExecutor = Executors.newSingleThreadExecutor().also { it.submit(manager) }
            _isConnected.value = true
            _isConnecting.value = false
            _isReady.value = false
            _incoming.tryEmit("USB serial port open · 115200 8-N-1")
            _incoming.tryEmit("Verifying NHSpectrum protocol v1…")
            // The ESP may have booted before Android opened the port. Request a fresh hello.
            send("hello")
            Log.i(TAG, "USB serial opened for ${driver.device.deviceName}")
        } catch (e: Exception) {
            Log.e(TAG, "USB serial open failed", e)
            _incoming.tryEmit("USB serial open failed: ${e.message ?: "unknown error"}")
            disconnect()
        }
    }

    /** USB callbacks may split UTF-8 and JSON at any byte. Decode only complete lines. */
    private fun consumeBytes(data: ByteArray) {
        synchronized(receiveLock) {
            data.forEach { byte ->
                when (byte) {
                    10.toByte() -> {
                        val raw = receiveLine.toByteArray()
                        receiveLine.reset()
                        val line = raw.toString(Charsets.UTF_8).trim()
                        if (line.isNotEmpty()) handleLine(line)
                    }
                    13.toByte() -> Unit
                    else -> {
                        if (receiveLine.size() < MAX_LINE_BYTES) receiveLine.write(byte.toInt())
                        else {
                            receiveLine.reset()
                            _incoming.tryEmit("Serial frame discarded · line exceeded ${MAX_LINE_BYTES} bytes")
                        }
                    }
                }
            }
        }
    }

    private fun handleLine(line: String) {
        // Ignore binary boot noise; preserve readable boot diagnostics and JSON protocol lines.
        val printable = line.filter { it == '\t' || it.code in 0x20..0x7E }
        if (printable.isBlank()) return
        if (printable.startsWith("{")) {
            try {
                val type = org.json.JSONObject(printable).optString("type")
                if (type == "hello_ack" && org.json.JSONObject(printable).optInt("v") == 1) {
                    _isReady.value = true
                }
            } catch (_: Exception) {
                _incoming.tryEmit("Malformed firmware JSON frame discarded")
                return
            }
        } else if (printable.length < line.length / 2) {
            return
        }
        _incoming.tryEmit(printable)
    }

    fun send(command: String): Boolean {
        if (!_isConnected.value) return false
        return try {
            val bytes = (command.trim() + "\n").toByteArray(Charsets.UTF_8)
            port?.write(bytes, 1500) ?: return false
            true
        } catch (e: IOException) {
            Log.e(TAG, "Serial write failed", e)
            _incoming.tryEmit("USB serial write failed: ${e.message ?: "I/O error"}")
            false
        }
    }

    fun disconnect() {
        _isConnecting.value = false
        _isReady.value = false
        try { ioManager?.stop() } catch (_: Exception) {}
        try { port?.close() } catch (_: Exception) {}
        ioManager = null
        port = null
        try { ioExecutor?.shutdownNow() } catch (_: Exception) {}
        ioExecutor = null
        _isConnected.value = false
        synchronized(receiveLock) { receiveLine.reset() }
    }

    fun release() {
        disconnect()
        if (receiverRegistered) {
            try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    companion object {
        private const val TAG = "Esp32Bridge"
        private const val MAX_LINE_BYTES = 4096
    }
}
