package com.noorzzz.nhspectrum.hardware

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.Executors

class Esp32Bridge(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    private val ACTION_USB_PERMISSION = "com.noorzzz.nhspectrum.USB_PERMISSION"

    fun connect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Log.w("Esp32Bridge", "No USB serial device found")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            return
        }

        openPort(driver)
    }

    private fun openPort(driver: com.hoho.android.usbserial.driver.UsbSerialDriver) {
        try {
            val connection = usbManager.openDevice(driver.device) ?: return
            port = driver.ports[0]
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    val text = String(data).trim()
                    if (text.isNotEmpty()) {
                        _incoming.tryEmit(text)
                    }
                }
                override fun onRunError(e: Exception) {
                    Log.e("Esp32Bridge", "IO error", e)
                    disconnect()
                }
            })
            Executors.newSingleThreadExecutor().submit(ioManager)

            _isConnected.value = true
            Log.i("Esp32Bridge", "ESP32 connected")
        } catch (e: Exception) {
            Log.e("Esp32Bridge", "Open failed", e)
            disconnect()
        }
    }

    fun send(command: String) {
        try {
            val data = (command.trim() + "\n").toByteArray()
            port?.write(data, 1000)
        } catch (e: IOException) {
            Log.e("Esp32Bridge", "Write failed", e)
        }
    }

    // Ready-made Marauder style commands
    fun deauth(bssid: String? = null) {
        if (bssid != null) send("attack -t deauth -d $bssid")
        else send("attack -t deauth")
    }

    fun beaconSpam() = send("attack -t beacon")
    fun probeSniff() = send("sniffprobe")
    fun handshake() = send("sniffpmkid")
    fun stopAll() = send("stopscan")
    fun scanAp() = send("scanap")
    fun status() = send("status")

    fun disconnect() {
        try {
            ioManager?.stop()
            port?.close()
        } catch (_: Exception) {}
        port = null
        ioManager = null
        _isConnected.value = false
    }
}
