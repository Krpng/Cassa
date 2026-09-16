package it.krpng.cassa.domain.printer

/**
 * One pre-encoder printable line (D-049 / PRINT-002 / D-060).
 * Pure text + semantic formatting hints — no bytes, no hardware commands.
 *
 * Defaults ([PrintAlignment.LEFT], [PrintTextScale.NORMAL]) preserve existing receipts.
 */
data class PrintableLine(
    val text: String,
    val emphasis: PrintEmphasis = PrintEmphasis.NORMAL,
    val alignment: PrintAlignment = PrintAlignment.LEFT,
    val textScale: PrintTextScale = PrintTextScale.NORMAL,
)
