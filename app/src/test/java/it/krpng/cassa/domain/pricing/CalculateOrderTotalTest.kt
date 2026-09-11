package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateOrderTotalTest {
    @Test
    fun `ORDER-040 empty items yield zero total`() {
        val result = CalculateOrderTotal.fromPersistedItems(emptyList())

        assertEquals(emptyList<OrderLineTotalResult>(), result.lineTotals)
        assertEquals(OrderTotalResult.Success(Money.ZERO), result.orderTotal)
    }

    @Test
    fun `ORDER-041 single standard line total`() {
        val items = listOf(item(id = "a", finalCents = 700, quantity = 2))

        val result = CalculateOrderTotal.fromPersistedItems(items)

        assertEquals(
            listOf(OrderLineTotalResult("a", Money.ofCents(1_400))),
            result.lineTotals,
        )
        assertEquals(OrderTotalResult.Success(Money.ofCents(1_400)), result.orderTotal)
    }

    @Test
    fun `ORDER-042 multiple lines sum line totals`() {
        val items = listOf(
            item(id = "a", finalCents = 700, quantity = 2),
            item(id = "b", finalCents = 250, quantity = 3),
        )

        val result = CalculateOrderTotal.fromPersistedItems(items)

        assertEquals(OrderTotalResult.Success(Money.ofCents(2_150)), result.orderTotal)
        assertEquals(Money.ofCents(1_400), result.lineTotals[0].lineTotal)
        assertEquals(Money.ofCents(750), result.lineTotals[1].lineTotal)
    }

    @Test
    fun `ORDER-049 manual zero unit price yields zero line total`() {
        val items = listOf(item(id = "a", finalCents = 0, quantity = 3))

        val result = CalculateOrderTotal.fromPersistedItems(items)

        assertEquals(OrderTotalResult.Success(Money.ZERO), result.orderTotal)
        assertEquals(Money.ZERO, result.lineTotals.single().lineTotal)
    }

    @Test
    fun `uses persisted final unit price without recomputing from catalog`() {
        // Catalog would say 999; persisted final is 500 — total must follow persisted.
        val items = listOf(item(id = "a", finalCents = 500, quantity = 2))

        assertEquals(
            OrderTotalResult.Success(Money.ofCents(1_000)),
            CalculateOrderTotal.orderTotalFromPersistedItems(items),
        )
    }

    @Test
    fun `ORDER-057 multiplication overflow is AmountOverflow`() {
        val items = listOf(
            item(id = "a", finalCents = Long.MAX_VALUE, quantity = 2),
        )

        val result = CalculateOrderTotal.fromPersistedItems(items)

        assertEquals(OrderTotalResult.AmountOverflow, result.orderTotal)
        assertEquals(null, result.lineTotals.single().lineTotal)
    }

    @Test
    fun `ORDER-058 sum overflow is AmountOverflow`() {
        val items = listOf(
            item(id = "a", finalCents = Long.MAX_VALUE, quantity = 1),
            item(id = "b", finalCents = 1, quantity = 1),
        )

        val result = CalculateOrderTotal.fromPersistedItems(items)

        assertEquals(OrderTotalResult.AmountOverflow, result.orderTotal)
        assertEquals(Money.ofCents(Long.MAX_VALUE), result.lineTotals[0].lineTotal)
        assertEquals(Money.ofCents(1), result.lineTotals[1].lineTotal)
    }

    @Test
    fun `lineTotalCents multiplies final unit price by quantity`() {
        assertEquals(
            Money.ofCents(2_100),
            CalculateOrderTotal.lineTotalCents(Money.ofCents(700), 3),
        )
    }

    private fun item(
        id: String,
        finalCents: Long,
        quantity: Int,
    ): OrderItem = OrderItem(
        id = id,
        productId = 1L,
        productNameSnapshot = "Item",
        productPrintedNameSnapshot = "ITEM",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = quantity,
        baseUnitPrice = Money.ofCents(finalCents.coerceAtLeast(0)),
        automaticExtrasTotal = Money.ZERO,
        manualUnitPrice = null,
        finalUnitPrice = Money.ofCents(finalCents),
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = 1,
        additions = emptyList(),
        removals = emptyList(),
    )
}
