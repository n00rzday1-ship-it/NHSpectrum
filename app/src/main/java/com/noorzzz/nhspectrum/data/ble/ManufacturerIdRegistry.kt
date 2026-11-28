package com.noorzzz.nhspectrum.data.ble

/** Bluetooth SIG Company Identifier labels for commonly encountered devices. */
object ManufacturerIdRegistry {
    private val names = mapOf(
        0x0006 to "Microsoft",
        0x004C to "Apple",
        0x0075 to "Samsung Electronics",
        0x00E0 to "Google",
        0x00C7 to "Tile",
        0x02E5 to "Espressif Systems",
        0x0059 to "Nordic Semiconductor",
        0x0131 to "Sony",
        0x0157 to "Huawei",
        0x038F to "Xiaomi"
    )

    fun resolve(companyId: Int): String = names[companyId] ?: "Unknown company (0x%04X)".format(companyId.coerceIn(0, 0xFFFF))

    fun parseManufacturerHex(rawHex: String): ByteArray? {
        val normalized = rawHex.filterNot(Char::isWhitespace)
        if (normalized.length % 2 != 0 || normalized.length > 1024 || normalized.any { it.digitToIntOrNull(16) == null }) return null
        return runCatching {
            ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
