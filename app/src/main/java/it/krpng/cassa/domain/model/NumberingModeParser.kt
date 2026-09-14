package it.krpng.cassa.domain.model

/**
 * Parses persisted `app_settings.numberingMode` strings.
 * Unknown values are rejected — no silent fallback to RANDOM/SEQUENTIAL.
 */
object NumberingModeParser {
    fun parseOrNull(raw: String): NumberingMode? =
        NumberingMode.entries.firstOrNull { mode -> mode.name == raw }
}
