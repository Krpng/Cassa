package it.krpng.cassa.domain.model

/**
 * Device/layout/charset profile for printing (D-048 / PRINT-001 / D-060 / D-061).
 * Pure Kotlin — no Room, DataStore, Bluetooth, or vendor fields.
 *
 * Constructor defaults: paperWidthMm=80, supportsCut=false, pricePrintMode=DETAILED,
 * escPosCodeTable=null (omit ESC t unless set).
 *
 * NETUM M9 operational physical profile (**D-061**, paper-validated):
 * paperWidthMm=80, charsPerLine=42 (safe NORMAL layout width — not claimed exact max),
 * codePage=IBM00858, escPosCodeTable=19, feedLines=3, supportsCut=false,
 * cutCommandVariant=null. Text scale (e.g. DOUBLE_BOTH) is **not** a profile field.
 *
 * [codePage] = JVM Charset name (Unicode → bytes).
 * [escPosCodeTable] = optional physical ESC/POS table selector for ESC t (0..255), distinct
 * from [codePage].
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
    val escPosCodeTable: Int? = null,
) {
    companion object {
        const val DEFAULT_PAPER_WIDTH_MM: Int = 80
        const val DEFAULT_SUPPORTS_CUT: Boolean = false
    }
}
