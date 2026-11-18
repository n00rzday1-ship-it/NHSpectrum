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
            if ("SUITE_B_192" in cap || "WPA3-EAP" in cap) add("WPA3-Enterprise")
            else if ("EAP" in cap) add("802.1X/EAP")
            if ("OWE" in cap) add("OWE")
            if ("WEP" in cap) add("WEP")
            if ("WAPI" in cap) add("WAPI")
        }.distinct()
        if (schemes.isNotEmpty()) return schemes.joinToString(" + ")

        // Do not label an unfamiliar protected AKM/cipher as an open network.
        val advertisesProtection = "PRIVACY" in cap || "RSN" in cap || "WPA" in cap
        return if (advertisesProtection) "Protected · unknown suite" else "OPEN"
    }

    fun validSignalDbm(value: Int): Int? =
        value.takeIf { it != Int.MIN_VALUE && it != Int.MAX_VALUE && it in -160..0 }
}
