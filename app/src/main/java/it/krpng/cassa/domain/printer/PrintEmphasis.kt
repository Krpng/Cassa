package it.krpng.cassa.domain.printer

/**
 * Semantic line emphasis hint for later encoder/formatter (D-049).
 * Not ESC/POS commands — NORMAL / EMPHASIZED only.
 */
enum class PrintEmphasis {
    NORMAL,
    EMPHASIZED,
}