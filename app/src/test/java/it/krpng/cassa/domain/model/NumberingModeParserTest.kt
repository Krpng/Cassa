package it.krpng.cassa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberingModeParserTest {
    @Test
    fun parsesKnownModesExplicitly() {
        assertEquals(NumberingMode.SEQUENTIAL, NumberingModeParser.parseOrNull("SEQUENTIAL"))
        assertEquals(NumberingMode.RANDOM, NumberingModeParser.parseOrNull("RANDOM"))
    }

    @Test
    fun rejectsUnknownWithoutFallback() {
        assertNull(NumberingModeParser.parseOrNull("random"))
        assertNull(NumberingModeParser.parseOrNull("SEQUENTIAL "))
        assertNull(NumberingModeParser.parseOrNull("UNKNOWN"))
        assertNull(NumberingModeParser.parseOrNull(""))
    }
}
