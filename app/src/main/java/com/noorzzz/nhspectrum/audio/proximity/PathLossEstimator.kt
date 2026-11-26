package com.noorzzz.nhspectrum.audio.proximity

enum class ProximityTrend { APPROACHING, MOVING_AWAY, STEADY, UNKNOWN }

data class ProximityEstimate(
    val rawRssiDbm: Int,
    val smoothedRssiDbm: Double,
    val estimatedMeters: Double,
    val trend: ProximityTrend,
    val uncertaintyMeters: Double
)

/**
 * Relative log-distance path-loss estimator. Output is an estimate, not ranging.
 * Calibrate referenceRssiDbm at one metre and pathLossExponent per environment.
 */
class PathLossEstimator(
    private val referenceRssiDbm: Double,
    private val pathLossExponent: Double,
    private val alpha: Double = 0.25,
    private val minimumTrendDeltaDb: Double = 2.0
) {
    init {
        require(referenceRssiDbm in -100.0..-20.0)
        require(pathLossExponent in 1.2..5.0)
        require(alpha in 0.05..1.0)
    }

    private var smoothed: Double? = null
    private var previous: Double? = null

    fun reset() { smoothed = null; previous = null }

    fun update(rssiDbm: Int): ProximityEstimate? {
        if (rssiDbm !in -127..0) return null
        val old = smoothed
        val next = if (old == null) rssiDbm.toDouble() else alpha * rssiDbm + (1.0 - alpha) * old
        previous = old
        smoothed = next
        val distance = Math.pow(10.0, (referenceRssiDbm - next) / (10.0 * pathLossExponent))
            .coerceIn(0.1, 10_000.0)
        val trend = when {
            old == null -> ProximityTrend.UNKNOWN
            next - old >= minimumTrendDeltaDb -> ProximityTrend.APPROACHING
            next - old <= -minimumTrendDeltaDb -> ProximityTrend.MOVING_AWAY
            else -> ProximityTrend.STEADY
        }
        // RSSI-only ranging has large environmental uncertainty. Do not imply precision.
        return ProximityEstimate(
            rawRssiDbm = rssiDbm,
            smoothedRssiDbm = next,
            estimatedMeters = distance,
            trend = trend,
            uncertaintyMeters = distance * 0.75
        )
    }
}
