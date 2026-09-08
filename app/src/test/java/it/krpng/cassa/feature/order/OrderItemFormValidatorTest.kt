package it.krpng.cassa.feature.order

import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderItemFormValidatorTest {
    @Test
    fun `valid fields normalize blank note and preserve optional automatic price`() {
        val result = OrderItemFormValidator.validate(
            quantityInput = "2",
            note = "   ",
            manualPriceInput = null,
        ) as OrderItemFormValidationResult.Valid

        assertEquals(2, result.fields.quantity)
        assertNull(result.fields.note)
        assertNull(result.fields.manualUnitPrice)
    }

    @Test
    fun `valid manual price accepts comma dot and zero in exact cents`() {
        listOf("0" to 0L, "6,00" to 600L, "6.5" to 650L).forEach { (input, cents) ->
            val result = OrderItemFormValidator.validate("1", " Ben cotta ", input)
                as OrderItemFormValidationResult.Valid

            assertEquals(Money.ofCents(cents), result.fields.manualUnitPrice)
            assertEquals("Ben cotta", result.fields.note)
        }
    }

    @Test
    fun `invalid quantity and manual price expose both field errors`() {
        listOf("0", "-1", "", "2147483648").forEach { quantity ->
            val result = OrderItemFormValidator.validate(quantity, "", "7,123")
                as OrderItemFormValidationResult.Invalid

            assertTrue(result.errors.quantity != null)
            assertTrue(result.errors.manualPrice != null)
        }
    }

    @Test
    fun `manual price overflow is rejected`() {
        val result = OrderItemFormValidator.validate(
            quantityInput = "1",
            note = "",
            manualPriceInput = "92233720368547758,08",
        ) as OrderItemFormValidationResult.Invalid

        assertTrue(result.errors.manualPrice != null)
    }
}
