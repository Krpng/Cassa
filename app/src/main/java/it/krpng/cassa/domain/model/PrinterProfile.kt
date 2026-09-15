package it.krpng.cassa.domain.model

/**
 * Device/layout/charset profile for printing (D-048 / PRINT-001).
 * Pure Kotlin — no Room, DataStore, Bluetooth, or vendor fields.
 *
 * Frozen defaults: paperWidthMm=80, supportsCut=false, pricePrintMode=DETAILED.
 * [charsPerLine] / [codePage] / [feedLines] are calibrated on hardware (no invented defaults).
 */
data class PrinterProfile(
    val id: String,
    val name: String,
    val charsPerLine: Int,
    val codePage: String,
    val feedLines: Int,
    val paperWidthMm: Int = DEFAULT_PAPER_WIDTH_MM,
    val supportsCut: Boolean = DEFAULT_SUPPORTS_CUT,
    val cutCommandVariant: String? = null,
    val pricePrintMode: PricePrintMode = PricePrintMode.DETAILED,
) {
    companion object {
        const val DEFAULT_PAPER_WIDTH_MM: Int = 80
        const val DEFAULT_SUPPORTS_CUT: Boolean = false
    }
}