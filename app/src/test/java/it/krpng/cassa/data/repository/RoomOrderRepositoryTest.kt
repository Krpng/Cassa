package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.data.database.relation.OrderWithItems
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
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

    @Test
    fun `generic item update preserves snapshots and writes item plus timestamp atomically`() =
        runTest {
            val original = orderItemEntity(quantity = 2).copy(
                automaticExtrasTotalCents = 100,
                finalUnitPriceCents = 800,
                note = "Originale",
            )
            val dao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(original, emptyList(), emptyList())),
                ),
            )
            val clock = CountingClock(FIXED_NOW)
            val repository = RoomOrderRepository(dao, clock)

            val result = repository.updateOrderItem(
                orderId = "draft-id",
                orderItemId = "item-id",
                quantity = 3,
                note = "  Senza sale  ",
                manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(600),
            )

            assertSame(UpdateOrderItemResult.Updated, result)
            val updated = requireNotNull(dao.updatedOrderItem)
            assertEquals(3, updated.quantity)
            assertEquals("Senza sale", updated.note)
            assertEquals(600L, updated.manualUnitPriceCents)
            assertEquals(600L, updated.finalUnitPriceCents)
            assertEquals(original.productId, updated.productId)
            assertEquals(original.productNameSnapshot, updated.productNameSnapshot)
            assertEquals(original.productPrintedNameSnapshot, updated.productPrintedNameSnapshot)
            assertEquals(original.categorySnapshot, updated.categorySnapshot)
            assertEquals(original.baseUnitPriceCents, updated.baseUnitPriceCents)
            assertEquals(
                original.automaticExtrasPricingSnapshot,
                updated.automaticExtrasPricingSnapshot,
            )
            assertEquals(FIXED_NOW.toEpochMilli(), dao.updatedDraftTimestamp)
            assertEquals(1, clock.calls)
        }

    @Test
    fun `generic item update rejects wrong order item accepted order and invalid quantity`() =
        runTest {
            val item = orderItemEntity(quantity = 1)
            val draftDao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(item, emptyList(), emptyList())),
                ),
            )
            val acceptedDao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(item, emptyList(), emptyList())),
                ).copy(
                    order = fullDraft().order.copy(
                        status = OrderStatus.ACCEPTED,
                        draftSlot = null,
                    ),
                ),
            )

            assertSame(
                UpdateOrderItemResult.ItemNotFound,
                RoomOrderRepository(draftDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    "draft-id",
                    "other-item",
                    1,
                    null,
                    null,
                ),
            )
            assertSame(
                UpdateOrderItemResult.OrderNotEditable,
                RoomOrderRepository(acceptedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    "draft-id",
                    "item-id",
                    1,
                    null,
                    null,
                ),
            )
            assertSame(
                UpdateOrderItemResult.InvalidQuantity,
                RoomOrderRepository(draftDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    "draft-id",
                    "item-id",
                    0,
                    null,
                    null,
                ),
            )
            assertNull(draftDao.updatedOrderItem)
            assertNull(acceptedDao.updatedOrderItem)
        }

    @Test
    fun `generic item update maps write conflict and monetary overflow without timestamp write`() =
        runTest {
            val item = orderItemEntity(quantity = 1)
            val conflictDao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(item, emptyList(), emptyList())),
                ),
                updateOrderItemResult = 0,
            )
            val conflictClock = CountingClock(FIXED_NOW)

            assertSame(
                UpdateOrderItemResult.PersistenceFailure,
                RoomOrderRepository(conflictDao, conflictClock).updateOrderItem(
                    "draft-id",
                    "item-id",
                    1,
                    null,
                    null,
                ),
            )
            assertEquals(0, conflictClock.calls)
            assertNull(conflictDao.updatedDraftTimestamp)

            val overflowDao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(item, emptyList(), emptyList())),
                ),
            )
            assertSame(
                UpdateOrderItemResult.AmountOverflow,
                RoomOrderRepository(overflowDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    "draft-id",
                    "item-id",
                    2,
                    null,
                    it.krpng.cassa.core.money.Money.ofCents(Long.MAX_VALUE),
                ),
            )
            assertNull(overflowDao.updatedOrderItem)
            assertNull(overflowDao.updatedDraftTimestamp)
        }

    @Test
    fun `pizza additions persist ordered snapshots including zero price and recalculate item`() =
        runTest {
            val item = orderItemEntity(quantity = 1)
            val dao = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(OrderItemWithModifiers(item, emptyList(), emptyList())),
                ),
                activeAdditions = listOf(
                    additionEntity(10, "Provola", null, 150),
                    additionEntity(11, "Basilico", "BASILICO", 0),
                ),
            )
            val clock = CountingClock(FIXED_NOW)

            val result = RoomOrderRepository(dao, clock).updateOrderItem(
                orderId = "draft-id",
                orderItemId = "item-id",
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(10, 11),
            )

            assertSame(UpdateOrderItemResult.Updated, result)
            assertEquals(2, dao.insertedOrderItemAdditions.size)
            val provola = dao.insertedOrderItemAdditions[0]
            assertEquals(10L, provola.additionId)
            assertEquals("Provola", provola.additionNameSnapshot)
            assertEquals("Provola", provola.additionPrintedNameSnapshot)
            assertEquals(150L, provola.listedPriceCents)
            assertEquals(150L, provola.chargedPriceCents)
            assertEquals(0, provola.displayOrder)
            val basilico = dao.insertedOrderItemAdditions[1]
            assertEquals(0L, basilico.listedPriceCents)
            assertEquals(0L, basilico.chargedPriceCents)
            assertEquals(1, basilico.displayOrder)
            assertEquals(150L, dao.updatedOrderItem?.automaticExtrasTotalCents)
            assertEquals(850L, dao.updatedOrderItem?.finalUnitPriceCents)
            assertEquals(1, clock.calls)
        }

    @Test
    fun `pizza addition selection is idempotent and deselection removes only relation`() = runTest {
        val relation = OrderItemAdditionEntity(
            id = "relation-id",
            orderItemId = "item-id",
            additionId = 10,
            additionNameSnapshot = "Provola storica",
            additionPrintedNameSnapshot = "PROVOLA STORICA",
            listedPriceCents = 150,
            chargedPriceCents = 150,
            displayOrder = 3,
        )
        val item = orderItemEntity(quantity = 1).copy(
            automaticExtrasTotalCents = 150,
            finalUnitPriceCents = 600,
            manualUnitPriceCents = 600,
        )
        val unchangedDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(OrderItemWithModifiers(item, listOf(relation), emptyList())),
            ),
        )

        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(unchangedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                1,
                null,
                it.krpng.cassa.core.money.Money.ofCents(600),
                listOf(10),
            ),
        )
        assertTrue(unchangedDao.insertedOrderItemAdditions.isEmpty())
        assertTrue(unchangedDao.deletedOrderItemAdditionIds.isEmpty())
        assertEquals(600L, unchangedDao.updatedOrderItem?.finalUnitPriceCents)

        val removeDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(OrderItemWithModifiers(item, listOf(relation), emptyList())),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(removeDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                1,
                null,
                null,
                emptyList(),
            ),
        )
        assertEquals(listOf("relation-id"), removeDao.deletedOrderItemAdditionIds)
        assertEquals(0L, removeDao.updatedOrderItem?.automaticExtrasTotalCents)
        assertEquals(700L, removeDao.updatedOrderItem?.finalUnitPriceCents)
    }

    @Test
    fun `disabled automatic extras persist listed snapshots with zero charge and preserve manual price`() =
        runTest {
            fun dao() = FakeOrderDao(
                fullOrder = fullDraft(
                    items = listOf(
                        OrderItemWithModifiers(
                            orderItemEntity(1).copy(automaticExtrasPricingSnapshot = false),
                            emptyList(),
                            listOf(
                                OrderItemRemovalEntity(
                                    id = "removal-relation",
                                    orderItemId = "item-id",
                                    ingredientId = 20,
                                    ingredientNameSnapshot = "Mozzarella",
                                    displayOrder = 0,
                                ),
                            ),
                        ),
                    ),
                ),
                activeAdditions = listOf(
                    additionEntity(10, "Provola", null, 150),
                    additionEntity(11, "Basilico", null, 0),
                ),
            )

            val automaticDao = dao()
            assertSame(
                UpdateOrderItemResult.Updated,
                RoomOrderRepository(automaticDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    orderId = "draft-id",
                    orderItemId = "item-id",
                    quantity = 1,
                    note = null,
                    manualUnitPrice = null,
                    selectedAdditionIds = listOf(10, 11),
                    selectedRemovalIngredientIds = listOf(20),
                ),
            )
            assertEquals(listOf(150L, 0L), automaticDao.insertedOrderItemAdditions.map {
                it.listedPriceCents
            })
            assertEquals(listOf(0L, 0L), automaticDao.insertedOrderItemAdditions.map {
                it.chargedPriceCents
            })
            assertEquals(0L, automaticDao.updatedOrderItem?.automaticExtrasTotalCents)
            assertEquals(700L, automaticDao.updatedOrderItem?.finalUnitPriceCents)
            assertTrue(automaticDao.deletedOrderItemRemovalIds.isEmpty())
            assertTrue(automaticDao.insertedOrderItemRemovals.isEmpty())

            val manualDao = dao()
            assertSame(
                UpdateOrderItemResult.Updated,
                RoomOrderRepository(manualDao, CountingClock(FIXED_NOW)).updateOrderItem(
                    orderId = "draft-id",
                    orderItemId = "item-id",
                    quantity = 2,
                    note = null,
                    manualUnitPrice = Money.ofCents(900),
                    selectedAdditionIds = listOf(10),
                    customizationQuantityIntent =
                        CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                ),
            )
            assertEquals(0L, manualDao.updatedOrderItem?.automaticExtrasTotalCents)
            assertEquals(900L, manualDao.updatedOrderItem?.manualUnitPriceCents)
            assertEquals(900L, manualDao.updatedOrderItem?.finalUnitPriceCents)
        }

    @Test
    fun `confirmed customized quantity increase keeps one item and all modifiers`() = runTest {
        val addition = OrderItemAdditionEntity(
            id = "addition-relation",
            orderItemId = "item-id",
            additionId = 10,
            additionNameSnapshot = "Provola",
            additionPrintedNameSnapshot = "PROVOLA",
            listedPriceCents = 150,
            chargedPriceCents = 150,
            displayOrder = 0,
        )
        val removal = OrderItemRemovalEntity(
            id = "removal-relation",
            orderItemId = "item-id",
            ingredientId = 20,
            ingredientNameSnapshot = "Mozzarella",
            displayOrder = 0,
        )
        val item = orderItemEntity(quantity = 1).copy(
            automaticExtrasTotalCents = 150,
            finalUnitPriceCents = 900,
            manualUnitPriceCents = 900,
        )
        val unconfirmedDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(OrderItemWithModifiers(item, listOf(addition), listOf(removal))),
            ),
        )

        assertSame(
            UpdateOrderItemResult.AmbiguousPizzaQuantity,
            RoomOrderRepository(unconfirmedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                orderId = "draft-id",
                orderItemId = "item-id",
                quantity = 2,
                note = null,
                manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(900),
                selectedAdditionIds = listOf(10),
                selectedRemovalIngredientIds = listOf(20),
            ),
        )
        assertNull(unconfirmedDao.updatedOrderItem)

        val confirmedDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(OrderItemWithModifiers(item, listOf(addition), listOf(removal))),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(confirmedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                orderId = "draft-id",
                orderItemId = "item-id",
                quantity = 2,
                note = null,
                manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(900),
                selectedAdditionIds = listOf(10),
                selectedRemovalIngredientIds = listOf(20),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertEquals("item-id", confirmedDao.updatedOrderItem?.id)
        assertEquals(2, confirmedDao.updatedOrderItem?.quantity)
        assertEquals(900L, confirmedDao.updatedOrderItem?.manualUnitPriceCents)
        assertEquals(900L, confirmedDao.updatedOrderItem?.finalUnitPriceCents)
        assertTrue(confirmedDao.insertedOrderItemAdditions.isEmpty())
        assertTrue(confirmedDao.deletedOrderItemAdditionIds.isEmpty())
        assertTrue(confirmedDao.insertedOrderItemRemovals.isEmpty())
        assertTrue(confirmedDao.deletedOrderItemRemovalIds.isEmpty())

        val alreadyCustomized = item.copy(quantity = 2)
        val unchangedDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(
                    OrderItemWithModifiers(alreadyCustomized, listOf(addition), listOf(removal)),
                ),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(unchangedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                2,
                null,
                it.krpng.cassa.core.money.Money.ofCents(900),
                listOf(10),
                listOf(20),
            ),
        )

        val increasedDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(
                    OrderItemWithModifiers(alreadyCustomized, listOf(addition), listOf(removal)),
                ),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(increasedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                3,
                null,
                it.krpng.cassa.core.money.Money.ofCents(900),
                listOf(10),
                listOf(20),
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertEquals("item-id", increasedDao.updatedOrderItem?.id)
        assertEquals(3, increasedDao.updatedOrderItem?.quantity)
        assertEquals(900L, increasedDao.updatedOrderItem?.manualUnitPriceCents)
        assertTrue(increasedDao.insertedOrderItemAdditions.isEmpty())
        assertTrue(increasedDao.insertedOrderItemRemovals.isEmpty())

        val changedModifiersDao = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(
                    OrderItemWithModifiers(alreadyCustomized, listOf(addition), listOf(removal)),
                ),
            ),
        )
        assertSame(
            UpdateOrderItemResult.AmbiguousPizzaQuantity,
            RoomOrderRepository(changedModifiersDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                3,
                null,
                it.krpng.cassa.core.money.Money.ofCents(900),
                emptyList(),
                listOf(20),
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertNull(changedModifiersDao.updatedOrderItem)
    }

    @Test
    fun `standard quantity one can add an addition and increase to two when confirmed`() = runTest {
        fun dao() = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(
                    OrderItemWithModifiers(orderItemEntity(1), emptyList(), emptyList()),
                ),
            ),
            activeAdditions = listOf(additionEntity(10, "Acciughe", null, 150)),
        )

        val unconfirmedDao = dao()
        assertSame(
            UpdateOrderItemResult.AmbiguousPizzaQuantity,
            RoomOrderRepository(unconfirmedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                2,
                null,
                it.krpng.cassa.core.money.Money.ofCents(1_000),
                listOf(10),
            ),
        )
        assertNull(unconfirmedDao.updatedOrderItem)
        assertTrue(unconfirmedDao.insertedOrderItemAdditions.isEmpty())

        val confirmedDao = dao()
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(confirmedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                2,
                null,
                it.krpng.cassa.core.money.Money.ofCents(1_000),
                listOf(10),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertEquals("item-id", confirmedDao.updatedOrderItem?.id)
        assertEquals(2, confirmedDao.updatedOrderItem?.quantity)
        assertEquals(1_000L, confirmedDao.updatedOrderItem?.manualUnitPriceCents)
        assertEquals(1_000L, confirmedDao.updatedOrderItem?.finalUnitPriceCents)
        assertEquals(listOf(10L), confirmedDao.insertedOrderItemAdditions.map { it.additionId })
    }

    @Test
    fun `standard quantity one can add removals and combined modifiers when confirmed`() = runTest {
        fun dao() = FakeOrderDao(
            fullOrder = fullDraft(
                items = listOf(
                    OrderItemWithModifiers(orderItemEntity(1), emptyList(), emptyList()),
                ),
            ),
            activeAdditions = listOf(additionEntity(10, "Acciughe", null, 150)),
            productIngredients = listOf(
                IngredientEntity(20, "Mozzarella", "mozzarella", true),
            ),
        )

        val removalOnlyDao = dao()
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(removalOnlyDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                2,
                null,
                null,
                emptyList(),
                listOf(20),
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertEquals("item-id", removalOnlyDao.updatedOrderItem?.id)
        assertEquals(2, removalOnlyDao.updatedOrderItem?.quantity)
        assertEquals(listOf(20L), removalOnlyDao.insertedOrderItemRemovals.map { it.ingredientId })

        val combinedDao = dao()
        assertSame(
            UpdateOrderItemResult.Updated,
            RoomOrderRepository(combinedDao, CountingClock(FIXED_NOW)).updateOrderItem(
                "draft-id",
                "item-id",
                2,
                null,
                null,
                listOf(10),
                listOf(20),
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertEquals("item-id", combinedDao.updatedOrderItem?.id)
        assertEquals(2, combinedDao.updatedOrderItem?.quantity)
        assertEquals(listOf(10L), combinedDao.insertedOrderItemAdditions.map { it.additionId })
        assertEquals(listOf(20L), combinedDao.insertedOrderItemRemovals.map { it.ingredientId })
    }

    @Test
    fun `pizza addition guards reject non pizza unavailable and ambiguous split`() =
        runTest {
            fun repositoryFor(item: OrderItemEntity, additions: List<AdditionEntity> = emptyList()) =
                RoomOrderRepository(
                    FakeOrderDao(
                        fullOrder = fullDraft(
                            items = listOf(
                                OrderItemWithModifiers(item, emptyList(), emptyList()),
                            ),
                        ),
                        activeAdditions = additions,
                    ),
                    CountingClock(FIXED_NOW),
                )

            assertSame(
                UpdateOrderItemResult.ItemNotPizza,
                repositoryFor(
                    orderItemEntity(1).copy(categorySnapshot = ProductCategory.BIBITA),
                ).updateOrderItem("draft-id", "item-id", 1, null, null, listOf(10)),
            )
            assertSame(
                UpdateOrderItemResult.AdditionUnavailable,
                repositoryFor(orderItemEntity(1)).updateOrderItem(
                    "draft-id",
                    "item-id",
                    1,
                    null,
                    null,
                    listOf(10),
                ),
            )
            assertSame(
                UpdateOrderItemResult.AmbiguousPizzaQuantity,
                repositoryFor(
                    orderItemEntity(2),
                    listOf(additionEntity(10, "Provola", null, 150)),
                ).updateOrderItem("draft-id", "item-id", 2, null, null, listOf(10)),
            )
            assertSame(
                UpdateOrderItemResult.AmbiguousPizzaQuantity,
                repositoryFor(
                    orderItemEntity(2),
                    listOf(additionEntity(10, "Provola", null, 150)),
                ).updateOrderItem(
                    "draft-id",
                    "item-id",
                    2,
                    null,
                    null,
                    listOf(10),
                    customizationQuantityIntent =
                        CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                ),
            )
            assertSame(
                UpdateOrderItemResult.Updated,
                repositoryFor(
                    orderItemEntity(2),
                    listOf(additionEntity(10, "Provola", null, 150)),
                ).updateOrderItem("draft-id", "item-id", 1, null, null, listOf(10)),
            )
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
        private val activeAdditions: List<AdditionEntity> = emptyList(),
        private val productIngredients: List<IngredientEntity> = emptyList(),
        private val updateOrderItemResult: Int = 1,
        private val updateTimestampResult: Int = 1,
    ) : OrderDao {
        var insertedDraft: OrderEntity? = null
        var deletedDraftId: String? = null
        var observedOrderId: String? = null
        var insertedOrderItem: OrderItemEntity? = null
        var updatedOrderItem: OrderItemEntity? = null
        var updatedDraftTimestamp: Long? = null
        val insertedOrderItemAdditions = mutableListOf<OrderItemAdditionEntity>()
        val deletedOrderItemAdditionIds = mutableListOf<String>()
        val insertedOrderItemRemovals = mutableListOf<OrderItemRemovalEntity>()
        val deletedOrderItemRemovalIds = mutableListOf<String>()

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
            return updateOrderItemResult
        }

        override suspend fun getActiveAdditionsByIds(
            additionIds: List<Long>,
        ): List<AdditionEntity> = activeAdditions.filter { it.id in additionIds && it.active }

        override suspend fun insertOrderItemAdditions(
            additions: List<OrderItemAdditionEntity>,
        ) {
            insertedOrderItemAdditions += additions
        }

        override suspend fun deleteOrderItemAdditions(
            orderItemId: String,
            relationIds: List<String>,
        ): Int {
            deletedOrderItemAdditionIds += relationIds
            return relationIds.size
        }

        override suspend fun getProductIngredientsByIds(
            productId: Long,
            ingredientIds: List<Long>,
        ): List<IngredientEntity> = productIngredients.filter { it.id in ingredientIds }

        override suspend fun insertOrderItemRemovals(
            removals: List<OrderItemRemovalEntity>,
        ) {
            insertedOrderItemRemovals += removals
        }

        override suspend fun deleteOrderItemRemovals(
            orderItemId: String,
            relationIds: List<String>,
        ): Int {
            deletedOrderItemRemovalIds += relationIds
            return relationIds.size
        }

        override suspend fun updateDraftTimestamp(orderId: String, updatedAt: Long): Int {
            updatedDraftTimestamp = updatedAt
            return updateTimestampResult
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

    private fun additionEntity(
        id: Long,
        name: String,
        printedName: String?,
        priceCents: Long,
    ): AdditionEntity = AdditionEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        printedName = printedName,
        priceCents = priceCents,
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
