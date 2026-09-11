package it.krpng.cassa.feature.order

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizedPizzaRowHighlightTest {
    @Test
    fun standardPizzaIsNotHighlighted() {
        assertFalse(pizza().isCustomizedPizzaRow())
    }

    @Test
    fun blankOrWhitespaceNoteIsNotCustomForNoteCriterion() {
        assertFalse(pizza(note = null).isCustomizedPizzaRow())
        assertFalse(pizza(note = "").isCustomizedPizzaRow())
        assertFalse(pizza(note = "   ").isCustomizedPizzaRow())
    }

    @Test
    fun nullManualPriceIsNotCustomForPriceCriterion() {
        assertFalse(pizza(manualUnitPrice = null).isCustomizedPizzaRow())
    }

    @Test
    fun zeroManualPriceIsCustomAndHighlighted() {
        assertTrue(pizza(manualUnitPrice = Money.ZERO).isCustomizedPizzaRow())
    }

    @Test
    fun additionRemovalNoteOrManualPriceHighlightsPizza() {
        assertTrue(
            pizza(
                additions = listOf(
                    OrderItemAddition(
                        id = "a1",
                        additionId = 1L,
                        nameSnapshot = "Prosciutto",
                        printedNameSnapshot = "Prosciutto",
                        listedPrice = Money.ofCents(100),
                        chargedPrice = Money.ofCents(100),
                        displayOrder = 0,
                    ),
                ),
            ).isCustomizedPizzaRow(),
        )
        assertTrue(
            pizza(
                removals = listOf(
                    OrderItemRemoval(
                        id = "r1",
                        ingredientId = 2L,
                        nameSnapshot = "Cipolla",
                        displayOrder = 0,
                    ),
                ),
            ).isCustomizedPizzaRow(),
        )
        assertTrue(pizza(note = "Ben cotta").isCustomizedPizzaRow())
        assertTrue(pizza(manualUnitPrice = Money.ofCents(500)).isCustomizedPizzaRow())
    }

    @Test
    fun nonPizzaAndGeneralNoteDoNotTriggerCustomPizzaHighlight() {
        // ORDER-039
        assertFalse(
            pizza(
                category = ProductCategory.BIBITA,
                note = "Con ghiaccio",
            ).isCustomizedPizzaRow(),
        )
        assertFalse(
            pizza(
                category = ProductCategory.FRITTURA,
                manualUnitPrice = Money.ZERO,
            ).isCustomizedPizzaRow(),
        )
        // orders.generalNote is outside OrderItem and cannot enter this predicate.
        assertFalse(pizza().isCustomizedPizzaRow())
    }

    private fun pizza(
        category: ProductCategory = ProductCategory.PIZZA,
        note: String? = null,
        manualUnitPrice: Money? = null,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem = OrderItem(
        id = "item-1",
        productId = 1L,
        productNameSnapshot = "Margherita",
        productPrintedNameSnapshot = "Margherita",
        categorySnapshot = category,
        quantity = 1,
        baseUnitPrice = Money.ofCents(700),
        automaticExtrasTotal = Money.ZERO,
        manualUnitPrice = manualUnitPrice,
        finalUnitPrice = manualUnitPrice ?: Money.ofCents(700),
        automaticExtrasPricingSnapshot = true,
        note = note,
        createdSequence = 1,
        additions = additions,
        removals = removals,
    )
}
