package com.noorzzz.nhspectrum

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class Esp32Bridge(private val context: Context) {
    private var port: UsbSerialPort? = null
    private val ACTION_USB_PERMISSION = "com.noorzzz.nhspectrum.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { connectToDevice(it) }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun connectToMarauder() {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(manager.deviceList.values.firstOrNull() ?: return) ?: return
        val device = driver.device

        if (manager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            val intent = Intent(ACTION_USB_PERMISSION)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getBroadcast(context, 0, intent, 0)
            }
            manager.requestPermission(device, pendingIntent)
        }
    }

    private fun connectToDevice(device: android.hardware.usb.UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return
        val connection = manager.openDevice(device) ?: return
        
        port = driver.ports[0]
        try {
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: Exception) {}
    }

    fun sendCommand(cmd: String) {
        try {
            port?.write("$cmd\n".toByteArray(), 1000)
        } catch (e: Exception) {}
    }
}
