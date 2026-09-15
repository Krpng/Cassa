package it.krpng.cassa.domain.printer

/**
 * Print variant for composition (D-049 / PRINT-002).
 * Reprint uses [FINAL] content — there is no separate REPRINT kind.
 */
enum class PrintKind {
    DRAFT,
    FINAL,
}