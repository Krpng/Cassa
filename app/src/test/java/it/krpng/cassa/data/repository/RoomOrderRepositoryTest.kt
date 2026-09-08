package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.data.database.relation.OrderWithItems
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOrderRepositoryTest {
    @Test
    fun `create draft uses one logical clock instant and persists empty unnumbered state`() = runTest {
        val dao = FakeOrderDao(insertResult = 1)
        val clock = CountingClock(FIXED_NOW)
        val repository = RoomOrderRepository(dao, clock)

        val result = repository.createDraft()

        assertTrue(result is CreateDraftResult.Created)
        val created = (result as CreateDraftResult.Created).draft
        UUID.fromString(created.id)
        assertEquals(OrderStatus.DRAFT, created.status)
        assertEquals(FIXED_NOW, created.createdAt)
        assertEquals(FIXED_NOW, created.updatedAt)
        assertEquals(1, clock.calls)
        assertNull(created.displayNumber)
        assertNull(created.numberingMode)
        assertNull(created.numberingCycle)
        assertNull(created.businessDate)
        assertNull(created.acceptedAt)
        assertEquals(0L, created.total.cents)
        assertNull(created.generalNote)
        assertNull(created.sourceOrderId)
        assertTrue(created.items.isEmpty())

        val entity = requireNotNull(dao.insertedDraft)
        assertEquals(created.id, entity.id)
        assertEquals(1, entity.draftSlot)
        assertEquals(FIXED_NOW.toEpochMilli(), entity.createdAt)
        assertEquals(entity.createdAt, entity.updatedAt)
        assertEquals(0L, entity.totalCents)
    }

    @Test
    fun `create draft reports explicit conflict when unique slot rejects insert`() = runTest {
        val repository = RoomOrderRepository(
            orderDao = FakeOrderDao(insertResult = -1),
            clockProvider = CountingClock(FIXED_NOW),
        )

        assertSame(CreateDraftResult.AlreadyExists, repository.createDraft())
    }

    @Test
    fun `get and observe active draft map the Room aggregate`() = runTest {
        val fullOrder = fullDraft()
        val repository = RoomOrderRepository(
            orderDao = FakeOrderDao(activeDraft = fullOrder),
            clockProvider = CountingClock(FIXED_NOW),
        )

        assertEquals("draft-id", repository.getActiveDraft()?.id)
        assertEquals("draft-id", repository.observeActiveDraft().first()?.id)
    }

    @Test
    fun `observe by id delegates the requested aggregate and maps updates`() = runTest {
        val fullOrder = fullDraft()
        val dao = FakeOrderDao(observedOrder = fullOrder)
        val repository = RoomOrderRepository(dao, CountingClock(FIXED_NOW))

        assertEquals("draft-id", repository.observeById("draft-id").first()?.id)
        assertEquals("draft-id", dao.observedOrderId)
    }

    @Test
    fun `get and observe active draft preserve absence as null`() = runTest {
        val repository = RoomOrderRepository(
            orderDao = FakeOrderDao(activeDraft = null),
            clockProvider = CountingClock(FIXED_NOW),
        )

        assertNull(repository.getActiveDraft())
        assertNull(repository.observeActiveDraft().first())
    }

    @Test
    fun `delete draft reports deleted or not draft without widening the operation`() = runTest {
        val deletedDao = FakeOrderDao(deleteResult = 1)
        val protectedDao = FakeOrderDao(deleteResult = 0)

        assertSame(
            DeleteDraftResult.Deleted,
            RoomOrderRepository(deletedDao, CountingClock(FIXED_NOW)).deleteDraft("draft-id"),
        )
        assertEquals("draft-id", deletedDao.deletedDraftId)
        assertSame(
            DeleteDraftResult.NotFoundOrNotDraft,
            RoomOrderRepository(protectedDao, CountingClock(FIXED_NOW)).deleteDraft("accepted-id"),
        )
        assertEquals("accepted-id", protectedDao.deletedDraftId)
    }

    @Test
    fun `replace draft delegates one atomic DAO operation and returns the new draft`() = runTest {
        val dao = FakeOrderDao(insertResult = 1, deleteResult = 1)
        val clock = CountingClock(FIXED_NOW)
        val repository = RoomOrderRepository(dao, clock)

        val result = repository.replaceDraft("draft-id")

        assertTrue(result is ReplaceDraftResult.Created)
        val created = (result as ReplaceDraftResult.Created).draft
        assertEquals("draft-id", dao.deletedDraftId)
        assertEquals(created.id, dao.insertedDraft?.id)
        assertEquals(FIXED_NOW, created.createdAt)
        assertEquals(FIXED_NOW, created.updatedAt)
        assertEquals(1, clock.calls)
    }

    @Test
    fun `replace draft reports missing original without a replacement`() = runTest {
        val dao = FakeOrderDao(insertResult = 1, deleteResult = 0)
        val repository = RoomOrderRepository(dao, CountingClock(FIXED_NOW))

        assertSame(
            ReplaceDraftResult.OriginalNotFoundOrNotDraft,
            repository.replaceDraft("missing-id"),
        )
        assertNull(dao.insertedDraft)
    }

    @Test
    fun `replace draft maps insert conflict explicitly`() = runTest {
        val dao = FakeOrderDao(insertResult = -1, deleteResult = 1)
        val repository = RoomOrderRepository(dao, CountingClock(FIXED_NOW))

        assertSame(ReplaceDraftResult.Conflict, repository.replaceDraft("draft-id"))
    }

    @Test
    fun `quick add creates a standard item with resolved catalog snapshots`() = runTest {
        val dao = FakeOrderDao(
            fullOrder = fullDraft(),
            activeProduct = productEntity(
                printedName = null,
                priceCents = 750,
                automaticExtrasPricing = false,
            ),
        )
        val clock = CountingClock(FIXED_NOW)
        val repository = RoomOrderRepository(dao, clock)

        val result = repository.quickAddStandard("draft-id", 42)

        assertTrue(result is QuickAddStandardResult.Added)
        val item = requireNotNull(dao.insertedOrderItem)
        assertEquals("draft-id", item.orderId)
        assertEquals(42L, item.productId)
        assertEquals("Margherita", item.productNameSnapshot)
        assertEquals("Margherita", item.productPrintedNameSnapshot)
        assertEquals(ProductCategory.PIZZA, item.categorySnapshot)
        assertEquals(1, item.quantity)
        assertEquals(750L, item.baseUnitPriceCents)
        assertEquals(0L, item.automaticExtrasTotalCents)
        assertNull(item.manualUnitPriceCents)
        assertEquals(750L, item.finalUnitPriceCents)
        assertFalse(item.automaticExtrasPricingSnapshot)
        assertNull(item.note)
        assertEquals(1, item.createdSequence)
        assertEquals(FIXED_NOW.toEpochMilli(), dao.updatedDraftTimestamp)
        assertEquals(1, clock.calls)
    }

    @Test
    fun `quick add merges a standard line through the approved policy`() = runTest {
        val existing = orderItemEntity(quantity = 1)
        val dao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(OrderItemWithModifiers(existing, emptyList(), emptyList())),
            ),
            activeProduct = productEntity(),
        )
        val repository = RoomOrderRepository(dao, CountingClock(FIXED_NOW))

        val result = repository.quickAddStandard("draft-id", 42)

        assertEquals(QuickAddStandardResult.Merged("item-id", 2), result)
        assertEquals(2, dao.updatedOrderItem?.quantity)
        assertNull(dao.insertedOrderItem)
    }

    @Test
    fun `quick add rejects accepted missing and unavailable records before writing`() = runTest {
        val acceptedDao = FakeOrderDao(
            fullOrder = fullDraft().copy(
                order = fullDraft().order.copy(
                    status = OrderStatus.ACCEPTED,
                    draftSlot = null,
                ),
            ),
            activeProduct = productEntity(),
        )
        val missingOrderDao = FakeOrderDao(activeProduct = productEntity())
        val unavailableProductDao = FakeOrderDao(fullOrder = fullDraft())

        assertSame(
            QuickAddStandardResult.OrderNotEditable,
            RoomOrderRepository(acceptedDao, CountingClock(FIXED_NOW))
                .quickAddStandard("accepted-id", 42),
        )
        assertSame(
            QuickAddStandardResult.OrderNotFound,
            RoomOrderRepository(missingOrderDao, CountingClock(FIXED_NOW))
                .quickAddStandard("missing-id", 42),
        )
        assertSame(
            QuickAddStandardResult.ProductUnavailable,
            RoomOrderRepository(unavailableProductDao, CountingClock(FIXED_NOW))
                .quickAddStandard("draft-id", 42),
        )
        assertNull(acceptedDao.insertedOrderItem)
        assertNull(missingOrderDao.insertedOrderItem)
        assertNull(unavailableProductDao.insertedOrderItem)
    }

    private class CountingClock(
        private val instant: Instant,
    ) : ClockProvider {
        var calls: Int = 0

        override fun now(): Instant {
            calls += 1
            return instant
        }
    }

    private class FakeOrderDao(
        private val activeDraft: FullOrder? = null,
        private val observedOrder: FullOrder? = null,
        private val insertResult: Long = 1,
        private val deleteResult: Int = 0,
        private val fullOrder: FullOrder? = null,
        private val activeProduct: ProductEntity? = null,
    ) : OrderDao {
        var insertedDraft: OrderEntity? = null
        var deletedDraftId: String? = null
        var observedOrderId: String? = null
        var insertedOrderItem: OrderItemEntity? = null
        var updatedOrderItem: OrderItemEntity? = null
        var updatedDraftTimestamp: Long? = null

        override suspend fun getWithItems(orderId: String): OrderWithItems? = null

        override suspend fun getFullOrder(orderId: String): FullOrder? = fullOrder

        override fun observeFullOrder(orderId: String): Flow<FullOrder?> {
            observedOrderId = orderId
            return flowOf(observedOrder)
        }

        override fun observeActiveDraft(): Flow<FullOrder?> = flowOf(activeDraft)

        override suspend fun getActiveDraft(): FullOrder? = activeDraft

        override suspend fun insertDraft(order: OrderEntity): Long {
            insertedDraft = order
            return insertResult
        }

        override suspend fun getActiveProductForQuickAdd(productId: Long): ProductEntity? =
            activeProduct?.takeIf { product -> product.id == productId && product.active }

        override suspend fun insertOrderItem(item: OrderItemEntity) {
            insertedOrderItem = item
        }

        override suspend fun updateOrderItem(item: OrderItemEntity): Int {
            updatedOrderItem = item
            return 1
        }

        override suspend fun updateDraftTimestamp(orderId: String, updatedAt: Long): Int {
            updatedDraftTimestamp = updatedAt
            return 1
        }

        override suspend fun deleteDraft(orderId: String): Int {
            deletedDraftId = orderId
            return deleteResult
        }
    }

    private fun fullDraft(
        items: List<OrderItemWithModifiers> = emptyList(),
    ): FullOrder = FullOrder(
        order = OrderEntity(
            id = "draft-id",
            status = OrderStatus.DRAFT,
            draftSlot = 1,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = FIXED_NOW.toEpochMilli(),
            updatedAt = FIXED_NOW.toEpochMilli(),
            acceptedAt = null,
            totalCents = 0,
            generalNote = null,
            sourceOrderId = null,
        ),
        items = items,
    )

    private fun productEntity(
        printedName: String? = "MARGHERITA",
        priceCents: Long = 700,
        automaticExtrasPricing: Boolean = true,
    ): ProductEntity = ProductEntity(
        id = 42,
        name = "Margherita",
        normalizedName = "margherita",
        printedName = printedName,
        category = ProductCategory.PIZZA,
        priceCents = priceCents,
        automaticExtrasPricing = automaticExtrasPricing,
        active = true,
        createdAt = 1_000,
        updatedAt = 2_000,
    )

    private fun orderItemEntity(quantity: Int): OrderItemEntity = OrderItemEntity(
        id = "item-id",
        orderId = "draft-id",
        productId = 42,
        productNameSnapshot = "Margherita",
        productPrintedNameSnapshot = "MARGHERITA",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = quantity,
        baseUnitPriceCents = 700,
        automaticExtrasTotalCents = 0,
        manualUnitPriceCents = null,
        finalUnitPriceCents = 700,
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = 1,
    )

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
    }
}
