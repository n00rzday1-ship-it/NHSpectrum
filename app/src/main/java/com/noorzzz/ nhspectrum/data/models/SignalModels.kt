package com.noorzzz.nhspectrum.data.models

data class WifiSignal(
    val id: String,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: String,
    val security: String,
    val vendor: String = "Unknown",
    val isEvilTwin: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

data class BleSignal(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int,
    val txPower: Int? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

data class CellSignal(
    val id: String,
    val type: String,          // LTE / NR
    val operator: String,
    val cid: Long,
    val tac: Int,
    val pci: Int,
    val rsrp: Int,
    val rsrq: Int,
    val band: String = "",
    val lastSeen: Long = System.currentTimeMillis()
)

data class DroneSignal(
    val id: String,
    val model: String,
    val serial: String,
    val rssi: Int,
    val altitude: Double? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

data class ScanSnapshot(
    val wifi: List<WifiSignal> = emptyList(),
    val ble: List<BleSignal> = emptyList(),
    val cell: List<CellSignal> = emptyList(),
    val drones: List<DroneSignal> = emptyList()
)

enum class AttackType {
    DEAUTH, BEACON, EVIL_TWIN, PROBE, HANDSHAKE, JAM, STOP
}
