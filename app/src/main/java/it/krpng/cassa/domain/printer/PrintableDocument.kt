package it.krpng.cassa.domain.printer

/**
 * Intermediate receipt/ticket before EscPosEncoder (D-049 / PRINT-002).
 * Not ByteArray, not ESC/POS, not Bluetooth metadata.
 */
data class PrintableDocument(
    val kind: PrintKind,
    val lines: List<PrintableLine>,
)