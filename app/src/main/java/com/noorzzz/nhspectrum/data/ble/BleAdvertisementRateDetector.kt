package com.noorzzz.nhspectrum.data.ble

import java.util.ArrayDeque

data class BleAdvertisementEvent(
    val observedAtMs: Long,
    val addressToken: String,
    val manufacturerId: Int?,
    val payloadFingerprint: String
)

data class BleRateFinding(
    val eventRatePerSecond: Double,
    val distinctAddressCount: Int,
    val repeatedPayloadCount: Int,
    val suspicious: Boolean,
    val explanation: String
)

/**
 * Generic advertisement-rate anomaly detector. It does not identify a specific
 * exploit family from an address or manufacturer ID alone.
 */
class BleAdvertisementRateDetector(
    private val windowMs: Long = 10_000L,
    private val suspiciousRatePerSecond: Double = 15.0,
    private val minimumEvents: Int = 30
) {
    private val events = ArrayDeque<BleAdvertisementEvent>()

    init {
        require(windowMs in 1_000L..120_000L)
        require(suspiciousRatePerSecond > 0.0)
        require(minimumEvents >= 2)
    }

    @Synchronized
    fun add(event: BleAdvertisementEvent): BleRateFinding {
        if (event.observedAtMs <= 0L || event.addressToken.isBlank()) return currentFinding(event.observedAtMs)
        events.addLast(event)
        return currentFinding(event.observedAtMs)
    }

    @Synchronized
    fun reset() = events.clear()

    private fun currentFinding(nowMs: Long): BleRateFinding {
        val cutoff = nowMs - windowMs
        while (events.isNotEmpty() && events.first.observedAtMs < cutoff) events.removeFirst()
        val count = events.size
        val rate = count * 1000.0 / windowMs.toDouble()
        val addresses = events.map { it.addressToken }.toSet().size
        val repeatedPayloads = events.groupingBy { it.payloadFingerprint }.eachCount().values.count { it > 3 }
        val suspicious = count >= minimumEvents && rate >= suspiciousRatePerSecond
        val explanation = when {
            suspicious -> "High BLE advertisement rate observed; inspect payloads and environment"
            count == 0 -> "No recent BLE advertisements"
            else -> "Advertisement rate within configured threshold"
        }
        return BleRateFinding(rate, addresses, repeatedPayloads, suspicious, explanation)
    }
}
