package it.krpng.cassa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterProfileTest {
    @Test
    fun `frozen defaults are 80mm no cut and DETAILED`() {
        val profile =
            PrinterProfile(
                id = "profile-1",
                name = "Test",
                charsPerLine = 32,
                codePage = "CP437",
                feedLines = 3,
            )

        assertEquals(PrinterProfile.DEFAULT_PAPER_WIDTH_MM, profile.paperWidthMm)
        assertEquals(80, profile.paperWidthMm)
        assertEquals(PrinterProfile.DEFAULT_SUPPORTS_CUT, profile.supportsCut)
        assertEquals(false, profile.supportsCut)
        assertEquals(PricePrintMode.DETAILED, profile.pricePrintMode)
        assertNull(profile.cutCommandVariant)
    }

    @Test
    fun `TOTAL_ONLY is constructible on a profile`() {
        val profile =
            PrinterProfile(
                id = "profile-2",
                name = "Totals",
                charsPerLine = 42,
                codePage = "CP858",
                feedLines = 2,
                pricePrintMode = PricePrintMode.TOTAL_ONLY,
            )

        assertEquals(PricePrintMode.TOTAL_ONLY, profile.pricePrintMode)
    }

    @Test
    fun `price print modes match the frozen contract`() {
        assertEquals(
            listOf("DETAILED", "TOTAL_ONLY"),
            PricePrintMode.entries.map(PricePrintMode::name),
        )
    }
}