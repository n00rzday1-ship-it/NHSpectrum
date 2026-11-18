package com.noorzzz.nhspectrum.data.models

import java.util.Locale

/**
 * Pure parsing helpers for radio metadata exposed by Android scan APIs.
 * These are descriptive interpretations of scan results, not security audits.
 */
object RadioMetadata {
    fun channelFromFrequency(frequencyMhz: Int): Int = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
        frequencyMhz in 5000..5895 -> (frequencyMhz - 5000) / 5
        frequencyMhz == 5935 -> 2
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> 0
    }

    fun securityFromCapabilities(capabilities: String): String {
        val cap = capabilities.uppercase(Locale.ROOT)
        val schemes = buildList {
            if ("SAE" in cap || "WPA3" in cap) add("WPA3-SAE")
            if ("PSK" in cap && "WPA2" in cap) add("WPA2-PSK")
            else if ("PSK" in cap && "WPA" in cap) add("WPA-PSK")
            if ("EAP" in cap) add("802.1X/EAP")
            if ("OWE" in cap) add("OWE")
            if ("WEP" in cap) add("WEP")
            if ("WAPI" in cap) add("WAPI")
        }.distinct()
        return schemes.takeIf { it.isNotEmpty() }?.joinToString(" + ") ?: "OPEN"
    }

    fun validSignalDbm(value: Int): Int? =
        value.takeIf { it != Int.MIN_VALUE && it != Int.MAX_VALUE && it in -160..0 }
}
