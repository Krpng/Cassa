package it.krpng.cassa.domain.printer

/**
 * Physical line alignment hint for EscPosEncoder (D-060 / HW-001).
 * Not ESC/POS bytes — LEFT / CENTER / RIGHT only.
 */
enum class PrintAlignment {
    LEFT,
    CENTER,
    RIGHT,
}
