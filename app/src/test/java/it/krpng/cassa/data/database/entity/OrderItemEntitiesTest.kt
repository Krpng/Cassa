package it.krpng.cassa.data.database.entity

import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderItemEntitiesTest {
    @Test
    fun `order item preserves resolved snapshots without a live product`() {
        val item = orderItem(productId = null)

        assertNull(item.productId)
        assertEquals("Margherita", item.productNameSnapshot)
        assertEquals("MARGHERITA", item.productPrintedNameSnapshot)
        assertEquals(ProductCategory.PIZZA, item.categorySnapshot)
        assertEquals(700, item.baseUnitPriceCents)
        assertEquals(700, item.finalUnitPriceCents)
        assertNull(item.manualUnitPriceCents)
    }

    @Test
    fun `addition preserves listed and uncharged prices without a live catalog row`() {
        val addition = OrderItemAdditionEntity(
            id = "order-item-addition-id",
            orderItemId = "order-item-id",
            additionId = null,
            additionNameSnapshot = "Prosciutto",
            additionPrintedNameSnapshot = "PROSCIUTTO",
            listedPriceCents = 200,
            chargedPriceCents = 0,
            displayOrder = 2,
        )

        assertNull(addition.additionId)
        assertEquals(200, addition.listedPriceCents)
        assertEquals(0, addition.chargedPriceCents)
        assertEquals(2, addition.displayOrder)
    }

    @Test
    fun `removal preserves its snapshot and order without a live ingredient`() {
        val removal = OrderItemRemovalEntity(
            id = "order-item-removal-id",
            orderItemId = "order-item-id",
            ingredientId = null,
            ingredientNameSnapshot = "Mozzarella",
            displayOrder = 3,
        )

        assertNull(removal.ingredientId)
        assertEquals("Mozzarella", removal.ingredientNameSnapshot)
        assertEquals(3, removal.displayOrder)
    }

    @Test
    fun `order item validates quantity and persisted monetary values`() {
        assertThrows(IllegalArgumentException::class.java) {
            orderItem(quantity = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderItem(baseUnitPriceCents = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderItem(automaticExtrasTotalCents = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderItem(manualUnitPriceCents = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            orderItem(finalUnitPriceCents = -1)
        }
    }

    @Test
    fun `addition validates both persisted monetary values`() {
        assertThrows(IllegalArgumentException::class.java) {
            addition(listedPriceCents = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            addition(chargedPriceCents = -1)
        }
    }

    @Test
    fun `created and modifier order values are preserved exactly`() {
        assertEquals(42, orderItem(createdSequence = 42).createdSequence)
        assertEquals(7, addition(displayOrder = 7).displayOrder)
        assertTrue(OrderItemRemovalEntity::class.java.declaredFields.none { it.name.contains("price", true) })
    }

    private fun orderItem(
        productId: Long? = 10,
        quantity: Int = 1,
        baseUnitPriceCents: Long = 700,
        automaticExtrasTotalCents: Long = 0,
        manualUnitPriceCents: Long? = null,
        finalUnitPriceCents: Long = 700,
        createdSequence: Int = 1,
    ): OrderItemEntity = OrderItemEntity(
        id = "order-item-id",
        orderId = "order-id",
        productId = productId,
        productNameSnapshot = "Margherita",
        productPrintedNameSnapshot = "MARGHERITA",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = quantity,
        baseUnitPriceCents = baseUnitPriceCents,
        automaticExtrasTotalCents = automaticExtrasTotalCents,
        manualUnitPriceCents = manualUnitPriceCents,
        finalUnitPriceCents = finalUnitPriceCents,
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = createdSequence,
    )

    private fun addition(
        listedPriceCents: Long = 200,
        chargedPriceCents: Long = 200,
        displayOrder: Int = 1,
    ): OrderItemAdditionEntity = OrderItemAdditionEntity(
        id = "order-item-addition-id",
        orderItemId = "order-item-id",
        additionId = 20,
        additionNameSnapshot = "Prosciutto",
        additionPrintedNameSnapshot = "PROSCIUTTO",
        listedPriceCents = listedPriceCents,
        chargedPriceCents = chargedPriceCents,
        displayOrder = displayOrder,
    )
}
