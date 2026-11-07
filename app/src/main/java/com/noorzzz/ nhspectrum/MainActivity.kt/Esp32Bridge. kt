package com.noorzzz.nhspectrum

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class Esp32Bridge(private val context: Context) {
    private var port: UsbSerialPort? = null

    fun connectToMarauder() {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(manager.deviceList.values.firstOrNull() ?: return) ?: return
        
        if (!manager.hasPermission(driver.device)) {
            val intent = Intent("com.noorzzz.nhspectrum.USB_PERMISSION")
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            manager.requestPermission(driver.device, pendingIntent)
            return
        }

        val connection = manager.openDevice(driver.device) ?: return
        port = driver.ports[0]
        
        try {
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            println("ESP32 CONNECTED OVER USB")
        } catch (e: Exception) {
            println("USB CONNECTION FAILED: ${e.message}")
        }
    }

    fun sendCommand(cmd: String) {
        try {
            port?.write("$cmd\n".toByteArray(), 1000)
        } catch (e: Exception) {
            println("FAILED TO SEND COMMAND")
        }
    }
}
