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
import it.krpng.cassa.data.database.dao.OrderDao
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
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateAcceptedOrderRepositoryTest {
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
    fun dup001To004FaithfulIndependentCopyAndSourceUnchanged() = runBlocking {
        val sourceId = seedRichAccepted()
        val before = requireNotNull(repository.getById(sourceId))

        val result = repository.duplicateAcceptedOrder(sourceId, businessDate)
        assertTrue(result is DuplicateAcceptedOrderResult.Created)
        val draftId = (result as DuplicateAcceptedOrderResult.Created).draftId
        assertNotEquals(sourceId, draftId)

        val draft = requireNotNull(repository.getById(draftId))
        assertEquals(OrderStatus.DRAFT, draft.status)
        assertNull(draft.displayNumber)
        assertNull(draft.acceptedAt)
        assertNull(draft.businessDate)
        assertEquals(sourceId, draft.sourceOrderId)
        assertEquals(Money.ZERO, draft.total)
        assertEquals("Linea 1\n  spaces  ", draft.generalNote)
        assertEquals(NOW, draft.createdAt)
        assertEquals(NOW, draft.updatedAt)

        assertEquals(2, draft.items.size)
        val pizza = draft.items.single { it.productNameSnapshot == "Margherita" }
        val drink = draft.items.single { it.productNameSnapshot == "Coca" }
        assertEquals(listOf(1, 4), listOf(pizza.createdSequence, drink.createdSequence).sorted())
        assertEquals(productId, pizza.productId)
        assertNull(drink.productId)
        assertEquals(2, pizza.quantity)
        assertEquals(Money.ofCents(700), pizza.baseUnitPrice)
        assertEquals(Money.ofCents(100), pizza.automaticExtrasTotal)
        assertTrue(pizza.automaticExtrasPricingSnapshot)
        assertEquals(Money.ofCents(0), pizza.manualUnitPrice)
        assertEquals(Money.ofCents(0), pizza.finalUnitPrice)
        assertEquals("Ben cotta", pizza.note)
        assertEquals(additionId, pizza.additions.single().additionId)
        assertEquals("Bufala", pizza.additions.single().nameSnapshot)
        assertEquals(Money.ofCents(150), pizza.additions.single().listedPrice)
        assertEquals(Money.ofCents(150), pizza.additions.single().chargedPrice)
        assertEquals(ingredientId, pizza.removals.single().ingredientId)
        assertEquals("Pomodoro", pizza.removals.single().nameSnapshot)

        assertNotEquals(before.items.map { it.id }.toSet(), draft.items.map { it.id }.toSet())
        assertNotEquals(
            before.items.flatMap { it.additions.map { addition -> addition.id } }.toSet(),
            draft.items.flatMap { it.additions.map { addition -> addition.id } }.toSet(),
        )

        val after = requireNotNull(repository.getById(sourceId))
        assertEquals(before, after)
    }

    @Test
    fun dup005ExistingDraftTypedConflictZeroWrites() = runBlocking {
        val sourceId = seedRichAccepted()
        val existingDraftId = (repository.createDraft() as CreateDraftResult.Created).draft.id
        val ordersBefore = countRows("orders")
        val itemsBefore = countRows("order_items")

        assertEquals(
            DuplicateAcceptedOrderResult.DraftConflict(existingDraftId),
            repository.duplicateAcceptedOrder(sourceId, businessDate),
        )
        assertEquals(ordersBefore, countRows("orders"))
        assertEquals(itemsBefore, countRows("order_items"))
        assertEquals(existingDraftId, repository.getActiveDraft()?.id)
        assertEquals(OrderStatus.ACCEPTED, requireNotNull(repository.getById(sourceId)).status)
    }

    @Test
    fun archT027RejectsDraftOldAndMissing() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        assertEquals(
            DuplicateAcceptedOrderResult.SourceUnavailable,
            repository.duplicateAcceptedOrder(draft.id, businessDate),
        )
        repository.deleteDraft(draft.id)

        insertOrder(
            id = "old-accepted",
            businessDate = "2026-09-14",
            generalNote = null,
            items = listOf(orderItem(id = "i1", orderId = "old-accepted", sequence = 1, productId = null)),
        )
        assertEquals(
            DuplicateAcceptedOrderResult.SourceUnavailable,
            repository.duplicateAcceptedOrder("old-accepted", businessDate),
        )
        assertEquals(
            DuplicateAcceptedOrderResult.SourceUnavailable,
            repository.duplicateAcceptedOrder("missing", businessDate),
        )
    }

    @Test
    fun archT028GeneralNoteCopyExactMultiline() = runBlocking {
        val withNote = seedRichAccepted()
        val draftWithNote = (
            repository.duplicateAcceptedOrder(withNote, businessDate)
                as DuplicateAcceptedOrderResult.Created
            ).draftId
        assertEquals(
            "Linea 1\n  spaces  ",
            requireNotNull(repository.getById(draftWithNote)).generalNote,
        )
    }

    @Test
    fun archT028GeneralNoteNullRemainsNull() = runBlocking {
        insertOrder(
            id = "accepted-null-note",
            businessDate = businessDate.toString(),
            generalNote = null,
            items = listOf(
                orderItem(id = "n1", orderId = "accepted-null-note", sequence = 1, productId = null),
            ),
        )
        val draftNull = (
            repository.duplicateAcceptedOrder("accepted-null-note", businessDate)
                as DuplicateAcceptedOrderResult.Created
            ).draftId
        assertNull(requireNotNull(repository.getById(draftNull)).generalNote)
    }

    @Test
    fun archT029CreatedSequenceExactWithGaps() = runBlocking {
        insertOrder(
            id = "seq-gaps",
            businessDate = businessDate.toString(),
            generalNote = null,
            items = listOf(
                orderItem(id = "s1", orderId = "seq-gaps", sequence = 1, name = "A", productId = null),
                orderItem(id = "s2", orderId = "seq-gaps", sequence = 4, name = "B", productId = null),
                orderItem(id = "s3", orderId = "seq-gaps", sequence = 9, name = "C", productId = null),
            ),
        )
        val draftId = (
            repository.duplicateAcceptedOrder("seq-gaps", businessDate)
                as DuplicateAcceptedOrderResult.Created
            ).draftId
        assertEquals(
            listOf(1, 4, 9),
            requireNotNull(repository.getById(draftId))
                .items
                .sortedBy { it.createdSequence }
                .map { it.createdSequence },
        )
    }

    @Test
    fun archT030ReferenceIdsCopiedIncludingNull() = runBlocking {
        val sourceId = seedRichAccepted()
        val draft = requireNotNull(
            repository.getById(
                (
                    repository.duplicateAcceptedOrder(sourceId, businessDate)
                        as DuplicateAcceptedOrderResult.Created
                    ).draftId,
            ),
        )
        val pizza = draft.items.single { it.productNameSnapshot == "Margherita" }
        val drink = draft.items.single { it.productNameSnapshot == "Coca" }
        assertEquals(productId, pizza.productId)
        assertNull(drink.productId)
        assertEquals(additionId, pizza.additions.single().additionId)
        assertEquals(ingredientId, pizza.removals.single().ingredientId)
    }

    @Test
    fun archT031RollbackOnChildInsertFailure() = runBlocking {
        val sourceId = seedRichAccepted()
        val failingRepo = RoomOrderRepository(
            orderDao = FailingRemovalOrderDao(database.orderDao()),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
        val ordersBefore = countRows("orders")
        val itemsBefore = countRows("order_items")
        val additionsBefore = countRows("order_item_additions")
        val removalsBefore = countRows("order_item_removals")

        assertEquals(
            DuplicateAcceptedOrderResult.PersistenceFailure,
            failingRepo.duplicateAcceptedOrder(sourceId, businessDate),
        )
        assertEquals(ordersBefore, countRows("orders"))
        assertEquals(itemsBefore, countRows("order_items"))
        assertEquals(additionsBefore, countRows("order_item_additions"))
        assertEquals(removalsBefore, countRows("order_item_removals"))
        assertNull(repository.getActiveDraft())
        assertEquals(OrderStatus.ACCEPTED, requireNotNull(repository.getById(sourceId)).status)
    }

    private suspend fun seedRichAccepted(): String {
        val id = "accepted-rich"
        insertOrder(
            id = id,
            businessDate = businessDate.toString(),
            generalNote = "Linea 1\n  spaces  ",
            totalCents = 9_999,
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

    private suspend fun insertOrder(
        id: String,
        businessDate: String,
        generalNote: String?,
        items: List<OrderItemEntity>,
        totalCents: Long = 1_400,
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
                businessDate = businessDate,
                createdAt = NOW.toEpochMilli(),
                updatedAt = NOW.toEpochMilli(),
                acceptedAt = NOW.toEpochMilli(),
                totalCents = totalCents,
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

    private suspend fun countRows(table: String): Int = database.query(
        "SELECT COUNT(*) FROM $table",
        emptyArray(),
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private class FailingRemovalOrderDao(
        private val delegate: OrderDao,
    ) : OrderDao by delegate {
        override suspend fun insertOrderItemRemovals(
            removals: List<OrderItemRemovalEntity>,
        ): Unit = throw SQLiteException("removal insert failed")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-15T12:30:00Z")
    }
}
