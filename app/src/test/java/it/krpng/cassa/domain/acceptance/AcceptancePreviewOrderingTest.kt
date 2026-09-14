package it.krpng.cassa.domain.acceptance

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptancePreviewOrderingTest {
    @Test
    fun `ACCEPT-T010 category order is PIZZE then FRITTURA then BIBITE and omits empty`() {
        val items = listOf(
            item(id = "b1", category = ProductCategory.BIBITA, sequence = 1),
            item(id = "p1", category = ProductCategory.PIZZA, sequence = 1),
            item(id = "f1", category = ProductCategory.FRITTURA, sequence = 1),
        )

        val sections = AcceptancePreviewOrdering.groupByCategory(items)

        assertEquals(listOf("PIZZE", "FRITTURA", "BIBITE"), sections.map { it.title })
        assertEquals(listOf("p1", "f1", "b1"), sections.flatMap { section -> section.items.map { it.id } })
    }

    @Test
    fun `ACCEPT-T010 empty categories are omitted`() {
        val items = listOf(
            item(id = "p1", category = ProductCategory.PIZZA, sequence = 1),
            item(id = "b1", category = ProductCategory.BIBITA, sequence = 1),
        )

        val sections = AcceptancePreviewOrdering.groupByCategory(items)

        assertEquals(listOf("PIZZE", "BIBITE"), sections.map { it.title })
        assertTrue(sections.none { it.category == ProductCategory.FRITTURA })
    }

    @Test
    fun `ACCEPT-T011 within category sorts by createdSequence ASC not alphabetically`() {
        val items = listOf(
            item(id = "z-late", category = ProductCategory.PIZZA, sequence = 3, name = "AAA"),
            item(id = "a-early", category = ProductCategory.PIZZA, sequence = 1, name = "ZZZ"),
            item(id = "m-mid", category = ProductCategory.PIZZA, sequence = 2, name = "MMM"),
            item(id = "drink", category = ProductCategory.BIBITA, sequence = 10, name = "Acqua"),
        )

        val sections = AcceptancePreviewOrdering.groupByCategory(items)
        val pizzaIds = sections.single { it.category == ProductCategory.PIZZA }.items.map { it.id }

        assertEquals(listOf("a-early", "m-mid", "z-late"), pizzaIds)
    }

    @Test
    fun `snapshots are preserved without catalog reordering`() {
        val addition = OrderItemAddition(
            id = "add-1",
            additionId = 1L,
            nameSnapshot = "Funghi",
            printedNameSnapshot = "FUNGHI",
            listedPrice = Money.ofCents(100),
            chargedPrice = Money.ofCents(100),
            displayOrder = 0,
        )
        val removal = OrderItemRemoval(
            id = "rem-1",
            ingredientId = 2L,
            nameSnapshot = "Basilico",
            displayOrder = 0,
        )
        val items = listOf(
            item(
                id = "custom",
                category = ProductCategory.PIZZA,
                sequence = 1,
                name = "Margherita",
                printed = "MARGHERITA",
                note = "ben cotta",
                additions = listOf(addition),
                removals = listOf(removal),
            ),
        )

        val line = AcceptancePreviewOrdering.groupByCategory(items).single().items.single()

        assertEquals("Margherita", line.productNameSnapshot)
        assertEquals("MARGHERITA", line.productPrintedNameSnapshot)
        assertEquals("Funghi", line.additions.single().nameSnapshot)
        assertEquals("Basilico", line.removals.single().nameSnapshot)
        assertEquals("ben cotta", line.note)
    }

    private fun item(
        id: String,
        category: ProductCategory,
        sequence: Int,
        name: String = id,
        printed: String = name.uppercase(),
        note: String? = null,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem = OrderItem(
        id = id,
        productId = null,
        productNameSnapshot = name,
        productPrintedNameSnapshot = printed,
        categorySnapshot = category,
        quantity = 1,
        baseUnitPrice = Money.ofCents(700),
        automaticExtrasTotal = Money.ZERO,
        manualUnitPrice = null,
        finalUnitPrice = Money.ofCents(700),
        automaticExtrasPricingSnapshot = true,
        note = note,
        createdSequence = sequence,
        additions = additions,
        removals = removals,
    )
}
