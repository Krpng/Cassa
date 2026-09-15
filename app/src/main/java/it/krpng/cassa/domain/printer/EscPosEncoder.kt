package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile

/**
 * Pure-Kotlin ESC/POS encoder (D-052 / PRINT-005).
 * Maps [PrintableDocument] + [PrinterProfile] → [EncodeResult].
 * No Bluetooth, NETUM, layout, or hardware code-page select (`ESC t`).
 */
interface EscPosEncoder {
    fun encode(
        document: PrintableDocument,
        profile: PrinterProfile,
    ): EncodeResult
}
