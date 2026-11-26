package com.noorzzz.nhspectrum.core.model

enum class ObservationSource { ANDROID_API, ESP32_COMPANION, USB_WIFI_ADAPTER, IMPORTED }

enum class CapabilityState { AVAILABLE, LIMITED, UNSUPPORTED, PERMISSION_REQUIRED, DISCONNECTED, STALE, ERROR }

data class ObservationFreshness(
    val observedAtEpochMs: Long,
    val receivedAtElapsedNanos: Long? = null,
    val isCached: Boolean = false
) {
    fun ageMs(nowEpochMs: Long): Long = (nowEpochMs - observedAtEpochMs).coerceAtLeast(0L)
    fun isStale(nowEpochMs: Long, maxAgeMs: Long): Boolean =
        ageMs(nowEpochMs) > maxAgeMs
}
