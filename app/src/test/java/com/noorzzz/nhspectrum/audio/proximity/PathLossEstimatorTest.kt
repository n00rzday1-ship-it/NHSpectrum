package com.noorzzz.nhspectrum.audio.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathLossEstimatorTest {
    @Test fun strongerSignalMovesEstimatedDistanceCloser() {
        val estimator = PathLossEstimator(referenceRssiDbm = -40.0, pathLossExponent = 2.0, alpha = 1.0)
        val far = estimator.update(-70)!!
        val near = estimator.update(-50)!!
        assertTrue(near.estimatedMeters < far.estimatedMeters)
        assertEquals(ProximityTrend.APPROACHING, near.trend)
        assertTrue(near.uncertaintyMeters > 0.0)
    }

    @Test fun rejectsInvalidRssi() {
        val estimator = PathLossEstimator(-40.0, 2.0)
        assertNotNull(estimator.update(-65))
        assertEquals(null, estimator.update(5))
    }
}
