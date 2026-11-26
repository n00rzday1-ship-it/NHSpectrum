package com.noorzzz.nhspectrum.data.cellular

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellIdentityValidatorTest {
    private val now = 1_000_000_000_000L
    private fun candidate(
        cellId: Long = 12345L,
        tac: Int = 100,
        pci: Int = 42,
        signal: Int? = -95,
        ageNanos: Long = 5_000_000_000L
    ) = CellCandidate("LTE", cellId, tac, pci, signal, false, now - ageNanos)

    @Test fun acceptsFreshModemReportedNeighbor() {
        assertTrue(CellIdentityValidator.validate(candidate(), now).accepted)
    }

    @Test fun rejectsUnavailableIdentityAndSignal() {
        assertFalse(CellIdentityValidator.validate(candidate(cellId = Long.MAX_VALUE), now).accepted)
        assertFalse(CellIdentityValidator.validate(candidate(tac = Int.MAX_VALUE), now).accepted)
        assertFalse(CellIdentityValidator.validate(candidate(signal = null), now).accepted)
    }

    @Test fun rejectsStaleCellInfo() {
        assertFalse(CellIdentityValidator.validate(candidate(ageNanos = 121_000_000_000L), now).accepted)
    }
}
