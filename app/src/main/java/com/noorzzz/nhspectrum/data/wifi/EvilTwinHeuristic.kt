package com.noorzzz.nhspectrum.data.wifi

data class AccessPointFingerprint(
    val ssid: String,
    val bssid: String,
    val security: String,
    val channel: Int,
    val rssiDbm: Int,
    val vendor: String,
    val observedAtMs: Long
)

data class WifiRiskIndicator(
    val suspicious: Boolean,
    val score: Int,
    val reasons: List<String>,
    val confidenceLabel: String = "Heuristic"
)

/**
 * Explainable local comparison only. A matching SSID or vendor mismatch alone
 * is not proof of an Evil Twin. Keep history bounded and age observations out.
 */
class EvilTwinHeuristic(
    private val historyWindowMs: Long = 30 * 60 * 1000L,
    private val newBssidWindowMs: Long = 5 * 60 * 1000L
) {
    private val history = LinkedHashMap<String, AccessPointFingerprint>()

    @Synchronized
    fun assess(current: AccessPointFingerprint, nowMs: Long = current.observedAtMs): WifiRiskIndicator {
        history.entries.removeIf { nowMs - it.value.observedAtMs > historyWindowMs }
        if (current.ssid.isBlank() || current.ssid == "<hidden>") {
            history[current.bssid] = current
            return WifiRiskIndicator(false, 0, emptyList())
        }

        val peers = history.values.filter {
            it.ssid == current.ssid && it.bssid != current.bssid &&
                nowMs - it.observedAtMs <= historyWindowMs
        }
        val sameBssid = history[current.bssid]
        val reasons = mutableListOf<String>()
        var score = 0

        if (peers.any { it.security != current.security }) {
            reasons += "Same SSID observed with different advertised security"
            score += 45
        }
        if (peers.any { it.channel != current.channel }) {
            reasons += "Same SSID observed on another channel"
            score += 10
        }
        if (peers.any { it.vendor != "Unknown" && current.vendor != "Unknown" && it.vendor != current.vendor }) {
            reasons += "Same SSID has a different observed BSSID vendor"
            score += 15
        }
        if (sameBssid != null && kotlin.math.abs(current.rssiDbm - sameBssid.rssiDbm) >= 25) {
            reasons += "Large RSSI change from the previous observation"
            score += 10
        }
        if (peers.any { nowMs - it.observedAtMs <= newBssidWindowMs }) {
            reasons += "Additional BSSID recently appeared for this SSID"
            score += 15
        }

        history[current.bssid] = current
        while (history.size > 512) history.remove(history.keys.first())
        val boundedScore = score.coerceAtMost(100)
        return WifiRiskIndicator(
            suspicious = boundedScore >= 45,
            score = boundedScore,
            reasons = reasons
        )
    }

    @Synchronized
    fun clear() = history.clear()
}
