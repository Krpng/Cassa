package it.krpng.cassa.domain.printer

/**
 * One pre-encoder printable line (D-049 / PRINT-002).
 * Pure text + emphasis hint — no bytes, no hardware commands.
 */
data class PrintableLine(
    val text: String,
    val emphasis: PrintEmphasis = PrintEmphasis.NORMAL,
)