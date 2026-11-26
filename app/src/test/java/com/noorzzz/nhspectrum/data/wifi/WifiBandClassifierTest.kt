package com.noorzzz.nhspectrum.data.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiBandClassifierTest {
    @Test fun classifiesCommonBands() {
        assertEquals(WifiBand.GHZ_2_4, WifiBandClassifier.classify(2412).band)
        assertEquals(1, WifiBandClassifier.classify(2412).channel)
        assertEquals(WifiBand.GHZ_5, WifiBandClassifier.classify(5180).band)
        assertEquals(36, WifiBandClassifier.classify(5180).channel)
        assertEquals(WifiBand.GHZ_6, WifiBandClassifier.classify(5955).band)
        assertEquals(1, WifiBandClassifier.classify(5955).channel)
        assertEquals(14, WifiBandClassifier.classify(2484).channel)
        assertEquals(2, WifiBandBandClassifierCompat.channel5935())
    }

    @Test fun rejectsUnsupportedChannelWidths() {
        assertTrue(WifiBandClassifier.supportedChannelWidth(320))
        assertTrue(!WifiBandClassifier.supportedChannelWidth(40 + 10))
    }
}

private object WifiBandBandClassifierCompat {
    fun channel5935() = WifiBandClassifier.classify(5935).channel
}
