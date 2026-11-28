package com.noorzzz.nhspectrum.data.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementRateDetectorTest {
    @Test fun flagsHighRateWithoutClaimingExploitIdentity() {
        val detector = BleAdvertisementRateDetector(windowMs = 10_000, suspiciousRatePerSecond = 5.0, minimumEvents = 20)
        var result: BleRateFinding? = null
        repeat(60) { index ->
            result = detector.add(BleAdvertisementEvent(1_000L + index * 100L, "random-$index", 0x004C, "payload"))
        }
        assertTrue(result!!.suspicious)
        assertTrue(result!!.explanation.contains("High BLE advertisement rate"))
    }

    @Test fun normalRateIsNotFlagged() {
        val detector = BleAdvertisementRateDetector()
        val result = detector.add(BleAdvertisementEvent(10_000, "device", null, "abc"))
        assertFalse(result.suspicious)
    }
}
