package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PricingCalculatorTest {
    @Test
    fun `base price without additions remains unchanged`() {
        val result = calculate(baseCents = 700)

        assertEquals(Money.ZERO, result.automaticExtrasTotal)
        assertEquals(Money.ofCents(700), result.automaticUnitPrice)
        assertEquals(Money.ofCents(700), result.finalUnitPrice)
        assertEquals(Money.ofCents(700), result.lineTotal)
    }

    @Test
    fun `single addition increases automatic price`() {
        val result = calculate(baseCents = 700, additionCents = listOf(200))

        assertEquals(Money.ofCents(200), result.automaticExtrasTotal)
        assertEquals(Money.ofCents(900), result.finalUnitPrice)
    }

    @Test
    fun `PRICE-001 multiple additions are summed exactly`() {
        val result = calculate(baseCents = 700, additionCents = listOf(200, 200))

        assertEquals(Money.ofCents(400), result.automaticExtrasTotal)
        assertEquals(Money.ofCents(1_100), result.automaticUnitPrice)
        assertEquals(Money.ofCents(1_100), result.finalUnitPrice)
    }

    @Test
    fun `PRICE-002 removals cannot subtract because they are not pricing inputs`() {
        val resultForItemWithRemoval = calculate(baseCents = 700)

        assertEquals(Money.ofCents(700), resultForItemWithRemoval.finalUnitPrice)
    }

    @Test
    fun `PRICE-003 disabled automatic extras leave base price unchanged`() {
        val result = calculate(
            baseCents = 700,
            additionCents = listOf(200, 200),
            automaticExtrasPricing = false,
        )

        assertEquals(Money.ZERO, result.automaticExtrasTotal)
        assertEquals(Money.ofCents(700), result.automaticUnitPrice)
        assertEquals(Money.ofCents(700), result.finalUnitPrice)
    }

    @Test
    fun `PRICE-004 manual override has absolute precedence`() {
        val result = calculate(
            baseCents = 700,
            additionCents = listOf(200, 200),
            manualUnitPriceCents = 1_000,
        )

        assertEquals(Money.ofCents(1_100), result.automaticUnitPrice)
        assertEquals(Money.ofCents(1_000), result.finalUnitPrice)
        assertEquals(Money.ofCents(1_000), result.lineTotal)
    }

    @Test
    fun `manual override also wins when automatic extras are disabled`() {
        val result = calculate(
            baseCents = 700,
            additionCents = listOf(400),
            automaticExtrasPricing = false,
            manualUnitPriceCents = 1_000,
        )

        assertEquals(Money.ZERO, result.automaticExtrasTotal)
        assertEquals(Money.ofCents(700), result.automaticUnitPrice)
        assertEquals(Money.ofCents(1_000), result.finalUnitPrice)
    }

    @Test
    fun `PRICE-005 clearing manual override restores automatic price`() {
        val withOverride = calculate(
            baseCents = 700,
            additionCents = listOf(200, 200),
            manualUnitPriceCents = 1_000,
        )
        val afterReset = calculate(
            baseCents = 700,
            additionCents = listOf(200, 200),
            manualUnitPriceCents = null,
        )

        assertEquals(Money.ofCents(1_000), withOverride.finalUnitPrice)
        assertEquals(Money.ofCents(1_100), afterReset.finalUnitPrice)
    }

    @Test
    fun `PRICE-006 quantity multiplies final unit price`() {
        val result = calculate(
            baseCents = 700,
            additionCents = listOf(200, 200),
            quantity = 2,
        )

        assertEquals(Money.ofCents(1_100), result.finalUnitPrice)
        assertEquals(Money.ofCents(2_200), result.lineTotal)
    }

    @Test
    fun `PRICE-007 zero priced addition is valid`() {
        val result = calculate(baseCents = 700, additionCents = listOf(0))

        assertEquals(Money.ZERO, result.automaticExtrasTotal)
        assertEquals(Money.ofCents(700), result.finalUnitPrice)
    }

    @Test
    fun `integer cents stay exact without floating point rounding`() {
        val result = calculate(baseCents = 1, additionCents = listOf(1, 1), quantity = 3)

        assertEquals(3L, result.finalUnitPrice.cents)
        assertEquals(9L, result.lineTotal.cents)
    }

    @Test
    fun `PRICE-008 reasonable prices and quantities do not overflow`() {
        val result = calculate(
            baseCents = 1_000_000,
            additionCents = listOf(500_000),
            quantity = 1_000,
        )

        assertEquals(Money.ofCents(1_500_000_000), result.lineTotal)
    }

    @Test
    fun `Money validation rejects invalid quantities`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculate(baseCents = 700, quantity = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(baseCents = 700, quantity = -1)
        }
    }

    @Test
    fun `Money overflow is propagated explicitly`() {
        assertThrows(ArithmeticException::class.java) {
            PricingCalculator.calculate(
                baseUnitPrice = Money.ofCents(Long.MAX_VALUE),
                additionPrices = listOf(Money.ofCents(1)),
                automaticExtrasPricing = true,
                manualUnitPrice = null,
                quantity = 1,
            )
        }
        assertThrows(ArithmeticException::class.java) {
            calculate(baseCents = Long.MAX_VALUE, quantity = 2)
        }
    }

    private fun calculate(
        baseCents: Long,
        additionCents: List<Long> = emptyList(),
        automaticExtrasPricing: Boolean = true,
        manualUnitPriceCents: Long? = null,
        quantity: Int = 1,
    ): PricingResult = PricingCalculator.calculate(
        baseUnitPrice = Money.ofCents(baseCents),
        additionPrices = additionCents.map(Money::ofCents),
        automaticExtrasPricing = automaticExtrasPricing,
        manualUnitPrice = manualUnitPriceCents?.let(Money::ofCents),
        quantity = quantity,
    )
}
