package it.krpng.cassa.domain.search

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchEngineTest {
    @Test
    fun `SEARCH-001 product name starts-with ranks before name contains`() {
        val results = search(
            query = "mar",
            product(id = 2, name = "Pizza Margherita"),
            product(id = 1, name = "Marinara"),
        )

        assertEquals(listOf("Marinara", "Pizza Margherita"), results.map { it.product.name })
        assertEquals(
            listOf(ProductMatchType.NAME_STARTS_WITH, ProductMatchType.NAME_CONTAINS),
            results.map { it.matchType },
        )
    }

    @Test
    fun `SEARCH-002 product name contains is returned`() {
        val result = search(query = "gher", product(name = "Margherita")).single()

        assertEquals(ProductMatchType.NAME_CONTAINS, result.matchType)
        assertNull(result.matchedIngredient)
    }

    @Test
    fun `SEARCH-003 ingredient starts-with is returned`() {
        val result = search(
            query = "parm",
            product(
                name = "Quattro formaggi",
                ingredients = listOf(productIngredient(name = "Parmigiano")),
            ),
        ).single()

        assertEquals(ProductMatchType.INGREDIENT_STARTS_WITH, result.matchType)
    }

    @Test
    fun `SEARCH-004 ingredient contains is returned`() {
        val result = search(
            query = "mig",
            product(
                name = "Quattro formaggi",
                ingredients = listOf(productIngredient(name = "Parmigiano")),
            ),
        ).single()

        assertEquals(ProductMatchType.INGREDIENT_CONTAINS, result.matchType)
    }

    @Test
    fun `SEARCH-005 search is case-insensitive`() {
        val result = search(query = "MARGHERITA", product(name = "Margherita")).single()

        assertEquals(ProductMatchType.NAME_STARTS_WITH, result.matchType)
    }

    @Test
    fun `SEARCH-006 search is accent-tolerant`() {
        val result = search(query = "PIZZA", product(name = "Pìzza rustica")).single()

        assertEquals(ProductMatchType.NAME_STARTS_WITH, result.matchType)
    }

    @Test
    fun `SEARCH-007 ingredient match preserves display name`() {
        val result = search(
            query = "  PARMIGIANO  ",
            product(
                name = "Quattro formaggi",
                ingredients = listOf(productIngredient(name = "Parmigiano Reggiano")),
            ),
        ).single()

        assertEquals("Parmigiano Reggiano", result.matchedIngredient)
    }

    @Test
    fun `SEARCH-008 inactive products are excluded`() {
        val results = search(
            query = "mar",
            product(id = 1, name = "Margherita", active = false),
            product(id = 2, name = "Marinara"),
        )

        assertEquals(listOf("Marinara"), results.map { it.product.name })
    }

    @Test
    fun `name contains ranks before ingredient starts-with`() {
        val results = search(
            query = "mar",
            product(id = 1, name = "Pizza Margherita"),
            product(
                id = 2,
                name = "Diavola",
                ingredients = listOf(productIngredient(name = "Marinara")),
            ),
        )

        assertEquals(listOf(1L, 2L), results.map { it.product.id })
        assertEquals(
            listOf(ProductMatchType.NAME_CONTAINS, ProductMatchType.INGREDIENT_STARTS_WITH),
            results.map { it.matchType },
        )
    }

    @Test
    fun `equal ranks use normalized product name then id`() {
        val results = search(
            query = "pizza",
            product(id = 3, name = "Pizza Zeta"),
            product(id = 2, name = "Pizza Alfa"),
            product(id = 1, name = "Pizza Alfa"),
        )

        assertEquals(listOf(1L, 2L, 3L), results.map { it.product.id })
    }

    @Test
    fun `best matching ingredient uses match class then display order`() {
        val result = search(
            query = "mo",
            product(
                name = "Ortolana",
                ingredients = listOf(
                    productIngredient(id = 1, name = "Pomodoro", displayOrder = 1),
                    productIngredient(id = 2, name = "Mozzarella", displayOrder = 3),
                    productIngredient(id = 3, name = "Mortadella", displayOrder = 2),
                ),
            ),
        ).single()

        assertEquals(ProductMatchType.INGREDIENT_STARTS_WITH, result.matchType)
        assertEquals("Mortadella", result.matchedIngredient)
    }

    @Test
    fun `query whitespace is trimmed and collapsed`() {
        val result = search(
            query = "  PIZZA   MAR  ",
            product(name = "Pizza Margherita"),
        ).single()

        assertEquals(ProductMatchType.NAME_STARTS_WITH, result.matchType)
    }

    @Test
    fun `empty and whitespace-only queries return no search matches`() {
        val catalog = arrayOf(product(name = "Margherita"))

        assertTrue(search(query = "", *catalog).isEmpty())
        assertTrue(search(query = "  \t\n ", *catalog).isEmpty())
    }

    @Test
    fun `misspelling is not corrected or fuzzy-matched`() {
        assertTrue(search(query = "Margerita", product(name = "Margherita")).isEmpty())
    }

    private fun search(
        query: String,
        vararg products: Product,
    ): List<ProductSearchResult> = ProductSearchEngine.search(products.toList(), query)

    private fun product(
        id: Long = 1,
        name: String,
        active: Boolean = true,
        ingredients: List<ProductIngredient> = emptyList(),
    ): Product = Product(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        category = ProductCategory.PIZZA,
        price = Money.ZERO,
        automaticExtrasPricing = true,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ingredients = ingredients,
    )

    private fun productIngredient(
        id: Long = 1,
        name: String,
        displayOrder: Int = 1,
    ): ProductIngredient = ProductIngredient(
        ingredient = Ingredient(
            id = id,
            name = name,
            normalizedName = TextNormalizer.normalize(name),
            active = true,
        ),
        displayOrder = displayOrder,
    )
}
