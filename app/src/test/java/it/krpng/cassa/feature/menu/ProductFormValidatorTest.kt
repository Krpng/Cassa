package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormValidatorTest {
    @Test
    fun `price parser accepts comma dot zero and one decimal without floating point`() {
        assertEquals(Money.ZERO, ProductFormValidator.parsePrice("0"))
        assertEquals(Money.ofCents(100), ProductFormValidator.parsePrice("1"))
        assertEquals(Money.ofCents(150), ProductFormValidator.parsePrice("1,5"))
        assertEquals(Money.ofCents(150), ProductFormValidator.parsePrice("1,50"))
        assertEquals(Money.ofCents(150), ProductFormValidator.parsePrice("1.50"))
        assertEquals(Money.ofCents(1_000), ProductFormValidator.parsePrice("10,00"))
    }

    @Test
    fun `price parser rejects malformed negative and excessive precision inputs`() {
        listOf("", " ", "-1", "+1", "1,", ",50", "1,234", "1.2.3", "abc")
            .forEach { input -> assertNull("Expected invalid: $input", ProductFormValidator.parsePrice(input)) }
    }

    @Test
    fun `price parser rejects overflow`() {
        assertNull(ProductFormValidator.parsePrice("92233720368547758,08"))
    }

    @Test
    fun `valid form trims display fields and maps blank printed name to null`() {
        val result = ProductFormValidator.validate(
            name = "  Margherita  ",
            printedName = "   ",
            priceInput = " 7,00 ",
        ) as ProductFormValidationResult.Valid

        assertEquals("Margherita", result.fields.name)
        assertNull(result.fields.printedName)
        assertEquals(Money.ofCents(700), result.fields.price)
    }

    @Test
    fun `blank name and invalid price expose field errors`() {
        val result = ProductFormValidator.validate(
            name = "   ",
            printedName = "MARGHERITA",
            priceInput = "7,999",
        ) as ProductFormValidationResult.Invalid

        assertTrue(result.errors.hasErrors)
        assertEquals("Inserisci il nome del prodotto.", result.errors.name)
        assertEquals(
            "Inserisci un prezzo valido con massimo due decimali.",
            result.errors.price,
        )
    }
}
