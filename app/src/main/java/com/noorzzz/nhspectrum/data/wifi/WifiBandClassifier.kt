package com.noorzzz.nhspectrum.data.wifi

enum class WifiBand(val label: String) { GHZ_2_4("2.4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz"), OTHER("Other") }

data class WifiFrequencyInfo(val band: WifiBand, val channel: Int?)

object WifiBandClassifier {
    fun classify(frequencyMhz: Int): WifiFrequencyInfo = when (frequencyMhz) {
        2484 -> WifiFrequencyInfo(WifiBand.GHZ_2_4, 14)
        5935 -> WifiFrequencyInfo(WifiBand.GHZ_6, 2)
        in 2412..2472 -> WifiFrequencyInfo(WifiBand.GHZ_2_4, (frequencyMhz - 2407) / 5)
        in 5000..5895 -> WifiFrequencyInfo(WifiBand.GHZ_5, (frequencyMhz - 5000) / 5)
        in 5955..7115 -> WifiFrequencyInfo(WifiBand.GHZ_6, (frequencyMhz - 5950) / 5)
        else -> WifiFrequencyInfo(WifiBand.OTHER, null)
    }

    fun supportedChannelWidth(widthMhz: Int): Boolean = widthMhz in setOf(20, 40, 80, 160, 320)
}
