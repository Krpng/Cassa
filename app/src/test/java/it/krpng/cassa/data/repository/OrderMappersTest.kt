package it.krpng.cassa.data.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderMappersTest {
    @Test
    fun `full order maps snapshots money dates and deterministic child order`() {
        val databaseModel = fullOrder(
            items = listOf(
                itemWithModifiers(id = "item-b", createdSequence = 2),
                itemWithModifiers(
                    id = "item-a",
                    createdSequence = 1,
                    additions = listOf(
                        addition(id = "addition-b", displayOrder = 2),
                        addition(id = "addition-a", displayOrder = 1),
                    ),
                    removals = listOf(
                        removal(id = "removal-b", displayOrder = 2),
                        removal(id = "removal-a", displayOrder = 1),
                    ),
                ),
            ),
        )

        val order = databaseModel.toDomain()

        assertEquals(OrderStatus.ACCEPTED, order.status)
        assertEquals("001", order.displayNumber)
        assertEquals(NumberingMode.SEQUENTIAL, order.numberingMode)
        assertEquals(LocalDate.of(2026, 9, 4), order.businessDate)
        assertEquals(Instant.ofEpochMilli(1_000), order.createdAt)
        assertEquals(Instant.ofEpochMilli(3_000), order.acceptedAt)
        assertEquals(Money.ofCents(2_200), order.total)
        assertEquals(listOf("item-a", "item-b"), order.items.map { it.id })

        val firstItem = order.items.first()
        assertEquals(Money.ofCents(700), firstItem.baseUnitPrice)
        assertEquals(Money.ofCents(200), firstItem.automaticExtrasTotal)
        assertEquals(Money.ofCents(1_000), firstItem.manualUnitPrice)
        assertEquals(Money.ofCents(1_000), firstItem.finalUnitPrice)
        assertEquals(listOf("addition-a", "addition-b"), firstItem.additions.map { it.id })
        assertEquals(listOf("removal-a", "removal-b"), firstItem.removals.map { it.id })
        assertEquals(Money.ZERO, firstItem.additions.first().chargedPrice)
    }

    @Test
    fun `order domain maps back to complete entity graph and derives accepted draft slot`() {
        val order = fullOrder().toDomain()

        val databaseModel = order.toDatabaseModel()

        assertEquals(order.id, databaseModel.order.id)
        assertNull(databaseModel.order.draftSlot)
        assertEquals(order.businessDate.toString(), databaseModel.order.businessDate)
        assertEquals(order.total.cents, databaseModel.order.totalCents)
        assertEquals(order.acceptedAt?.toEpochMilli(), databaseModel.order.acceptedAt)

        val item = databaseModel.items.single()
        assertEquals(order.id, item.item.orderId)
        assertEquals(order.items.single().baseUnitPrice.cents, item.item.baseUnitPriceCents)
        assertEquals(order.items.single().manualUnitPrice?.cents, item.item.manualUnitPriceCents)
        assertEquals(item.item.id, item.additions.single().orderItemId)
        assertEquals(item.item.id, item.removals.single().orderItemId)
    }

    @Test
    fun `draft mapping restores reserved singleton slot without inventing acceptance data`() {
        val order = fullOrder(
            order = orderEntity(
                status = OrderStatus.DRAFT,
                draftSlot = 1,
                displayNumber = null,
                numberingMode = null,
                businessDate = null,
                acceptedAt = null,
            ),
        ).toDomain()

        val entity = order.toDatabaseModel().order

        assertEquals(1, entity.draftSlot)
        assertNull(entity.displayNumber)
        assertNull(entity.numberingMode)
        assertNull(entity.businessDate)
        assertNull(entity.acceptedAt)
    }

    private fun fullOrder(
        order: OrderEntity = orderEntity(),
        items: List<OrderItemWithModifiers> = listOf(itemWithModifiers()),
    ): FullOrder = FullOrder(order = order, items = items)

    private fun orderEntity(
        status: OrderStatus = OrderStatus.ACCEPTED,
        draftSlot: Int? = null,
        displayNumber: String? = "001",
        numberingMode: NumberingMode? = NumberingMode.SEQUENTIAL,
        businessDate: String? = "2026-09-04",
        acceptedAt: Long? = 3_000,
    ): OrderEntity = OrderEntity(
        id = "order-id",
        status = status,
        draftSlot = draftSlot,
        displayNumber = displayNumber,
        numberingMode = numberingMode,
        numberingCycle = null,
        businessDate = businessDate,
        createdAt = 1_000,
        updatedAt = 2_000,
        acceptedAt = acceptedAt,
        totalCents = 2_200,
        generalNote = "Senza fretta",
        sourceOrderId = null,
    )

    private fun itemWithModifiers(
        id: String = "item-id",
        createdSequence: Int = 1,
        additions: List<OrderItemAdditionEntity> = listOf(addition()),
        removals: List<OrderItemRemovalEntity> = listOf(removal()),
    ): OrderItemWithModifiers = OrderItemWithModifiers(
        item = OrderItemEntity(
            id = id,
            orderId = "order-id",
            productId = null,
            productNameSnapshot = "Margherita",
            productPrintedNameSnapshot = "MARGHERITA",
            categorySnapshot = ProductCategory.PIZZA,
            quantity = 2,
            baseUnitPriceCents = 700,
            automaticExtrasTotalCents = 200,
            manualUnitPriceCents = 1_000,
            finalUnitPriceCents = 1_000,
            automaticExtrasPricingSnapshot = true,
            note = "Ben cotta",
            createdSequence = createdSequence,
        ),
        additions = additions.map { it.copy(orderItemId = id) },
        removals = removals.map { it.copy(orderItemId = id) },
    )

    private fun addition(
        id: String = "addition-id",
        displayOrder: Int = 1,
    ): OrderItemAdditionEntity = OrderItemAdditionEntity(
        id = id,
        orderItemId = "item-id",
        additionId = null,
        additionNameSnapshot = "Prosciutto",
        additionPrintedNameSnapshot = "PROSCIUTTO",
        listedPriceCents = 200,
        chargedPriceCents = 0,
        displayOrder = displayOrder,
    )

    private fun removal(
        id: String = "removal-id",
        displayOrder: Int = 1,
    ): OrderItemRemovalEntity = OrderItemRemovalEntity(
        id = id,
        orderItemId = "item-id",
        ingredientId = null,
        ingredientNameSnapshot = "Mozzarella",
        displayOrder = displayOrder,
    )
}
