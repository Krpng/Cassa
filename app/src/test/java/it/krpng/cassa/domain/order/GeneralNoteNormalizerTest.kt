package it.krpng.cassa.domain.order

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneralNoteNormalizerTest {
    @Test
    fun blankAndWhitespaceBecomeNull() {
        // ORDER-026 / ORDER-029
        assertNull(GeneralNoteNormalizer.normalize(null))
        assertNull(GeneralNoteNormalizer.normalize(""))
        assertNull(GeneralNoteNormalizer.normalize("   "))
        assertNull(GeneralNoteNormalizer.normalize("\n\t  "))
    }

    @Test
    fun externalTrimPreservesInternalSpacesAndLineBreaks() {
        // ORDER-030 / ORDER-031
        assertEquals(
            "consegna dopo le 21",
            GeneralNoteNormalizer.normalize("   consegna dopo le 21   "),
        )
        assertEquals(
            "riga1\nriga2",
            GeneralNoteNormalizer.normalize("  riga1\nriga2  "),
        )
        assertEquals(
            "a  b",
            GeneralNoteNormalizer.normalize("a  b"),
        )
    }
}
