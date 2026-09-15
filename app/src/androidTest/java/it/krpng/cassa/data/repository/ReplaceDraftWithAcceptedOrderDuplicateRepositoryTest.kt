package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplaceDraftWithAcceptedOrderDuplicateRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var repository: RoomOrderRepository
    private val businessDate = LocalDate.parse("2026-09-15")
    private var productId: Long = 0
    private var additionId: Long = 0
    private var ingredientId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
        productId = database.productDao().insert(
            ProductEntity(
                id = 1,
                name = "Margherita",
                normalizedName = "margherita",
                printedName = "MARGHERITA",
                category = ProductCategory.PIZZA,
                priceCents = 700,
                automaticExtrasPricing = true,
                active = true,
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
            ),
        )
        additionId = database.additionDao().insert(
            AdditionEntity(
                id = 9,
                name = "Bufala",
                normalizedName = "bufala",
                printedName = "BUFALA",
                priceCents = 150,
                active = true,
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
            ),
        )
        ingredientId = database.ingredientDao().insert(
            IngredientEntity(
                id = 3,
                name = "Pomodoro",
                normalizedName = "pomodoro",
                active = true,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun arch7T005ToT010ReplaceDeletesOldDraftAndCopiesFaithfully() = runBlocking {
        val sourceId = seedRichAccepted()
        val oldDraft = seedDraftWithChildren(id = "old-draft")
        val sourceBefore = requireNotNull(repository.getById(sourceId))
        val oldRemovals = countRows("order_item_removals")
        assertTrue(oldRemovals >= 2)

        val result = repository.replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId = sourceId,
            currentBusinessDate = businessDate,
            expectedDraftId = oldDraft,
        )
        assertTrue(result is ReplaceDraftWithAcceptedOrderDuplicateResult.Created)
        val newDraftId = (result as ReplaceDraftWithAcceptedOrderDuplicateResult.Created).draftId
        assertNotEquals(oldDraft, newDraftId)
        assertNull(repository.getById(oldDraft))
        assertEquals(0, countRowsForOrder("order_items", oldDraft))
        assertEquals(0, countRowsForOrderItemsChildren("order_item_additions", oldDraft))
        assertEquals(0, countRowsForOrderItemsChildren("order_item_removals", oldDraft))

        val draft = requireNotNull(repository.getById(newDraftId))
        assertEquals(OrderStatus.DRAFT, draft.status)
        assertEquals(sourceId, draft.sourceOrderId)
        assertEquals("Linea 1\n  spaces  ", draft.generalNote)
        assertEquals(Money.ZERO, draft.total)
        assertNull(draft.displayNumber)
        assertNull(draft.acceptedAt)
        assertNull(draft.businessDate)

        val pizza = draft.items.single { it.productNameSnapshot == "Margherita" }
        assertEquals(productId, pizza.productId)
        assertEquals(Money.ofCents(0), pizza.manualUnitPrice)
        assertEquals(listOf(1, 4), draft.items.map { it.createdSequence }.sorted())
        assertEquals(additionId, pizza.additions.single().additionId)
        assertEquals(ingredientId, pizza.removals.single().ingredientId)

        assertEquals(sourceBefore, requireNotNull(repository.getById(sourceId)))
        assertEquals(newDraftId, repository.getActiveDraft()?.id)
    }

    @Test
    fun arch7T010EmptyOldDraftReplaced() = runBlocking {
        val sourceId = seedRichAccepted()
        val emptyId = (repository.createDraft() as CreateDraftResult.Created).draft.id

        val result = repository.replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId = sourceId,
            currentBusinessDate = businessDate,
            expectedDraftId = emptyId,
        )
        assertTrue(result is ReplaceDraftWithAcceptedOrderDuplicateResult.Created)
        val newId = (result as ReplaceDraftWithAcceptedOrderDuplicateResult.Created).draftId
        assertNull(repository.getById(emptyId))
        assertEquals(2, requireNotNull(repository.getById(newId)).items.size)
        assertEquals(newId, repository.getActiveDraft()?.id)
    }

    @Test
    fun arch7T011T012RollbackPreservesOldDraftAfterInsertFailure() = runBlocking {
        val sourceId = seedRichAccepted()
        val oldDraft = seedDraftWithChildren(id = "rollback-draft")
        val oldSnapshot = requireNotNull(repository.getById(oldDraft))
        val ordersBefore = countRows("orders")
        val itemsBefore = countRows("order_items")
        val additionsBefore = countRows("order_item_additions")
        val removalsBefore = countRows("order_item_removals")

        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER abort_new_draft_item
            BEFORE INSERT ON order_items
            BEGIN
              SELECT RAISE(ABORT, 'forced failure after draft delete');
            END;
            """.trimIndent(),
        )
        try {
            val failingRepo = RoomOrderRepository(
                orderDao = database.orderDao(),
                clockProvider = object : ClockProvider {
                    override fun now(): Instant = NOW
                },
                transactionRunner = RoomDatabaseTransactionRunner(database),
            )
            assertEquals(
                ReplaceDraftWithAcceptedOrderDuplicateResult.PersistenceFailure,
                failingRepo.replaceDraftWithAcceptedOrderDuplicate(
                    sourceOrderId = sourceId,
                    currentBusinessDate = businessDate,
                    expectedDraftId = oldDraft,
                ),
            )
        } finally {
            database.openHelper.writableDatabase.execSQL(
                "DROP TRIGGER IF EXISTS abort_new_draft_item",
            )
        }

        assertEquals(ordersBefore, countRows("orders"))
        assertEquals(itemsBefore, countRows("order_items"))
        assertEquals(additionsBefore, countRows("order_item_additions"))
        assertEquals(removalsBefore, countRows("order_item_removals"))
        assertEquals(oldSnapshot, requireNotNull(repository.getById(oldDraft)))
        assertEquals(oldDraft, repository.getActiveDraft()?.id)
        assertEquals(OrderStatus.ACCEPTED, requireNotNull(repository.getById(sourceId)).status)
        assertEquals(
            1,
            countRowsForOrderItemsChildren("order_item_removals", oldDraft),
        )
    }

    @Test
    fun arch7T013StaleSourceAndDraftGuardsZeroWrites() = runBlocking {
        val sourceId = seedRichAccepted()
        val oldDraft = seedDraftWithChildren(id = "guard-draft")
        val ordersBefore = countRows("orders")

        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable,
            repository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = "missing",
                currentBusinessDate = businessDate,
                expectedDraftId = oldDraft,
            ),
        )
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable,
            repository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = oldDraft,
                currentBusinessDate = businessDate,
                expectedDraftId = oldDraft,
            ),
        )

        database.orderDao().deleteRemovalsForOrder(oldDraft)
        assertEquals(
            it.krpng.cassa.domain.repository.DeleteDraftResult.Deleted,
            repository.deleteDraft(oldDraft),
        )
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateResult.DraftMissing,
            repository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = sourceId,
                currentBusinessDate = businessDate,
                expectedDraftId = oldDraft,
            ),
        )

        val other = (repository.createDraft() as CreateDraftResult.Created).draft.id
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateResult.DraftChanged,
            repository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = sourceId,
                currentBusinessDate = businessDate,
                expectedDraftId = "stale-expected",
            ),
        )
        assertEquals(other, repository.getActiveDraft()?.id)
        assertNotNull(repository.getById(sourceId))
        assertTrue(countRows("orders") >= ordersBefore)
    }

    @Test
    fun arch7T015NoCatalogRepriceOnReplace() = runBlocking {
        val sourceId = seedRichAccepted()
        database.productDao().update(
            ProductEntity(
                id = productId,
                name = "Margherita",
                normalizedName = "margherita",
                printedName = "MARGHERITA",
                category = ProductCategory.PIZZA,
                priceCents = 9999,
                automaticExtrasPricing = true,
                active = true,
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
            ),
        )
        val emptyId = (repository.createDraft() as CreateDraftResult.Created).draft.id
        val newId = (
            repository.replaceDraftWithAcceptedOrderDuplicate(
                sourceOrderId = sourceId,
                currentBusinessDate = businessDate,
                expectedDraftId = emptyId,
            ) as ReplaceDraftWithAcceptedOrderDuplicateResult.Created
            ).draftId
        val pizza = requireNotNull(repository.getById(newId))
            .items
            .single { it.productNameSnapshot == "Margherita" }
        assertEquals(Money.ofCents(700), pizza.baseUnitPrice)
        assertEquals(Money.ofCents(0), pizza.manualUnitPrice)
        assertEquals(Money.ofCents(0), pizza.finalUnitPrice)
    }

    private suspend fun seedDraftWithChildren(id: String): String {
        database.orderDao().insertDraft(
            OrderEntity(
                id = id,
                status = OrderStatus.DRAFT,
                draftSlot = 1,
                displayNumber = null,
                numberingMode = null,
                numberingCycle = null,
                businessDate = null,
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
                acceptedAt = null,
                totalCents = 0,
                generalNote = "old draft note",
                sourceOrderId = null,
            ),
        )
        database.orderDao().insertOrderItem(
            orderItem(
                id = "old-item",
                orderId = id,
                sequence = 1,
                name = "Vecchia",
                productId = productId,
                note = "old",
            ),
        )
        database.orderDao().insertOrderItemAdditions(
            listOf(
                OrderItemAdditionEntity(
                    id = "old-add",
                    orderItemId = "old-item",
                    additionId = additionId,
                    additionNameSnapshot = "Bufala",
                    additionPrintedNameSnapshot = "BUFALA",
                    listedPriceCents = 150,
                    chargedPriceCents = 150,
                    displayOrder = 0,
                ),
            ),
        )
        database.orderDao().insertOrderItemRemovals(
            listOf(
                OrderItemRemovalEntity(
                    id = "old-rem",
                    orderItemId = "old-item",
                    ingredientId = ingredientId,
                    ingredientNameSnapshot = "Pomodoro",
                    displayOrder = 0,
                ),
            ),
        )
        return id
    }

    private suspend fun seedRichAccepted(): String {
        val id = "accepted-rich"
        insertAccepted(
            id = id,
            generalNote = "Linea 1\n  spaces  ",
            items = listOf(
                orderItem(
                    id = "item-pizza",
                    orderId = id,
                    sequence = 1,
                    name = "Margherita",
                    productId = productId,
                    quantity = 2,
                    baseCents = 700,
                    autoExtrasCents = 100,
                    manualCents = 0,
                    finalCents = 0,
                    note = "Ben cotta",
                ),
                orderItem(
                    id = "item-drink",
                    orderId = id,
                    sequence = 4,
                    name = "Coca",
                    productId = null,
                    category = ProductCategory.BIBITA,
                    quantity = 1,
                    baseCents = 200,
                    finalCents = 200,
                ),
            ),
            additions = listOf(
                OrderItemAdditionEntity(
                    id = "add-1",
                    orderItemId = "item-pizza",
                    additionId = additionId,
                    additionNameSnapshot = "Bufala",
                    additionPrintedNameSnapshot = "BUFALA",
                    listedPriceCents = 150,
                    chargedPriceCents = 150,
                    displayOrder = 0,
                ),
            ),
            removals = listOf(
                OrderItemRemovalEntity(
                    id = "rem-1",
                    orderItemId = "item-pizza",
                    ingredientId = ingredientId,
                    ingredientNameSnapshot = "Pomodoro",
                    displayOrder = 0,
                ),
            ),
        )
        return id
    }

    private suspend fun insertAccepted(
        id: String,
        generalNote: String?,
        items: List<OrderItemEntity>,
        additions: List<OrderItemAdditionEntity> = emptyList(),
        removals: List<OrderItemRemovalEntity> = emptyList(),
    ) {
        database.orderDao().insertDraft(
            OrderEntity(
                id = id,
                status = OrderStatus.ACCEPTED,
                draftSlot = null,
                displayNumber = "042",
                numberingMode = null,
                numberingCycle = null,
                businessDate = businessDate.toString(),
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
                acceptedAt = NOW.toEpochMilli(),
                totalCents = 9_999,
                generalNote = generalNote,
                sourceOrderId = null,
            ),
        )
        for (entity in items) {
            database.orderDao().insertOrderItem(entity)
        }
        if (additions.isNotEmpty()) {
            database.orderDao().insertOrderItemAdditions(additions)
        }
        if (removals.isNotEmpty()) {
            database.orderDao().insertOrderItemRemovals(removals)
        }
    }

    private fun orderItem(
        id: String,
        orderId: String,
        sequence: Int,
        name: String = "Prodotto",
        productId: Long? = 1L,
        category: ProductCategory = ProductCategory.PIZZA,
        quantity: Int = 1,
        baseCents: Long = 700,
        autoExtrasCents: Long = 0,
        manualCents: Long? = null,
        finalCents: Long = 700,
        note: String? = null,
    ): OrderItemEntity = OrderItemEntity(
        id = id,
        orderId = orderId,
        productId = productId,
        productNameSnapshot = name,
        productPrintedNameSnapshot = name.uppercase(),
        categorySnapshot = category,
        quantity = quantity,
        baseUnitPriceCents = baseCents,
        automaticExtrasTotalCents = autoExtrasCents,
        manualUnitPriceCents = manualCents,
        finalUnitPriceCents = finalCents,
        automaticExtrasPricingSnapshot = true,
        note = note,
        createdSequence = sequence,
    )

    private fun countRows(table: String): Int = database.query(
        "SELECT COUNT(*) FROM $table",
        emptyArray(),
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun countRowsForOrder(table: String, orderId: String): Int = database.query(
        "SELECT COUNT(*) FROM $table WHERE orderId = ?",
        arrayOf(orderId),
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun countRowsForOrderItemsChildren(table: String, orderId: String): Int =
        database.query(
            """
            SELECT COUNT(*) FROM $table
            WHERE orderItemId IN (SELECT id FROM order_items WHERE orderId = ?)
            """.trimIndent(),
            arrayOf(orderId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-15T12:30:00Z")
    }
}
