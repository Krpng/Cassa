package it.krpng.cassa.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.dao.DraftReplacementConflictException
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
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
    fun separatelyAddedIdenticalCustomizedPizzasRemainTwoRowsInRoom() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        val provolaId = database.additionDao().insert(addition("Provola", null, 150))

        val first = repository.quickAddStandard(draft.id, 41) as QuickAddStandardResult.Added
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = first.orderItemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId),
            ),
        )
        val second = repository.quickAddStandard(draft.id, 41) as QuickAddStandardResult.Added
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = second.orderItemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId),
            ),
        )

        val items = requireNotNull(repository.getById(draft.id)).items
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.id }.toSet().size)
        assertEquals(listOf(1, 2), items.map { it.createdSequence }.sorted())
        items.forEach { item ->
            assertEquals(1, item.quantity)
            assertEquals(listOf(provolaId), item.additions.map { it.additionId })
            assertEquals(850L, item.finalUnitPrice.cents)
        }
    }

    @Test
    fun noteAndManualPriceKeepCustomizedPizzasOutOfStandardAutoMerge() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))

        val noted = repository.quickAddStandard(draft.id, 41) as QuickAddStandardResult.Added
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = noted.orderItemId,
                quantity = 1,
                note = "Ben cotta",
                manualUnitPrice = null,
            ),
        )
        val manual = repository.quickAddStandard(draft.id, 41) as QuickAddStandardResult.Added
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = manual.orderItemId,
                quantity = 1,
                note = null,
                manualUnitPrice = Money.ofCents(1_000),
            ),
        )

        assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
        val items = requireNotNull(repository.getById(draft.id)).items
        assertEquals(3, items.size)
        assertEquals("Ben cotta", items.single { it.id == noted.orderItemId }.note)
        assertEquals(1_000L, items.single { it.id == manual.orderItemId }.manualUnitPrice?.cents)
        assertEquals(
            1,
            items.count { item ->
                item.note == null &&
                    item.manualUnitPrice == null &&
                    item.additions.isEmpty() &&
                    item.removals.isEmpty()
            },
        )
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
            customizationQuantityIntent =
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
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
    fun manualUnitPriceOverridesChangingAutomaticComponentsAndSurvivesRoomReopen() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 600))
        assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val provolaId = database.additionDao().insert(addition("Provola", null, 100))
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(41, mozzarellaId, 0)),
        )
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = UPDATED_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = "Ben cotta",
                manualUnitPrice = Money.ofCents(550),
                selectedAdditionIds = listOf(provolaId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = "Molto ben cotta",
                manualUnitPrice = Money.ofCents(550),
                selectedAdditionIds = listOf(provolaId, acciugheId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 3,
                note = "Molto ben cotta",
                manualUnitPrice = Money.ofCents(550),
                selectedAdditionIds = listOf(provolaId, acciugheId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )

        val persisted = requireNotNull(repository.getById(draft.id))
        val item = persisted.items.single()
        assertEquals(itemId, item.id)
        assertEquals(3, item.quantity)
        assertEquals(250L, item.automaticExtrasTotal.cents)
        assertEquals(550L, item.manualUnitPrice?.cents)
        assertEquals(550L, item.finalUnitPrice.cents)
        assertEquals(1_650L, (item.finalUnitPrice * item.quantity).cents)
        assertEquals(listOf(100L, 150L), item.additions.map { it.chargedPrice.cents })
        assertEquals(listOf(mozzarellaId), item.removals.map { it.ingredientId })
        assertEquals("Molto ben cotta", item.note)
        assertEquals(UPDATED_NOW, persisted.updatedAt)

        val catalogProduct = requireNotNull(database.productDao().getWithIngredients(41)).product
        database.productDao().update(
            catalogProduct.copy(priceCents = 900, automaticExtrasPricing = false),
        )
        val catalogAddition = requireNotNull(database.additionDao().getById(provolaId))
        database.additionDao().update(catalogAddition.copy(priceCents = 400))

        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = RESET_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
        val reopened = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(600L, reopened.baseUnitPrice.cents)
        assertTrue(reopened.automaticExtrasPricingSnapshot)
        assertEquals(listOf(100L, 150L), reopened.additions.map { it.listedPrice.cents })
        assertEquals(550L, reopened.manualUnitPrice?.cents)
        assertEquals(550L, reopened.finalUnitPrice.cents)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 3,
                note = reopened.note,
                manualUnitPrice = Money.ZERO,
                selectedAdditionIds = listOf(provolaId, acciugheId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
            ),
        )
        val zeroOverride = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(0L, zeroOverride.manualUnitPrice?.cents)
        assertEquals(0L, zeroOverride.finalUnitPrice.cents)
        assertEquals(0L, (zeroOverride.finalUnitPrice * zeroOverride.quantity).cents)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 3,
                note = zeroOverride.note,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId, acciugheId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
            ),
        )
        val resetOrder = requireNotNull(repository.getById(draft.id))
        val reset = resetOrder.items.single()
        assertEquals(itemId, reset.id)
        assertEquals(3, reset.quantity)
        assertNull(reset.manualUnitPrice)
        assertEquals(250L, reset.automaticExtrasTotal.cents)
        assertEquals(850L, reset.finalUnitPrice.cents)
        assertEquals(2_550L, (reset.finalUnitPrice * reset.quantity).cents)
        assertEquals(listOf(provolaId, acciugheId), reset.additions.map { it.additionId })
        assertEquals(listOf(mozzarellaId), reset.removals.map { it.ingredientId })
        assertEquals("Molto ben cotta", reset.note)
        assertEquals(RESET_NOW, resetOrder.updatedAt)
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
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 2,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId, basilicoId),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
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
    fun automaticExtrasPricingSnapshotDisablesChargesAndSurvivesCatalogChanges() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(
            product(
                id = 41,
                name = "Pizza senza extra automatici",
                category = ProductCategory.PIZZA,
                priceCents = 800,
                automaticExtrasPricing = false,
            ),
        )
        assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val provolaId = database.additionDao().insert(addition("Provola", null, 100))
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val freeId = database.additionDao().insert(addition("Origano", null, 0))

        val catalogProduct = requireNotNull(database.productDao().getWithIngredients(41)).product
        database.productDao().update(catalogProduct.copy(automaticExtrasPricing = true))

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId, acciugheId, freeId),
            ),
        )
        val priced = requireNotNull(repository.getById(draft.id)).items.single()
        assertFalse(priced.automaticExtrasPricingSnapshot)
        assertEquals(listOf(100L, 150L, 0L), priced.additions.map { it.listedPrice.cents })
        assertEquals(listOf(0L, 0L, 0L), priced.additions.map { it.chargedPrice.cents })
        assertEquals(0L, priced.automaticExtrasTotal.cents)
        assertEquals(800L, priced.finalUnitPrice.cents)

        val catalogAddition = requireNotNull(database.additionDao().getById(provolaId))
        database.additionDao().update(catalogAddition.copy(priceCents = 250))
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = null,
                manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(950),
                selectedAdditionIds = listOf(provolaId, acciugheId, freeId),
            ),
        )
        val manual = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(100L, manual.additions.first().listedPrice.cents)
        assertEquals(0L, manual.additions.first().chargedPrice.cents)
        assertEquals(950L, manual.finalUnitPrice.cents)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(provolaId, acciugheId, freeId),
            ),
        )
        val reset = requireNotNull(repository.getById(draft.id)).items.single()
        assertNull(reset.manualUnitPrice)
        assertEquals(listOf(provolaId, acciugheId, freeId), reset.additions.map { it.additionId })
        assertEquals(listOf(0L, 0L, 0L), reset.additions.map { it.chargedPrice.cents })
        assertEquals(0L, reset.automaticExtrasTotal.cents)
        assertEquals(800L, reset.finalUnitPrice.cents)
    }

    @Test
    fun standardOneNeedsConfirmationAndStandardTwoCanModifyAllWithExplicitScope() =
        runBlocking {
            val draft = (repository.createDraft() as CreateDraftResult.Created).draft
            database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
            database.productDao().insert(product(42, "Marinara", ProductCategory.PIZZA, 600))
            assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
            assertTrue(repository.quickAddStandard(draft.id, 42) is QuickAddStandardResult.Added)
            assertTrue(repository.quickAddStandard(draft.id, 42) is QuickAddStandardResult.Merged)
            val items = requireNotNull(repository.getById(draft.id)).items
            val singleItemId = items.single { it.productId == 41L }.id
            val aggregatedItemId = items.single { it.productId == 42L }.id
            val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
            val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
            database.productDao().insertProductIngredients(
                listOf(ProductIngredientEntity(41, mozzarellaId, 0)),
            )

            assertSame(
                UpdateOrderItemResult.AmbiguousPizzaQuantity,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = singleItemId,
                    quantity = 2,
                    note = null,
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(acciugheId),
                    selectedRemovalIngredientIds = listOf(mozzarellaId),
                ),
            )
            val beforeConfirmation = requireNotNull(repository.getById(draft.id)).items
                .single { it.id == singleItemId }
            assertEquals(1, beforeConfirmation.quantity)
            assertTrue(beforeConfirmation.additions.isEmpty())
            assertTrue(beforeConfirmation.removals.isEmpty())

            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = singleItemId,
                    quantity = 2,
                    note = null,
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(acciugheId),
                    selectedRemovalIngredientIds = listOf(mozzarellaId),
                    customizationQuantityIntent =
                        CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                ),
            )
            val afterConfirmation = requireNotNull(repository.getById(draft.id))
            assertEquals(2, afterConfirmation.items.size)
            val customized = afterConfirmation.items.single { it.id == singleItemId }
            assertEquals(2, customized.quantity)
            assertEquals(listOf(acciugheId), customized.additions.map { it.additionId })
            assertEquals(listOf(mozzarellaId), customized.removals.map { it.ingredientId })
            assertEquals(1_000L, customized.manualUnitPrice?.cents)
            assertEquals(1_000L, customized.finalUnitPrice.cents)

            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = aggregatedItemId,
                    quantity = 2,
                    note = null,
                    manualUnitPrice = null,
                    selectedAdditionIds = listOf(acciugheId),
                    customizationQuantityIntent =
                        CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                ),
            )
            val aggregated = requireNotNull(repository.getById(draft.id)).items
                .single { it.id == aggregatedItemId }
            assertEquals(2, aggregated.quantity)
            assertEquals(listOf(acciugheId), aggregated.additions.map { it.additionId })
            assertEquals(2, requireNotNull(repository.getById(draft.id)).items.size)
        }

    @Test
    fun noteAndZeroManualPriceCustomizedQuantityKeepOneStableRoomItem() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(43, "Margherita", ProductCategory.PIZZA, 700))
        assertTrue(repository.quickAddStandard(draft.id, 43) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id

        assertSame(
            UpdateOrderItemResult.AmbiguousPizzaQuantity,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 2,
                note = "Ben cotta",
                manualUnitPrice = Money.ZERO,
            ),
        )
        val beforeConfirmation = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(itemId, beforeConfirmation.id)
        assertEquals(1, beforeConfirmation.quantity)
        assertNull(beforeConfirmation.note)
        assertNull(beforeConfirmation.manualUnitPrice)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 2,
                note = "Ben cotta",
                manualUnitPrice = Money.ZERO,
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        val customizedTwo = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(itemId, customizedTwo.id)
        assertEquals(2, customizedTwo.quantity)
        assertEquals("Ben cotta", customizedTwo.note)
        assertEquals(0L, customizedTwo.manualUnitPrice?.cents)

        assertSame(
            UpdateOrderItemResult.AmbiguousPizzaQuantity,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 3,
                note = "Ben cotta",
                manualUnitPrice = Money.ZERO,
            ),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 3,
                note = "Ben cotta",
                manualUnitPrice = Money.ZERO,
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        val customizedThree = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(itemId, customizedThree.id)
        assertEquals(3, customizedThree.quantity)
        assertEquals("Ben cotta", customizedThree.note)
        assertEquals(0L, customizedThree.manualUnitPrice?.cents)
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

    @Test
    fun pizzaRemovalsPersistSnapshotsWithoutChangingPriceAndRejectForeignIngredients() =
        runBlocking {
            val draft = (repository.createDraft() as CreateDraftResult.Created).draft
            database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
            assertTrue(repository.quickAddStandard(draft.id, 41) is QuickAddStandardResult.Added)
            val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
            val pomodoroId = database.ingredientDao().insert(ingredient("Pomodoro"))
            val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
            val basilicoId = database.ingredientDao().insert(ingredient("Basilico"))
            database.productDao().insertProductIngredients(
                listOf(
                    ProductIngredientEntity(41, pomodoroId, 0),
                    ProductIngredientEntity(41, mozzarellaId, 1),
                ),
            )
            val provolaId = database.additionDao().insert(addition("Provola", null, 150))
            repository = RoomOrderRepository(
                orderDao = database.orderDao(),
                clockProvider = object : ClockProvider {
                    override fun now(): Instant = UPDATED_NOW
                },
                transactionRunner = RoomDatabaseTransactionRunner(database),
            )

            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = itemId,
                    quantity = 1,
                    note = "Ben cotta",
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(provolaId),
                    selectedRemovalIngredientIds = listOf(mozzarellaId, pomodoroId),
                ),
            )

            val customized = requireNotNull(repository.getById(draft.id)).items.single()
            assertEquals(listOf(mozzarellaId, pomodoroId), customized.removals.map { it.ingredientId })
            assertEquals(listOf(0, 1), customized.removals.map { it.displayOrder })
            assertEquals(listOf("Mozzarella", "Pomodoro"), customized.removals.map { it.nameSnapshot })
            assertEquals(150L, customized.automaticExtrasTotal.cents)
            assertEquals(1_000L, customized.finalUnitPrice.cents)
            assertEquals(1_000L, customized.manualUnitPrice?.cents)
            assertEquals(UPDATED_NOW, requireNotNull(repository.getById(draft.id)).updatedAt)

            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = itemId,
                    quantity = 1,
                    note = "Ben cotta",
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(provolaId),
                    selectedRemovalIngredientIds = listOf(mozzarellaId, pomodoroId),
                ),
            )
            assertEquals(
                2,
                requireNotNull(repository.getById(draft.id)).items.single().removals.size,
            )

            val mozzarella = requireNotNull(database.ingredientDao().getById(mozzarellaId))
            database.ingredientDao().update(
                mozzarella.copy(name = "Fiordilatte", normalizedName = "fiordilatte"),
            )
            assertEquals(
                "Mozzarella",
                requireNotNull(repository.getById(draft.id)).items.single().removals
                    .first { it.ingredientId == mozzarellaId }
                    .nameSnapshot,
            )

            assertSame(
                UpdateOrderItemResult.IngredientNotRemovable,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = itemId,
                    quantity = 1,
                    note = "Ben cotta",
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(provolaId),
                    selectedRemovalIngredientIds = listOf(basilicoId),
                ),
            )
            assertEquals(
                listOf(mozzarellaId, pomodoroId),
                requireNotNull(repository.getById(draft.id)).items.single().removals
                    .map { it.ingredientId },
            )

            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = itemId,
                    quantity = 1,
                    note = "Ben cotta",
                    manualUnitPrice = it.krpng.cassa.core.money.Money.ofCents(1_000),
                    selectedAdditionIds = listOf(provolaId),
                    selectedRemovalIngredientIds = emptyList(),
                ),
            )
            val deselected = requireNotNull(repository.getById(draft.id)).items.single()
            assertTrue(deselected.removals.isEmpty())
            assertEquals(listOf(provolaId), deselected.additions.map { it.additionId })
            assertEquals(1_000L, deselected.manualUnitPrice?.cents)
            assertEquals(1_000L, deselected.finalUnitPrice.cents)

            database.productDao().insert(product(42, "Acqua", ProductCategory.BIBITA, 150))
            assertTrue(repository.quickAddStandard(draft.id, 42) is QuickAddStandardResult.Added)
            val drinkItemId = requireNotNull(repository.getById(draft.id)).items
                .single { it.productId == 42L }
                .id
            assertSame(
                UpdateOrderItemResult.ItemNotPizza,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = drinkItemId,
                    quantity = 1,
                    note = null,
                    manualUnitPrice = null,
                    selectedRemovalIngredientIds = listOf(pomodoroId),
                ),
            )
        }

    @Test
    fun modifyOneSplitsTheAggregatedStandardPizzaAndKeepsTheSourceUntouched() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        database.productDao().insert(product(42, "Crocchè", ProductCategory.FRITTURA, 250))
        repeat(3) { repository.quickAddStandard(draft.id, 41) }
        assertTrue(repository.quickAddStandard(draft.id, 42) is QuickAddStandardResult.Added)
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(41, mozzarellaId, 0)),
        )
        val before = requireNotNull(repository.getById(draft.id))
        val source = before.items.single { it.productId == 41L }
        assertEquals(3, source.quantity)
        assertEquals(1, source.createdSequence)
        assertEquals(2, before.items.single { it.productId == 42L }.createdSequence)

        val result = repository.splitStandardPizzaItem(
            orderId = draft.id,
            orderItemId = source.id,
            note = "Ben cotta",
            manualUnitPrice = null,
            selectedAdditionIds = listOf(acciugheId),
            selectedRemovalIngredientIds = listOf(mozzarellaId),
        )

        assertTrue(result is SplitStandardPizzaItemResult.Split)
        val split = result as SplitStandardPizzaItemResult.Split
        val after = requireNotNull(repository.getById(draft.id))
        assertEquals(3, after.items.size)
        val kept = after.items.single { it.id == source.id }
        val created = after.items.single { it.id == split.newOrderItemId }
        assertTrue(created.id != source.id)

        assertEquals(2, kept.quantity)
        assertEquals(1, created.quantity)
        assertEquals(3, after.items.filter { it.productId == 41L }.sumOf { it.quantity })

        assertTrue(kept.additions.isEmpty())
        assertTrue(kept.removals.isEmpty())
        assertNull(kept.note)
        assertNull(kept.manualUnitPrice)
        assertEquals(700L, kept.finalUnitPrice.cents)
        assertEquals(1, kept.createdSequence)
        assertEquals(2, after.items.single { it.productId == 42L }.createdSequence)
        assertEquals(3, created.createdSequence)
        assertEquals(created.createdSequence, split.newCreatedSequence)
        assertEquals(source.id, split.sourceOrderItemId)
        assertEquals(2, split.sourceQuantity)

        assertEquals(listOf(acciugheId), created.additions.map { it.additionId })
        assertEquals(listOf(mozzarellaId), created.removals.map { it.ingredientId })
        assertEquals("Ben cotta", created.note)

        assertEquals(source.productId, created.productId)
        assertEquals(source.productNameSnapshot, created.productNameSnapshot)
        assertEquals(source.productPrintedNameSnapshot, created.productPrintedNameSnapshot)
        assertEquals(source.categorySnapshot, created.categorySnapshot)
        assertEquals(source.baseUnitPrice.cents, created.baseUnitPrice.cents)
        assertEquals(
            source.automaticExtrasPricingSnapshot,
            created.automaticExtrasPricingSnapshot,
        )

        assertEquals(150L, created.automaticExtrasTotal.cents)
        assertEquals(850L, created.finalUnitPrice.cents)
        assertNull(created.manualUnitPrice)
    }

    @Test
    fun splitRollsBackTheDecrementAndTheNewRowWhenTheTransactionFails() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        repeat(3) { repository.quickAddStandard(draft.id, 41) }
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(41, mozzarellaId, 0)),
        )
        val source = requireNotNull(repository.getById(draft.id)).items.single()
        val failing = RoomOrderRepository(
            orderDao = FailingRemovalOrderDao(database.orderDao()),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = UPDATED_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )

        val result = failing.splitStandardPizzaItem(
            orderId = draft.id,
            orderItemId = source.id,
            note = "Ben cotta",
            manualUnitPrice = null,
            selectedAdditionIds = listOf(acciugheId),
            selectedRemovalIngredientIds = listOf(mozzarellaId),
        )

        assertSame(SplitStandardPizzaItemResult.PersistenceFailure, result)
        val after = requireNotNull(repository.getById(draft.id))
        val unchanged = after.items.single()
        assertEquals(source.id, unchanged.id)
        assertEquals(3, unchanged.quantity)
        assertNull(unchanged.note)
        assertTrue(unchanged.additions.isEmpty())
        assertTrue(unchanged.removals.isEmpty())
        assertEquals(1, countRows("order_items"))
        assertEquals(0, countRows("order_item_additions"))
        assertEquals(0, countRows("order_item_removals"))
        assertEquals(FIXED_NOW.toEpochMilli(), after.updatedAt.toEpochMilli())
    }

    @Test
    fun splitRejectsMissingTargetsAcceptedOrdersAndRowsThatCannotBeSplit() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(41, "Margherita", ProductCategory.PIZZA, 700))
        database.productDao().insert(product(42, "Crocchè", ProductCategory.FRITTURA, 250))
        database.productDao().insert(product(43, "Marinara", ProductCategory.PIZZA, 600))
        repeat(3) { repository.quickAddStandard(draft.id, 41) }
        repeat(3) { repository.quickAddStandard(draft.id, 42) }
        assertTrue(repository.quickAddStandard(draft.id, 43) is QuickAddStandardResult.Added)
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(41, mozzarellaId, 0)),
        )
        val basilicoId = database.ingredientDao().insert(ingredient("Basilico"))
        val items = requireNotNull(repository.getById(draft.id)).items
        val aggregatedPizzaId = items.single { it.productId == 41L }.id
        val aggregatedFritturaId = items.single { it.productId == 42L }.id
        val singlePizzaId = items.single { it.productId == 43L }.id
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        val foreignItemId = "foreign-item-id"
        database.orderDao().insertOrderItem(
            orderItem(id = foreignItemId, orderId = accepted.id, productId = 41),
        )

        suspend fun split(
            orderId: String = draft.id,
            itemId: String = aggregatedPizzaId,
            additionIds: List<Long> = listOf(acciugheId),
            removalIds: List<Long> = emptyList(),
        ) = repository.splitStandardPizzaItem(
            orderId = orderId,
            orderItemId = itemId,
            note = null,
            manualUnitPrice = null,
            selectedAdditionIds = additionIds,
            selectedRemovalIngredientIds = removalIds,
        )

        assertSame(
            SplitStandardPizzaItemResult.OrderNotFound,
            split(orderId = "missing-order-id"),
        )
        assertSame(
            SplitStandardPizzaItemResult.OrderNotEditable,
            split(orderId = accepted.id, itemId = foreignItemId),
        )
        assertSame(SplitStandardPizzaItemResult.ItemNotFound, split(itemId = "missing-item-id"))
        assertSame(SplitStandardPizzaItemResult.ItemNotFound, split(itemId = foreignItemId))
        assertSame(
            SplitStandardPizzaItemResult.ItemNotPizza,
            split(itemId = aggregatedFritturaId),
        )
        assertSame(
            SplitStandardPizzaItemResult.ItemNotAggregated,
            split(itemId = singlePizzaId),
        )
        assertSame(
            SplitStandardPizzaItemResult.CustomizationRequired,
            split(additionIds = emptyList()),
        )
        assertSame(
            SplitStandardPizzaItemResult.AdditionUnavailable,
            split(additionIds = listOf(acciugheId + 999)),
        )
        assertSame(
            SplitStandardPizzaItemResult.IngredientNotRemovable,
            split(additionIds = emptyList(), removalIds = listOf(basilicoId)),
        )

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = singlePizzaId,
                quantity = 2,
                note = null,
                manualUnitPrice = null,
                selectedAdditionIds = listOf(acciugheId),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertSame(
            SplitStandardPizzaItemResult.ItemNotStandard,
            split(itemId = singlePizzaId),
        )

        val after = requireNotNull(repository.getById(draft.id))
        assertEquals(3, after.items.size)
        assertEquals(3, after.items.single { it.id == aggregatedPizzaId }.quantity)
        assertEquals(3, after.items.single { it.id == aggregatedFritturaId }.quantity)
        assertTrue(after.items.single { it.id == aggregatedPizzaId }.additions.isEmpty())
        assertTrue(after.items.single { it.id == aggregatedPizzaId }.removals.isEmpty())
        // The three draft rows plus the row owned by the accepted order, which stays untouched.
        assertEquals(4, countRows("order_items"))
    }

    @Test
    fun repeatedSplitsNeverMergeAndHonourDeferredExtrasAndZeroManualPrice() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(
            product(44, "Margherita", ProductCategory.PIZZA, 700, automaticExtrasPricing = false),
        )
        repeat(4) { repository.quickAddStandard(draft.id, 44) }
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val sourceId = requireNotNull(repository.getById(draft.id)).items.single().id

        val first = repository.splitStandardPizzaItem(
            orderId = draft.id,
            orderItemId = sourceId,
            note = null,
            manualUnitPrice = null,
            selectedAdditionIds = listOf(acciugheId),
        ) as SplitStandardPizzaItemResult.Split
        val second = repository.splitStandardPizzaItem(
            orderId = draft.id,
            orderItemId = sourceId,
            note = null,
            manualUnitPrice = null,
            selectedAdditionIds = listOf(acciugheId),
        ) as SplitStandardPizzaItemResult.Split
        val third = repository.splitStandardPizzaItem(
            orderId = draft.id,
            orderItemId = sourceId,
            note = null,
            manualUnitPrice = Money.ZERO,
            selectedAdditionIds = listOf(acciugheId),
        ) as SplitStandardPizzaItemResult.Split

        val after = requireNotNull(repository.getById(draft.id))
        assertEquals(4, after.items.size)
        assertEquals(4, after.items.sumOf { it.quantity })
        assertEquals(1, after.items.single { it.id == sourceId }.quantity)
        assertEquals(
            listOf(2, 3, 4),
            listOf(first, second, third).map { it.newCreatedSequence },
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            after.items.map { it.createdSequence },
        )

        val firstRow = after.items.single { it.id == first.newOrderItemId }
        val secondRow = after.items.single { it.id == second.newOrderItemId }
        val thirdRow = after.items.single { it.id == third.newOrderItemId }
        assertTrue(firstRow.id != secondRow.id)
        assertEquals(1, firstRow.quantity)
        assertEquals(1, secondRow.quantity)

        assertEquals(150L, firstRow.additions.single().listedPrice.cents)
        assertEquals(0L, firstRow.additions.single().chargedPrice.cents)
        assertEquals(0L, firstRow.automaticExtrasTotal.cents)
        assertEquals(700L, firstRow.finalUnitPrice.cents)

        assertEquals(0L, thirdRow.manualUnitPrice?.cents)
        assertEquals(0L, thirdRow.finalUnitPrice.cents)
    }

    @Test
    fun changeQuantityIncreasesAndDecreasesTheSameStandardRowWithoutRepricing() = runBlocking {
        // ORDER-013 / ORDER-014 / ORDER-023
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(51, "Margherita", ProductCategory.PIZZA, 700))
        repeat(2) { repository.quickAddStandard(draft.id, 51) }
        val before = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(2, before.quantity)

        val clock = MutableClock(UPDATED_NOW)
        val timed = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = clock,
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
        assertSame(
            ChangeQuantityResult.Updated,
            timed.changeQuantity(draft.id, before.id, 3),
        )
        val increased = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(before.id, increased.id)
        assertEquals(3, increased.quantity)
        assertEquals(before.createdSequence, increased.createdSequence)
        assertEquals(before.baseUnitPrice.cents, increased.baseUnitPrice.cents)
        assertEquals(before.automaticExtrasTotal.cents, increased.automaticExtrasTotal.cents)
        assertEquals(before.finalUnitPrice.cents, increased.finalUnitPrice.cents)
        assertNull(increased.manualUnitPrice)
        assertEquals(UPDATED_NOW.toEpochMilli(), requireNotNull(repository.getById(draft.id)).updatedAt.toEpochMilli())

        clock.now = RESET_NOW
        assertSame(
            ChangeQuantityResult.Updated,
            timed.changeQuantity(draft.id, before.id, 1),
        )
        val decreased = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(1, decreased.quantity)
        assertEquals(before.id, decreased.id)
        assertEquals(before.finalUnitPrice.cents, decreased.finalUnitPrice.cents)
        assertEquals(RESET_NOW.toEpochMilli(), requireNotNull(repository.getById(draft.id)).updatedAt.toEpochMilli())
    }

    @Test
    fun changeQuantityRejectsNonPositiveAndLeavesQuantityOneUntouchedWhenAskedToStayAtOne() =
        runBlocking {
            // ORDER-015 (repo rejects quantity<=0; UI keeps minus disabled at 1)
            val draft = (repository.createDraft() as CreateDraftResult.Created).draft
            database.productDao().insert(product(52, "Margherita", ProductCategory.PIZZA, 700))
            assertTrue(repository.quickAddStandard(draft.id, 52) is QuickAddStandardResult.Added)
            val item = requireNotNull(repository.getById(draft.id)).items.single()

            assertSame(
                ChangeQuantityResult.InvalidQuantity,
                repository.changeQuantity(draft.id, item.id, 0),
            )
            assertSame(
                ChangeQuantityResult.InvalidQuantity,
                repository.changeQuantity(draft.id, item.id, -1),
            )
            assertEquals(1, requireNotNull(repository.getById(draft.id)).items.single().quantity)
            assertSame(
                ChangeQuantityResult.Updated,
                repository.changeQuantity(draft.id, item.id, 1),
            )
            assertEquals(1, requireNotNull(repository.getById(draft.id)).items.single().quantity)
        }

    @Test
    fun changeQuantityPreservesCustomizationsAndUnitPriceOnTheSameRow() = runBlocking {
        // ORDER-016
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(53, "Margherita", ProductCategory.PIZZA, 700))
        assertTrue(repository.quickAddStandard(draft.id, 53) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(53, mozzarellaId, 0)),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 2,
                note = "Ben cotta",
                manualUnitPrice = Money.ofCents(1_000),
                selectedAdditionIds = listOf(acciugheId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        val before = requireNotNull(repository.getById(draft.id)).items.single()

        assertSame(
            ChangeQuantityResult.Updated,
            repository.changeQuantity(draft.id, itemId, 3),
        )
        val after = requireNotNull(repository.getById(draft.id)).items.single()
        assertEquals(before.id, after.id)
        assertEquals(3, after.quantity)
        assertEquals(before.createdSequence, after.createdSequence)
        assertEquals(before.note, after.note)
        assertEquals(before.manualUnitPrice?.cents, after.manualUnitPrice?.cents)
        assertEquals(before.finalUnitPrice.cents, after.finalUnitPrice.cents)
        assertEquals(before.automaticExtrasTotal.cents, after.automaticExtrasTotal.cents)
        assertEquals(before.additions.map { it.additionId }, after.additions.map { it.additionId })
        assertEquals(
            before.removals.map { it.ingredientId },
            after.removals.map { it.ingredientId },
        )
        assertEquals(1, requireNotNull(repository.getById(draft.id)).items.size)
    }

    @Test
    fun removeOrderItemDeletesStandardAndCustomRowsWithChildrenWithoutDeletingTheDraft() =
        runBlocking {
            // ORDER-017 / ORDER-018 / ORDER-020 / ORDER-023 / ORDER-024 / ORDER-025
            val draft = (repository.createDraft() as CreateDraftResult.Created).draft
            database.productDao().insert(product(54, "Margherita", ProductCategory.PIZZA, 700))
            database.productDao().insert(product(55, "Crocchè", ProductCategory.FRITTURA, 250))
            assertTrue(repository.quickAddStandard(draft.id, 54) is QuickAddStandardResult.Added)
            assertTrue(repository.quickAddStandard(draft.id, 55) is QuickAddStandardResult.Added)
            val pizzaId = requireNotNull(repository.getById(draft.id)).items
                .single { it.productId == 54L }.id
            val fritturaId = requireNotNull(repository.getById(draft.id)).items
                .single { it.productId == 55L }.id
            val acciugheId = database.additionDao().insert(addition("Acciughe", null, 150))
            val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
            database.productDao().insertProductIngredients(
                listOf(ProductIngredientEntity(54, mozzarellaId, 0)),
            )
            assertSame(
                UpdateOrderItemResult.Updated,
                repository.updateOrderItem(
                    orderId = draft.id,
                    orderItemId = pizzaId,
                    quantity = 1,
                    note = null,
                    manualUnitPrice = null,
                    selectedAdditionIds = listOf(acciugheId),
                    selectedRemovalIngredientIds = listOf(mozzarellaId),
                ),
            )
            assertEquals(1, countRows("order_item_additions"))
            assertEquals(1, countRows("order_item_removals"))

            val clock = MutableClock(UPDATED_NOW)
            val timed = RoomOrderRepository(
                orderDao = database.orderDao(),
                clockProvider = clock,
                transactionRunner = RoomDatabaseTransactionRunner(database),
            )
            assertSame(
                RemoveOrderItemResult.Removed,
                timed.removeOrderItem(draft.id, pizzaId),
            )
            val afterCustomRemoval = requireNotNull(repository.getById(draft.id))
            assertEquals(listOf(fritturaId), afterCustomRemoval.items.map { it.id })
            assertEquals(0, countRows("order_item_additions"))
            assertEquals(0, countRows("order_item_removals"))
            assertEquals(UPDATED_NOW.toEpochMilli(), afterCustomRemoval.updatedAt.toEpochMilli())

            assertSame(
                RemoveOrderItemResult.Removed,
                timed.removeOrderItem(draft.id, fritturaId),
            )
            val emptyDraft = requireNotNull(repository.getById(draft.id))
            assertTrue(emptyDraft.items.isEmpty())
            assertEquals(OrderStatus.DRAFT, emptyDraft.status)
            assertEquals(draft.id, repository.getActiveDraft()?.id)
            assertEquals(0, countRows("order_items"))
            assertEquals(0, countRows("order_item_additions"))
            assertEquals(0, countRows("order_item_removals"))
            assertEquals(
                draft.id,
                repository.observeById(draft.id).first()?.id,
            )
            assertTrue(requireNotNull(repository.observeById(draft.id).first()).items.isEmpty())
        }

    @Test
    fun removeOrderItemRejectsWrongOwnershipAndAcceptedOrders() = runBlocking {
        // ORDER-021 / ORDER-022
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(56, "Margherita", ProductCategory.PIZZA, 700))
        assertTrue(repository.quickAddStandard(draft.id, 56) is QuickAddStandardResult.Added)
        val draftItemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        val foreignItemId = "foreign-remove-id"
        database.orderDao().insertOrderItem(
            orderItem(id = foreignItemId, orderId = accepted.id, productId = 56),
        )

        assertSame(
            RemoveOrderItemResult.ItemNotFound,
            repository.removeOrderItem(draft.id, foreignItemId),
        )
        assertSame(
            ChangeQuantityResult.ItemNotFound,
            repository.changeQuantity(draft.id, foreignItemId, 2),
        )
        assertSame(
            RemoveOrderItemResult.OrderNotEditable,
            repository.removeOrderItem(accepted.id, foreignItemId),
        )
        assertSame(
            ChangeQuantityResult.OrderNotEditable,
            repository.changeQuantity(accepted.id, foreignItemId, 2),
        )
        assertEquals(1, requireNotNull(repository.getById(draft.id)).items.size)
        assertEquals(draftItemId, requireNotNull(repository.getById(draft.id)).items.single().id)
        assertEquals(2, countRows("order_items"))
    }

    @Test
    fun updateGeneralNoteInsertsEditsClearsAndTrimsWithoutMigration() = runBlocking {
        // ORDER-026 / ORDER-027 / ORDER-028 / ORDER-029 / ORDER-030 / ORDER-031 / ORDER-032 / ORDER-033
        val created = repository.createDraft() as CreateDraftResult.Created
        val draft = created.draft
        assertNull(draft.generalNote)
        assertNull(requireNotNull(repository.getById(draft.id)).generalNote)

        val clock = MutableClock(UPDATED_NOW)
        val timed = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = clock,
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )

        assertSame(
            UpdateGeneralNoteResult.Updated,
            timed.updateGeneralNote(draft.id, "  Consegna alle 21  "),
        )
        var loaded = requireNotNull(repository.getById(draft.id))
        assertEquals("Consegna alle 21", loaded.generalNote)
        assertEquals(UPDATED_NOW.toEpochMilli(), loaded.updatedAt.toEpochMilli())

        clock.now = RESET_NOW
        assertSame(
            UpdateGeneralNoteResult.Updated,
            timed.updateGeneralNote(draft.id, "Nuova nota\nseconda riga"),
        )
        loaded = requireNotNull(repository.getById(draft.id))
        assertEquals("Nuova nota\nseconda riga", loaded.generalNote)
        assertEquals(RESET_NOW.toEpochMilli(), loaded.updatedAt.toEpochMilli())

        assertSame(
            UpdateGeneralNoteResult.Updated,
            timed.updateGeneralNote(draft.id, "   "),
        )
        loaded = requireNotNull(repository.getById(draft.id))
        assertNull(loaded.generalNote)
    }

    @Test
    fun updateGeneralNoteRejectsMissingAndAcceptedOrders() = runBlocking {
        // ORDER-034 / ORDER-035
        assertSame(
            UpdateGeneralNoteResult.OrderNotFound,
            repository.updateGeneralNote("missing-id", "Nota"),
        )

        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        assertSame(
            UpdateGeneralNoteResult.OrderNotEditable,
            repository.updateGeneralNote(accepted.id, "Nota"),
        )
        assertNull(requireNotNull(repository.getById(accepted.id)).generalNote)
    }

    @Test
    fun updateGeneralNoteLeavesItemNotesIndependent() = runBlocking {
        // ORDER-036
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(product(61, "Margherita", ProductCategory.PIZZA, 700))
        assertTrue(repository.quickAddStandard(draft.id, 61L) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id

        assertSame(
            UpdateGeneralNoteResult.Updated,
            repository.updateGeneralNote(draft.id, "Consegna alle 21"),
        )
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = "Ben cotta",
                manualUnitPrice = null,
            ),
        )

        var loaded = requireNotNull(repository.getById(draft.id))
        assertEquals("Consegna alle 21", loaded.generalNote)
        assertEquals("Ben cotta", loaded.items.single().note)

        assertSame(
            UpdateGeneralNoteResult.Updated,
            repository.updateGeneralNote(draft.id, null),
        )
        loaded = requireNotNull(repository.getById(draft.id))
        assertNull(loaded.generalNote)
        assertEquals("Ben cotta", loaded.items.single().note)

        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 1,
                note = null,
                manualUnitPrice = null,
            ),
        )
        loaded = requireNotNull(repository.getById(draft.id))
        assertNull(loaded.generalNote)
        assertNull(loaded.items.single().note)
    }

    private class MutableClock(var now: Instant) : ClockProvider {
        override fun now(): Instant = now
    }

    private class FailingRemovalOrderDao(private val delegate: OrderDao) : OrderDao by delegate {
        override suspend fun insertOrderItemRemovals(removals: List<OrderItemRemovalEntity>): Unit =
            throw SQLiteException("removal insert failed")
    }

    private suspend fun countRows(table: String): Int = database.query(
        "SELECT COUNT(*) FROM $table",
        emptyArray(),
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun orderItem(
        id: String,
        orderId: String,
        productId: Long,
    ): OrderItemEntity = OrderItemEntity(
        id = id,
        orderId = orderId,
        productId = productId,
        productNameSnapshot = "Margherita",
        productPrintedNameSnapshot = "MARGHERITA",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = 3,
        baseUnitPriceCents = 700,
        automaticExtrasTotalCents = 0,
        manualUnitPriceCents = null,
        finalUnitPriceCents = 700,
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = 1,
    )

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
        automaticExtrasPricing: Boolean = true,
    ): ProductEntity = ProductEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        printedName = name.uppercase(),
        category = category,
        priceCents = priceCents,
        automaticExtrasPricing = automaticExtrasPricing,
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

    private fun ingredient(name: String): IngredientEntity = IngredientEntity(
        name = name,
        normalizedName = name.lowercase(),
        active = true,
    )

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
        val UPDATED_NOW: Instant = Instant.parse("2026-09-07T11:30:00Z")
        val RESET_NOW: Instant = Instant.parse("2026-09-07T12:45:00Z")
    }
}
