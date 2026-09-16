package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile

/**
 * Pure-Kotlin ESC/POS encoder (D-052 / PRINT-005 / D-060).
 * Maps [PrintableDocument] + [PrinterProfile] → [EncodeResult].
 * Optional physical `ESC t` when [PrinterProfile.escPosCodeTable] is set.
 * No Bluetooth, NETUM, or receipt layout.
 */
interface EscPosEncoder {
    fun encode(
        document: PrintableDocument,
        profile: PrinterProfile,
    ): EncodeResult
}
