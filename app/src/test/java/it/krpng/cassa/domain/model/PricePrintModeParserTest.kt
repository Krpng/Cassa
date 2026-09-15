package it.krpng.cassa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PricePrintModeParserTest {
    @Test
    fun `null or blank falls back to DETAILED`() {
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault(null))
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault(""))
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault("   "))
    }

    @Test
    fun `known enum names decode`() {
        assertEquals(
            PricePrintMode.DETAILED,
            PricePrintModeParser.parseStoredOrDefault("DETAILED"),
        )
        assertEquals(
            PricePrintMode.TOTAL_ONLY,
            PricePrintModeParser.parseStoredOrDefault("TOTAL_ONLY"),
        )
    }

    @Test
    fun `unknown or corrupt values fall back to DETAILED`() {
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault("total_only"))
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault("DETAILED "))
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault("UNKNOWN"))
        assertEquals(PricePrintMode.DETAILED, PricePrintModeParser.parseStoredOrDefault("{broken}"))
    }
}