package com.noorzzz.nhspectrum.app

import com.noorzzz.nhspectrum.core.model.CapabilityState

enum class RadioCapability {
    WIFI_SCAN_ANDROID,
    WIFI_RAW_MONITOR,
    WIFI_DEAUTH_OBSERVE,
    WIFI_5_GHZ,
    WIFI_6_GHZ,
    BLE_ADVERTISEMENTS_ANDROID,
    BLE_ADVERTISEMENTS_ESP32,
    CELL_INFO_ANDROID,
    GNSS_SATELLITE_STATUS,
    GNSS_RAW_MEASUREMENTS,
    DRONE_REMOTE_ID_BLE,
    USB_SERIAL,
    CALIBRATED_RF_SPECTRUM
}

data class CapabilityReport(
    val state: CapabilityState,
    val source: String,
    val detail: String
)

/**
 * Runtime capability facts. Populate this only from API/hardware probes and
 * a successful companion handshake; never infer support from a marketing name.
 */
class CapabilityRegistry {
    private val reports = mutableMapOf<RadioCapability, CapabilityReport>()

    @Synchronized
    fun update(capability: RadioCapability, report: CapabilityReport) {
        reports[capability] = report
    }

    @Synchronized
    fun get(capability: RadioCapability): CapabilityReport =
        reports[capability] ?: CapabilityReport(
            CapabilityState.UNSUPPORTED, "not-probed", "Capability has not been verified"
        )

    @Synchronized
    fun snapshot(): Map<RadioCapability, CapabilityReport> = reports.toMap()
}
