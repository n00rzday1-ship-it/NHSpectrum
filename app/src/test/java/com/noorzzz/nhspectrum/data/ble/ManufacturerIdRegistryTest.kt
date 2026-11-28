package com.noorzzz.nhspectrum.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManufacturerIdRegistryTest {
    @Test fun resolvesKnownCompanyIds() {
        assertEquals("Apple", ManufacturerIdRegistry.resolve(0x004C))
        assertEquals("Espressif Systems", ManufacturerIdRegistry.resolve(0x02E5))
    }

    @Test fun validatesHexPayload() {
        assertEquals(listOf(0x01, 0xFF), ManufacturerIdRegistry.parseManufacturerHex("01ff")?.map { it.toInt() and 0xFF })
        assertNull(ManufacturerIdRegistry.parseManufacturerHex("0XZ1"))
        assertNull(ManufacturerIdRegistry.parseManufacturerHex("ABC"))
    }
}
