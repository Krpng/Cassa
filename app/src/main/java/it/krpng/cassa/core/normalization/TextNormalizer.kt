package it.krpng.cassa.core.normalization

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String {
        val collapsedWhitespace = WHITESPACE.replace(value, " ").trim()
        val localeNeutralLowercase = collapsedWhitespace.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(localeNeutralLowercase, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
    }

    private val WHITESPACE = Regex("[\\p{Z}\\s]+")
    private val COMBINING_MARKS = Regex("\\p{M}+")
}
