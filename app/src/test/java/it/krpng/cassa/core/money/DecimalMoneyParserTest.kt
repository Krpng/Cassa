package it.krpng.cassa.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalMoneyParserTest {
    @Test
    fun `parses exact decimal prices without floating point`() {
        assertEquals(Money.ZERO, DecimalMoneyParser.parse("0"))
        assertEquals(Money.ofCents(150), DecimalMoneyParser.parse("1,5"))
        assertEquals(Money.ofCents(150), DecimalMoneyParser.parse("1.50"))
        assertEquals(Money.ofCents(1_000), DecimalMoneyParser.parse("10,00"))
    }

    @Test
    fun `rejects negative malformed excessive precision and overflow`() {
        listOf("", "-1", "1,234", "1.2.3", "abc", "92233720368547758,08")
            .forEach { input -> assertNull(input, DecimalMoneyParser.parse(input)) }
    }
}
