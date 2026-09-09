package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.dao.DraftReplacementConflictException
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOrderRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var repository: RoomOrderRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = FIXED_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createGetObserveAndDeleteDraftUseTheRealSingleSlot() = runBlocking {
        val firstResult = repository.createDraft()
        assertTrue(firstResult is CreateDraftResult.Created)
        val draft = (firstResult as CreateDraftResult.Created).draft

        assertEquals(draft.id, repository.getActiveDraft()?.id)
        assertEquals(draft.id, repository.observeActiveDraft().first()?.id)
        assertSame(CreateDraftResult.AlreadyExists, repository.createDraft())
        assertEquals(listOf(draft.id), allOrderIds())

        assertSame(DeleteDraftResult.Deleted, repository.deleteDraft(draft.id))
        assertNull(repository.getActiveDraft())
        assertNull(repository.observeActiveDraft().first())
    }

    @Test
    fun observeByIdLoadsThePersistedDraftThroughRoom() = runBlocking {
        val created = repository.createDraft() as CreateDraftResult.Created

        val observed = repository.observeById(created.draft.id).first()

        assertEquals(created.draft.id, observed?.id)
        assertEquals(OrderStatus.DRAFT, observed?.status)
    }

    @Test
    fun deleteDraftCannotDeleteAcceptedOrder() = runBlocking {
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)

        assertSame(
            DeleteDraftResult.NotFoundOrNotDraft,
            repository.deleteDraft(accepted.id),
        )
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
    }

    @Test
    fun replaceDraftIsAtomicAndLeavesAcceptedOrdersUntouched() = runBlocking {
        val original = (repository.createDraft() as CreateDraftResult.Created).draft
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)

        val result = repository.replaceDraft(original.id)

        assertTrue(result is ReplaceDraftResult.Created)
        val replacement = (result as ReplaceDraftResult.Created).draft
        assertNull(repository.getById(original.id))
        assertEquals(replacement.id, repository.getActiveDraft()?.id)
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
        assertEquals(listOf(accepted.id, replacement.id).sorted(), allOrderIds().sorted())
    }

    @Test
    fun failedReplacementInsertRollsBackTheDraftDeletion() = runBlocking {
        val original = (repository.createDraft() as CreateDraftResult.Created).draft
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        val collidingReplacement = accepted.copy(
            status = OrderStatus.DRAFT,
            draftSlot = 1,
            displayNumber = null,
            businessDate = null,
            acceptedAt = null,
        )

        try {
            database.orderDao().replaceDraft(original.id, collidingReplacement)
            throw AssertionError("Expected replacement conflict")
        } catch (_: DraftReplacementConflictException) {
            // Expected: Room rolls the transaction back when the replacement insert fails.
        }

        assertEquals(original.id, repository.getActiveDraft()?.id)
        assertEquals(original.id, repository.getById(original.id)?.id)
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
    }

    @Test
    fun quickAddPersistsAndMergesStandardProductsForEveryCategory() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        database.productDao().insert(product(42, "Crocchè", ProductCategory.FRITTURA, 250))
        database.productDao().insert(product(43, "Acqua", ProductCategory.BIBITA, 150))

        repeat(2) { repository.quickAddStandard(draft.id, 41) }
        repeat(2) { repository.quickAddStandard(draft.id, 42) }
        repeat(3) { repository.quickAddStandard(draft.id, 43) }

        val order = requireNotNull(
            repository.observeById(draft.id).first { observed -> observed?.items?.size == 3 },
        )
        assertEquals(3, order.items.size)
        assertEquals(2, order.items.single { it.productId == 41L }.quantity)
        assertEquals(2, order.items.single { it.productId == 42L }.quantity)
        assertEquals(3, order.items.single { it.productId == 43L }.quantity)
        val pizza = order.items.single { it.productId == 41L }
        assertEquals("Margherita", pizza.productNameSnapshot)
        assertEquals("MARGHERITA", pizza.productPrintedNameSnapshot)
        assertEquals(ProductCategory.PIZZA, pizza.categorySnapshot)
        assertEquals(700L, pizza.baseUnitPrice.cents)
        assertEquals(700L, pizza.finalUnitPrice.cents)
        assertTrue(pizza.additions.isEmpty())
        assertTrue(pizza.removals.isEmpty())
        assertEquals(0L, order.total.cents)
    }

    @Test
    fun quickAddPreservesSnapshotsAndRejectsUnavailableOrAcceptedWrites() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        val original = product(41, "Margherita", ProductCategory.PIZZA, 700)
        database.productDao().insert(original)

        assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
        database.productDao().update(
            original.copy(
                name = "Margherita nuova",
                normalizedName = "margherita nuova",
                printedName = "NUOVA",
                priceCents = 900,
                active = false,
            ),
        )

        val persisted = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals("Margherita", persisted.productNameSnapshot)
        assertEquals("MARGHERITA", persisted.productPrintedNameSnapshot)
        assertEquals(700L, persisted.baseUnitPrice.cents)
        assertSame(
            QuickAddStandardResult.ProductUnavailable,
            repository.quickAddStandard(draft.id, 41),
        )
        assertSame(
            QuickAddStandardResult.ProductUnavailable,
            repository.quickAddStandard(draft.id, 999),
        )

        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        database.productDao().insert(product(42, "Acqua", ProductCategory.BIBITA, 150))
        assertSame(
            QuickAddStandardResult.OrderNotEditable,
            repository.quickAddStandard(accepted.id, 42),
        )
        assertTrue(requireNotNull(repository.getById(accepted.id)).items.isEmpty())
    }

    @Test
    fun concurrentQuickAddsSerializeWithoutDuplicateStandardLinesOrLostUpdates() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))

        val results = coroutineScope {
            listOf(
                async { repository.quickAddStandard(draft.id, 41) },
                async { repository.quickAddStandard(draft.id, 41) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is QuickAddStandardResult.Added })
        assertEquals(1, results.count { it is QuickAddStandardResult.Merged })
        val items = requireNotNull(repository.getById(draft.id)).items
        assertEquals(1, items.size)
        assertEquals(2, items.single().quantity)
        assertEquals(listOf(1), items.map { it.createdSequence })
    }

    @Test
    fun genericItemUpdatePersistsThroughRoomAndEmitsTheUpdatedSnapshot() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        repository.quickAddStandard(draft.id, 41)
        val original = requireNotNull(repository.getById(draft.id)).items.single()
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = UPDATED_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )

        val result = repository.updateOrderItem(
            orderId = draft.id,
            orderItemId = original.id,
            quantity = 2,
            note = "  Senza sale  ",
            manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(600),
        )

        assertSame(UpdateOrderItemResult.Updated, result)
        val updatedOrder = requireNotNull(
            repository.observeById(draft.id).first { order ->
                order?.items?.singleOrNull()?.quantity == 2
            },
        )
        val updated = updatedOrder.items.single()
        assertEquals(original.id, updated.id)
        assertEquals(2, updated.quantity)
        assertEquals("Senza sale", updated.note)
        assertEquals(600L, updated.manualUnitPrice?.cents)
        assertEquals(600L, updated.finalUnitPrice.cents)
        assertEquals(original.productNameSnapshot, updated.productNameSnapshot)
        assertEquals(original.productPrintedNameSnapshot, updated.productPrintedNameSnapshot)
        assertEquals(original.categorySnapshot, updated.categorySnapshot)
        assertEquals(original.baseUnitPrice, updated.baseUnitPrice)
        assertEquals(original.automaticExtrasPricingSnapshot, updated.automaticExtrasPricingSnapshot)
        assertEquals(UPDATED_NOW, updatedOrder.updatedAt)
    }

    @Test
    fun genericItemUpdateRejectsWrongItemMissingOrderAndAcceptedOrderInRoom() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        repository.quickAddStandard(draft.id, 41)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id

        assertSame(
            UpdateOrderItemResult.ItemNotFound,
            repository.updateOrderItem(draft.id, "other-item", 1, null, null),
        )
        assertSame(
            UpdateOrderItemResult.OrderNotFound,
            repository.updateOrderItem("missing-order", itemId, 1, null, null),
        )

        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        assertSame(
            UpdateOrderItemResult.OrderNotEditable,
            repository.updateOrderItem(accepted.id, itemId, 1, null, null),
        )
        assertEquals(1, requireNotNull(repository.getById(draft.id)).items.single().quantity)
    }

    @Test
    fun pizzaAdditionsPersistSnapshotsRepriceAndDeselectThroughRealRoom() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val provolaId = database.additionDao().insert(addition("Provola", null, 150))
        val basilicoId = database.additionDao().insert(addition("Basilico", "BASILICO", 0))

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId, basilicoId),
            ),
        )

        val withAdditions = requireNotNull(
            repository.observeById(draft.id).first { order ->
                order?.items?.singleOrNull()?.additions?.size == 2
            },
        )
        val item = withAdditions.items.single()
        assertEquals(listOf(provolaId, basilicoId), item.additions.map { it.additionId })
        assertEquals(listOf(0, 1), item.additions.map { it.displayOrder })
        assertEquals("Provola", item.additions[0].nameSnapshot)
        assertEquals("Provola", item.additions[0].printedNameSnapshot)
        assertEquals(150L, item.additions[0].listedPrice.cents)
        assertEquals(150L, item.additions[0].chargedPrice.cents)
        assertEquals(0L, item.additions[1].listedPrice.cents)
        assertEquals(0L, item.additions[1].chargedPrice.cents)
        assertEquals(150L, item.automaticExtrasTotal.cents)
        assertEquals(850L, item.finalUnitPrice.cents)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                draft.id,
                itemId,
                2,
                null,
                null,
                listOf(provolaId, basilicoId),
            ),
        )
        val increased = requireNotNull(repository.getById(draft.id))
        assertEquals(1, increased.items.size)
        assertEquals(2, increased.items.single().quantity)
        assertEquals(2, increased.items.single().additions.size)

        val originalProvola = requireNotNull(database.additionDao().getById(provolaId))
        database.additionDao().update(
            originalProvola.copy(name = "Provola nuova", printedName = "NUOVA", priceCents = 300),
        )
        val unchangedSnapshot = requireNotNull(repository.getById(draft.id)).items.single().additions[0]
        assertEquals("Provola", unchangedSnapshot.nameSnapshot)
        assertEquals("Provola", unchangedSnapshot.printedNameSnapshot)
        assertEquals(150L, unchangedSnapshot.listedPrice.cents)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(draft.id, itemId, 1, null, null, listOf(basilicoId)),
        )
        val deselected = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(listOf(basilicoId), deselected.additions.map { it.additionId })
        assertEquals(0L, deselected.automaticExtrasTotal.cents)
        assertEquals(700L, deselected.finalUnitPrice.cents)
    }

    @Test
    fun pizzaAdditionMutationRejectsInactiveNonPizzaAcceptedAndWrongTargetsInRoom() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        database.productDao().insert(product(42, "Acqua", ProductCategory.BIBITA, 150))
        repository.quickAddStandard(draft.id, 41)
        repository.quickAddStandard(draft.id, 42)
        val draftItems = requireNotNull(repository.getById(draft.id)).items
        val pizzaId = draftItems.single { it.productId == 41L }.id
        val drinkId = draftItems.single { it.productId == 42L }.id
        val inactiveId = database.additionDao().insert(
            addition("Inattiva", null, 100).copy(active = false),
        )

        assertSame(
            UpdateOrderItemResult.AdditionUnavailable,
            repository.updateOrderItem(draft.id, pizzaId, 1, null, null, listOf(inactiveId)),
        )
        assertSame(
            UpdateOrderItemResult.ItemNotPizza,
            repository.updateOrderItem(draft.id, drinkId, 1, null, null, listOf(inactiveId)),
        )
        assertSame(
            UpdateOrderItemResult.ItemNotFound,
            repository.updateOrderItem(draft.id, "wrong-item", 1, null, null, emptyList()),
        )
        assertSame(
            UpdateOrderItemResult.OrderNotFound,
            repository.updateOrderItem("wrong-order", pizzaId, 1, null, null, emptyList()),
        )

        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        assertSame(
            UpdateOrderItemResult.OrderNotEditable,
            repository.updateOrderItem(accepted.id, pizzaId, 1, null, null, emptyList()),
        )
        assertFalse(requireNotNull(repository.getById(draft.id)).items.single {
            it.id == pizzaId
        }.additions.isNotEmpty())
    }

    private suspend fun allOrderIds(): List<String> = database.query(
        "SELECT id FROM orders ORDER BY id",
        emptyArray(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(0))
            }
        }
    }

    private fun acceptedOrder(): OrderEntity = OrderEntity(
        id = "accepted-id",
        status = OrderStatus.ACCEPTED,
        draftSlot = null,
        displayNumber = "001",
        numberingMode = null,
        numberingCycle = null,
        businessDate = "2026-09-07",
        createdAt = FIXED_NOW.toEpochMilli(),
        updatedAt = FIXED_NOW.toEpochMilli(),
        acceptedAt = FIXED_NOW.toEpochMilli(),
        totalCents = 0,
        generalNote = null,
        sourceOrderId = null,
    )

    private fun product(
        id: Long,
        name: String,
        category: ProductCategory,
        priceCents: Long,
    ): ProductEntity = ProductEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        printedName = name.uppercase(),
        category = category,
        priceCents = priceCents,
        automaticExtrasPricing = true,
        active = true,
        createdAt = FIXED_NOW.toEpochMilli(),
        updatedAt = FIXED_NOW.toEpochMilli(),
    )

    private fun addition(
        name: String,
        printedName: String?,
        priceCents: Long,
    ): AdditionEntity = AdditionEntity(
        name = name,
        normalizedName = name.lowercase(),
        printedName = printedName,
        priceCents = priceCents,
        active = true,
        createdAt = FIXED_NOW.toEpochMilli(),
        updatedAt = FIXED_NOW.toEpochMilli(),
    )

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
        val UPDATED_NOW: Instant = Instant.parse("2026-09-07T11:30:00Z")
    }
}
