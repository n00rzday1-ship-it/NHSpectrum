package com.noorzzz.nhspectrum.data.wardrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WardriveRouteFilterTest {
    @Test
    fun rejectsSmallStationaryGpsDrift() {
        assertFalse(
            WardriveRouteFilter.accepts(
                distanceMeters = 2.0,
                previousAccuracyMeters = 3f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = 0.1f,
                speedAccuracyMetersPerSecond = 0.4f
            )
        )
    }

    @Test
    fun acceptsWalkingMovementAtNormalGpsAccuracy() {
        assertTrue(
            WardriveRouteFilter.accepts(
                distanceMeters = 4.0,
                previousAccuracyMeters = 3f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = 0.45f,
                speedAccuracyMetersPerSecond = 1.2f
            )
        )
    }

    @Test
    fun acceptsSmallerStepWhenReliableSpeedConfirmsMovement() {
        assertTrue(
            WardriveRouteFilter.accepts(
                distanceMeters = 2.2,
                previousAccuracyMeters = 3f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = 0.7f,
                speedAccuracyMetersPerSecond = 0.8f
            )
        )
    }

    @Test
    fun doesNotUseUnreliableSpeedToAcceptGpsDrift() {
        assertFalse(
            WardriveRouteFilter.accepts(
                distanceMeters = 2.2,
                previousAccuracyMeters = 3f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = 0.7f,
                speedAccuracyMetersPerSecond = 2.0f
            )
        )
    }

    @Test
    fun rejectsInvalidAccuracyAndDistance() {
        assertFalse(
            WardriveRouteFilter.accepts(
                distanceMeters = Double.NaN,
                previousAccuracyMeters = 3f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = null,
                speedAccuracyMetersPerSecond = null
            )
        )
        assertFalse(
            WardriveRouteFilter.accepts(
                distanceMeters = 5.0,
                previousAccuracyMeters = 0f,
                currentAccuracyMeters = 3f,
                speedMetersPerSecond = null,
                speedAccuracyMetersPerSecond = null
            )
        )
    }
}
