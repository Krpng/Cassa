package it.krpng.cassa.domain.printer

/**
 * Character width/height scale hint for EscPosEncoder (D-060 / HW-001).
 * MVP subset ≤2× — not ESC/POS bytes.
 */
enum class PrintTextScale {
    NORMAL,
    DOUBLE_WIDTH,
    DOUBLE_HEIGHT,
    DOUBLE_BOTH,
}
