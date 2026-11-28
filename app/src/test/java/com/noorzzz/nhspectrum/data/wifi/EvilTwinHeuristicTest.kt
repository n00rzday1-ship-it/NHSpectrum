package com.noorzzz.nhspectrum.data.wifi

import org.junit.Assert.assertTrue
import org.junit.Test

class EvilTwinHeuristicTest {
    @Test fun flagsSameSsidWithSecurityMismatchAsHeuristic() {
        val detector = EvilTwinHeuristic()
        detector.assess(AccessPointFingerprint("Office", "00:11:22:33:44:55", "WPA2-Enterprise", 36, -60, "Vendor A", 1_000_000))
        val result = detector.assess(AccessPointFingerprint("Office", "00:11:22:33:44:66", "OPEN", 36, -55, "Vendor B", 1_001_000))
        assertTrue(result.suspicious)
        assertTrue(result.reasons.any { it.contains("different advertised security") })
        assertTrue(result.confidenceLabel == "Heuristic")
    }
}
