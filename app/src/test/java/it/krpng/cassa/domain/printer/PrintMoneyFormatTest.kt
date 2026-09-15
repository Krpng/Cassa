package it.krpng.cassa.domain.printer

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintMoneyFormatTest {
    @Test
    fun `formats zero and small amounts without euro or grouping`() {
        assertEquals("0,00", PrintMoneyFormat.format(0))
        assertEquals("0,01", PrintMoneyFormat.format(1))
        assertEquals("7,00", PrintMoneyFormat.format(700))
        assertEquals("24,50", PrintMoneyFormat.format(2450))
        assertEquals("1000,00", PrintMoneyFormat.format(100_000))
    }
}
