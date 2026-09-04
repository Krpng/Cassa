package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderLineMergePolicyTest {
    @Test
    fun `ORDER-001 same standard pizza can merge`() {
        assertTrue(canMerge(pizza(productId = 1), pizza(productId = 1)))
    }

    @Test
    fun `different standard pizzas cannot merge`() {
        assertFalse(canMerge(pizza(productId = 1), pizza(productId = 2)))
    }

    @Test
    fun `pizza with an addition cannot merge automatically`() {
        val customized = pizza(productId = 1, hasAdditions = true)

        assertFalse(canMerge(customized, customized))
        assertFalse(canMerge(pizza(productId = 1), customized))
    }

    @Test
    fun `pizza with a removal cannot merge automatically`() {
        val customized = pizza(productId = 1, hasRemovals = true)

        assertFalse(canMerge(customized, customized))
        assertFalse(canMerge(pizza(productId = 1), customized))
    }

    @Test
    fun `ORDER-003 separately added identical customized pizzas remain separate`() {
        val first = pizza(productId = 1, hasAdditions = true)
        val second = pizza(productId = 1, hasAdditions = true)

        assertFalse(canMerge(first, second))
    }

    @Test
    fun `ORDER-005 non-empty note makes pizza customized`() {
        assertFalse(
            canMerge(
                pizza(productId = 1, note = "Senza tagliare"),
                pizza(productId = 1, note = "Senza tagliare"),
            ),
        )
    }

    @Test
    fun `blank note does not make pizza customized`() {
        assertTrue(
            canMerge(
                pizza(productId = 1, note = "  "),
                pizza(productId = 1),
            ),
        )
    }

    @Test
    fun `ORDER-006 manual price makes pizza customized`() {
        val customized = pizza(productId = 1, manualUnitPrice = Money.ofCents(1_000))

        assertFalse(canMerge(customized, customized))
        assertFalse(canMerge(pizza(productId = 1), customized))
    }

    @Test
    fun `ORDER-002 repeated standard drink can merge`() {
        val existing = standard(productId = 10, category = ProductCategory.BIBITA)
        val incoming = standard(productId = 10, category = ProductCategory.BIBITA)

        assertTrue(canMerge(existing, incoming))
        assertTrue(canMerge(existing, incoming))
    }

    @Test
    fun `same standard fry can merge`() {
        assertTrue(
            canMerge(
                standard(productId = 20, category = ProductCategory.FRITTURA),
                standard(productId = 20, category = ProductCategory.FRITTURA),
            ),
        )
    }

    @Test
    fun `different non-pizza products cannot merge`() {
        assertFalse(
            canMerge(
                standard(productId = 10, category = ProductCategory.BIBITA),
                standard(productId = 11, category = ProductCategory.BIBITA),
            ),
        )
    }

    @Test
    fun `same product id with different categories cannot merge`() {
        assertFalse(
            canMerge(
                standard(productId = 10, category = ProductCategory.BIBITA),
                standard(productId = 10, category = ProductCategory.FRITTURA),
            ),
        )
    }

    @Test
    fun `modified non-pizza lines do not merge with standard quick adds`() {
        val drinkWithNote = standard(
            productId = 10,
            category = ProductCategory.BIBITA,
            note = "Con ghiaccio",
        )
        val fryWithManualPrice = standard(
            productId = 20,
            category = ProductCategory.FRITTURA,
            manualUnitPrice = Money.ofCents(500),
        )

        assertFalse(
            canMerge(
                drinkWithNote,
                standard(productId = 10, category = ProductCategory.BIBITA),
            ),
        )
        assertFalse(
            canMerge(
                fryWithManualPrice,
                standard(productId = 20, category = ProductCategory.FRITTURA),
            ),
        )
    }

    private fun pizza(
        productId: Long,
        hasAdditions: Boolean = false,
        hasRemovals: Boolean = false,
        note: String? = null,
        manualUnitPrice: Money? = null,
    ): OrderLineMergeCandidate = OrderLineMergeCandidate(
        productId = productId,
        category = ProductCategory.PIZZA,
        hasAdditions = hasAdditions,
        hasRemovals = hasRemovals,
        note = note,
        manualUnitPrice = manualUnitPrice,
    )

    private fun standard(
        productId: Long,
        category: ProductCategory,
        note: String? = null,
        manualUnitPrice: Money? = null,
    ): OrderLineMergeCandidate = OrderLineMergeCandidate(
        productId = productId,
        category = category,
        note = note,
        manualUnitPrice = manualUnitPrice,
    )

    private fun canMerge(
        existing: OrderLineMergeCandidate,
        incoming: OrderLineMergeCandidate,
    ): Boolean = OrderLineMergePolicy.canMerge(existing, incoming)
}
