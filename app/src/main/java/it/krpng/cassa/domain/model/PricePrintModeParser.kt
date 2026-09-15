package it.krpng.cassa.domain.model

/**
 * Maps a persisted preference string to [PricePrintMode] (D-050).
 * Missing/unknown/corrupt values fall back to [PricePrintMode.DETAILED] without inventing modes.
 */
object PricePrintModeParser {
    fun parseStoredOrDefault(raw: String?): PricePrintMode {
        if (raw.isNullOrBlank()) return PricePrintMode.DETAILED
        return PricePrintMode.entries.firstOrNull { it.name == raw }
            ?: PricePrintMode.DETAILED
    }
}