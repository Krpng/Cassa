package it.krpng.cassa.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test
    fun `stores amounts as integer cents`() {
        assertEquals(150L, Money.ofCents(150).cents)
    }

    @Test
    fun `formats zero and cents as euros in Italian format`() {
        assertEquals("0,00 €", Money.ZERO.formatEur())
        assertEquals("0,01 €", Money.ofCents(1).formatEur())
        assertEquals("1,50 €", Money.ofCents(150).formatEur())
        assertEquals("1.234,56 €", Money.ofCents(123_456).formatEur())
    }

    @Test
    fun `PRICE base amounts sum exactly and format without rounding`() {
        val total = Money.ofCents(700) + Money.ofCents(200) + Money.ofCents(200)

        assertEquals(1_100L, total.cents)
        assertEquals("11,00 €", total.formatEur())
    }

    @Test
    fun `repeated cent sums remain exact`() {
        val total = (1..10).fold(Money.ZERO) { amount, _ -> amount + Money.ofCents(10) }

        assertEquals(100L, total.cents)
        assertEquals("1,00 €", total.formatEur())
    }

    @Test
    fun `multiplies unit price by a positive quantity`() {
        assertEquals(Money.ofCents(2_100), Money.ofCents(700) * 3)
    }

    @Test
    fun `subtracts without allowing a negative result`() {
        assertEquals(Money.ofCents(500), Money.ofCents(700) - Money.ofCents(200))
        assertThrows(IllegalArgumentException::class.java) {
            Money.ofCents(200) - Money.ofCents(700)
        }
    }

    @Test
    fun `rejects negative cents and non-positive quantities`() {
        assertThrows(IllegalArgumentException::class.java) { Money.ofCents(-1) }
        assertThrows(IllegalArgumentException::class.java) { Money.ofCents(100) * 0 }
        assertThrows(IllegalArgumentException::class.java) { Money.ofCents(100) * -1 }
    }

    @Test
    fun `reports arithmetic overflow explicitly`() {
        assertThrows(ArithmeticException::class.java) {
            Money.ofCents(Long.MAX_VALUE) + Money.ofCents(1)
        }
        assertThrows(ArithmeticException::class.java) {
            Money.ofCents(Long.MAX_VALUE) * 2
        }
    }

    @Test
    fun `compares amounts by cents`() {
        assertTrue(Money.ofCents(99) < Money.ofCents(100))
        assertEquals(0, Money.ofCents(100).compareTo(Money.ofCents(100)))
    }
}
