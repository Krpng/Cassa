package it.krpng.cassa.core.normalization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `leaves a normal lowercase string unchanged`() {
        assertEquals("margherita", TextNormalizer.normalize("margherita"))
    }

    @Test
    fun `SEARCH-005 normalizes case`() {
        assertEquals("pizza", TextNormalizer.normalize("PIZZA"))
        assertEquals("pizza", TextNormalizer.normalize("PiZzA"))
    }

    @Test
    fun `SEARCH-006 removes canonical accents`() {
        assertEquals("pizza", TextNormalizer.normalize("PÌZZA"))
        assertEquals("perche", TextNormalizer.normalize("perché"))
    }

    @Test
    fun `precomposed and decomposed accents normalize identically`() {
        val precomposed = "caffè"
        val decomposed = "caffe\u0300"

        assertEquals("caffe", TextNormalizer.normalize(precomposed))
        assertEquals(
            TextNormalizer.normalize(precomposed),
            TextNormalizer.normalize(decomposed),
        )
    }

    @Test
    fun `trims and collapses whitespace`() {
        assertEquals(
            "parmigiano reggiano",
            TextNormalizer.normalize("  Parmigiano   Reggiano  "),
        )
    }

    @Test
    fun `collapses Unicode separator characters`() {
        assertEquals(
            "pizza fritta",
            TextNormalizer.normalize("\u00A0Pizza\u2003\u2003Fritta\u00A0"),
        )
    }

    @Test
    fun `handles combined case accents and whitespace`() {
        assertEquals(
            "pizza margherita",
            TextNormalizer.normalize("  PÌZZA   Margherìta  "),
        )
    }

    @Test
    fun `empty and whitespace-only values normalize to empty`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("  \t\n\u00A0"))
    }

    @Test
    fun `result is deterministic and independent of default locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val results = List(20) { TextNormalizer.normalize("  PÌZZA   ITALIANA  ") }

            assertEquals(setOf("pizza italiana"), results.toSet())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `does not correct spelling or mutate display text`() {
        val displayText = "  Margerita  "

        assertEquals("margerita", TextNormalizer.normalize(displayText))
        assertEquals("  Margerita  ", displayText)
    }
}
