package it.krpng.cassa.domain.model

/**
 * How line/addition prices appear on a receipt (D-048 / PRINT-001).
 * Rendering behavior is owned by later PRINT tasks — enum/contract only here.
 */
enum class PricePrintMode {
    /** Default: print line/addition prices when coherent. */
    DETAILED,

    /** Print only the order total. */
    TOTAL_ONLY,
}