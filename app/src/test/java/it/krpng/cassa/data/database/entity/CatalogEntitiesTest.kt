package it.krpng.cassa.data.database.entity

import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEntitiesTest {
    @Test
    fun `new product uses documented defaults and nullable printed name`() {
        val product = ProductEntity(
            name = "Margherita",
            normalizedName = "margherita",
            printedName = null,
            category = ProductCategory.PIZZA,
            priceCents = 700,
            createdAt = 1_000,
            updatedAt = 1_000,
        )

        assertEquals(0, product.id)
        assertEquals(700, product.priceCents)
        assertEquals(ProductCategory.PIZZA, product.category)
        assertNull(product.printedName)
        assertTrue(product.automaticExtrasPricing)
        assertTrue(product.active)
    }

    @Test
    fun `new ingredient is active by default`() {
        val ingredient = IngredientEntity(
            name = "Mozzarella",
            normalizedName = "mozzarella",
        )

        assertEquals(0, ingredient.id)
        assertTrue(ingredient.active)
    }

    @Test
    fun `free addition is valid and active by default`() {
        val addition = AdditionEntity(
            name = "Origano",
            normalizedName = "origano",
            printedName = null,
            priceCents = 0,
            createdAt = 1_000,
            updatedAt = 1_000,
        )

        assertEquals(0, addition.priceCents)
        assertTrue(addition.active)
    }

    @Test
    fun `negative catalog prices are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductEntity(
                name = "Margherita",
                normalizedName = "margherita",
                printedName = null,
                category = ProductCategory.PIZZA,
                priceCents = -1,
                createdAt = 1_000,
                updatedAt = 1_000,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdditionEntity(
                name = "Prosciutto",
                normalizedName = "prosciutto",
                printedName = null,
                priceCents = -1,
                createdAt = 1_000,
                updatedAt = 1_000,
            )
        }
    }
}
