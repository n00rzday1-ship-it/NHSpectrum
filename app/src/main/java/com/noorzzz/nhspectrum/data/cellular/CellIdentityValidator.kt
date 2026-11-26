package com.noorzzz.nhspectrum.data.cellular

/**
 * Input is copied from a single Android CellInfo item. timestampNanos must be
 * CellInfo.getTimestampMillis() converted to nanoseconds on the elapsed clock.
 */
data class CellCandidate(
    val technology: String,
    val cellId: Long,
    val tac: Int,
    val pci: Int,
    val signalDbm: Int?,
    val registered: Boolean,
    val timestampNanos: Long
)

data class CellValidation(val accepted: Boolean, val reason: String)

object CellIdentityValidator {
    private const val MAX_AGE_NANOS = 120_000_000_000L
    private const val INVALID_INT = Int.MAX_VALUE.toLong()

    fun validate(candidate: CellCandidate, nowElapsedNanos: Long): CellValidation {
        if (candidate.technology !in setOf("GSM", "WCDMA", "LTE", "NR", "CDMA")) {
            return CellValidation(false, "Unsupported or unknown radio technology")
        }
        if (candidate.timestampNanos <= 0L || candidate.timestampNanos > nowElapsedNanos) {
            return CellValidation(false, "Invalid modem observation timestamp")
        }
        if (nowElapsedNanos - candidate.timestampNanos > MAX_AGE_NANOS) {
            return CellValidation(false, "Stale modem cell information")
        }
        val validCellId = candidate.cellId >= 0L &&
            candidate.cellId != Long.MAX_VALUE && candidate.cellId != INVALID_INT
        if (!validCellId) return CellValidation(false, "Cell identity unavailable")
        if (candidate.tac < 0 || candidate.tac == Int.MAX_VALUE) {
            return CellValidation(false, "Tracking/location area code unavailable")
        }
        if (candidate.pci < 0 || candidate.pci == Int.MAX_VALUE) {
            return CellValidation(false, "Physical cell identity unavailable")
        }
        val signal = candidate.signalDbm
        if (signal == null || signal !in -160..0) {
            return CellValidation(false, "No valid modem signal measurement")
        }
        return CellValidation(true, if (candidate.registered) "Fresh registered cell" else "Fresh modem-reported neighbor cell")
    }
}
