package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionFormValidatorTest {
    @Test
    fun `zero and decimal prices are accepted without floating point`() {
        assertPrice("0", Money.ZERO)
        assertPrice("0,00", Money.ZERO)
        assertPrice("1,5", Money.ofCents(150))
        assertPrice("1,50", Money.ofCents(150))
        assertPrice("1.50", Money.ofCents(150))
    }

    @Test
    fun `negative malformed excessive precision and overflow prices are rejected`() {
        listOf("", "-1", "1,", ",50", "7,123", "1.2.3", "abc", "92233720368547758,08")
            .forEach { input ->
                val result = AdditionFormValidator.validate("Olive", "", input)
                    as AdditionFormValidationResult.Invalid
                assertTrue("Expected invalid price: $input", result.errors.price != null)
            }
    }

    @Test
    fun `valid form trims names and maps blank printed name to null`() {
        val result = AdditionFormValidator.validate(
            name = "  Prosciutto  ",
            printedName = "   ",
            priceInput = " 2,00 ",
        ) as AdditionFormValidationResult.Valid

        assertEquals("Prosciutto", result.fields.name)
        assertNull(result.fields.printedName)
        assertEquals(Money.ofCents(200), result.fields.price)
    }

    @Test
    fun `blank name exposes an addition specific validation error`() {
        val result = AdditionFormValidator.validate(
            name = "   ",
            printedName = "OLIVE",
            priceInput = "0",
        ) as AdditionFormValidationResult.Invalid

        assertEquals("Inserisci il nome dell'aggiunta.", result.errors.name)
    }

    private fun assertPrice(input: String, expected: Money) {
        val result = AdditionFormValidator.validate("Test", "", input)
            as AdditionFormValidationResult.Valid
        assertEquals(expected, result.fields.price)
    }
}
