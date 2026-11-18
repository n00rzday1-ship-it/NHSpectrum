package com.noorzzz.nhspectrum.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioMetadataTest {
    @Test
    fun mapsCommonWifiFrequenciesToChannels() {
        assertEquals(1, RadioMetadata.channelFromFrequency(2412))
        assertEquals(6, RadioMetadata.channelFromFrequency(2437))
        assertEquals(13, RadioMetadata.channelFromFrequency(2472))
        assertEquals(14, RadioMetadata.channelFromFrequency(2484))
        assertEquals(36, RadioMetadata.channelFromFrequency(5180))
        assertEquals(2, RadioMetadata.channelFromFrequency(5935))
        assertEquals(1, RadioMetadata.channelFromFrequency(5955))
        assertEquals(0, RadioMetadata.channelFromFrequency(2400))
    }

    @Test
    fun recognizesWpa2Wpa3TransitionNetworks() {
        assertEquals(
            "WPA3-SAE + WPA2-PSK",
            RadioMetadata.securityFromCapabilities("[WPA2-PSK-CCMP][RSN-SAE-CCMP]")
        )
    }

    @Test
    fun recognizesEnterpriseAndEnhancedOpenSecurity() {
        assertEquals("802.1X/EAP", RadioMetadata.securityFromCapabilities("[WPA2-EAP-CCMP]"))
        assertEquals("OWE", RadioMetadata.securityFromCapabilities("[RSN-OWE-CCMP]"))
    }

    @Test
    fun doesNotInventSecurityForOpenNetworks() {
        assertEquals("OPEN", RadioMetadata.securityFromCapabilities("[ESS]"))
    }

    @Test
    fun normalizesAndroidUnavailableSignalSentinels() {
        assertNull(RadioMetadata.validSignalDbm(Int.MIN_VALUE))
        assertNull(RadioMetadata.validSignalDbm(Int.MAX_VALUE))
        assertNull(RadioMetadata.validSignalDbm(5))
        assertEquals(-75, RadioMetadata.validSignalDbm(-75))
    }
}
