package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RET-001 repository coverage: RET-T001..T006, RET-T008.
 * RET-T007 cutoff: BusinessDateCalculatorTest DATE-001/002.
 */
@RunWith(AndroidJUnit4::class)
class PurgeAcceptedBeforeRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: RoomOrderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Enforce FK so RET-T004 proves removals must be deleted before cascade deletes items.
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=ON")
        clock = MutableClock(romeLocal("2026-09-15T18:00:00"))
        val settingsRepository = RoomSettingsRepository(
            appSettingsDao = database.appSettingsDao(),
            clockProvider = clock,
        )
        val numberingRepository = RoomNumberingRepository(
            numberingStateDao = database.numberingStateDao(),
            numberingSeedProvider = FixedSeedProvider(42L),
        )
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = clock,
            transactionRunner = RoomDatabaseTransactionRunner(database),
            numberingRepository = numberingRepository,
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun retT001OldAcceptedIsDeleted() = runBlocking {
        insertAcceptedOrder(id = "old-1", businessDate = "2026-09-14", displayNumber = "001")

        val result = repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        assertTrue(result is PurgeAcceptedBeforeResult.Purged)
        assertEquals(1, (result as PurgeAcceptedBeforeResult.Purged).deletedOrderCount)
        assertNull(database.orderDao().getFullOrder("old-1"))
    }

    @Test
    fun retT002CurrentBusinessDateAcceptedIsPreserved() = runBlocking {
        insertAcceptedOrder(id = "today-1", businessDate = "2026-09-15", displayNumber = "010")
        insertAcceptedOrder(id = "old-1", businessDate = "2026-09-14", displayNumber = "001")

        repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        val today = requireNotNull(database.orderDao().getFullOrder("today-1"))
        assertEquals(OrderStatus.ACCEPTED, today.order.status)
        assertEquals("2026-09-15", today.order.businessDate)
        assertEquals("010", today.order.displayNumber)
        assertNull(database.orderDao().getFullOrder("old-1"))
    }

    @Test
    fun retT003ActiveDraftIsPreserved() = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        insertAcceptedOrder(id = "old-1", businessDate = "2026-09-14", displayNumber = "001")

        repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        assertEquals(draft.id, repository.getActiveDraft()?.id)
        assertEquals(OrderStatus.DRAFT, requireNotNull(repository.getById(draft.id)).status)
        assertNull(requireNotNull(repository.getById(draft.id)).businessDate)
    }

    @Test
    fun retT004NoOrphanItemsAdditionsOrRemovals() = runBlocking {
        val productId = insertPizzaWithIngredient()
        val additionId = database.additionDao().insert(
            AdditionEntity(
                name = "Bufala",
                normalizedName = "bufala",
                printedName = "BUFALA",
                priceCents = 100,
                active = true,
                createdAt = clock.now().toEpochMilli(),
                updatedAt = clock.now().toEpochMilli(),
            ),
        )
        val ingredientId = requireNotNull(
            database.ingredientDao().getByNormalizedName("pomodoro"),
        ).id

        val orderId = "old-rich"
        val itemId = "old-item"
        database.orderDao().insertDraft(
            acceptedOrder(orderId, "2026-09-14", "099").copy(totalCents = 800),
        )
        database.orderDao().insertOrderItem(
            OrderItemEntity(
                id = itemId,
                orderId = orderId,
                productId = productId,
                productNameSnapshot = "Margherita",
                productPrintedNameSnapshot = "MARGHERITA",
                categorySnapshot = ProductCategory.PIZZA,
                quantity = 1,
                baseUnitPriceCents = 700,
                automaticExtrasTotalCents = 100,
                manualUnitPriceCents = null,
                finalUnitPriceCents = 800,
                automaticExtrasPricingSnapshot = true,
                note = "nota",
                createdSequence = 1,
            ),
        )
        database.orderDao().insertOrderItemAdditions(
            listOf(
                OrderItemAdditionEntity(
                    id = "old-add",
                    orderItemId = itemId,
                    additionId = additionId,
                    additionNameSnapshot = "Bufala",
                    additionPrintedNameSnapshot = "BUFALA",
                    listedPriceCents = 100,
                    chargedPriceCents = 100,
                    displayOrder = 0,
                ),
            ),
        )
        database.orderDao().insertOrderItemRemovals(
            listOf(
                OrderItemRemovalEntity(
                    id = "old-rem",
                    orderItemId = itemId,
                    ingredientId = ingredientId,
                    ingredientNameSnapshot = "Pomodoro",
                    displayOrder = 0,
                ),
            ),
        )

        assertEquals(1, countRows("orders"))
        assertEquals(1, countRows("order_items"))
        assertEquals(1, countRows("order_item_additions"))
        assertEquals(1, countRows("order_item_removals"))

        repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        assertEquals(0, countRows("orders"))
        assertEquals(0, countRows("order_items"))
        assertEquals(0, countRows("order_item_additions"))
        assertEquals(0, countRows("order_item_removals"))
    }

    @Test
    fun retT005NumberingStateUnchanged() = runBlocking {
        val oldDate = "2026-09-14"
        val currentDate = "2026-09-15"
        database.numberingStateDao().insert(
            NumberingStateEntity(
                businessDate = oldDate,
                nextSequentialNumber = 7,
                randomCycle = 2,
                randomSeed = 99L,
                randomSeedInitialized = true,
                randomPosition = 3,
                updatedAt = clock.now().toEpochMilli(),
            ),
        )
        database.numberingStateDao().insert(
            NumberingStateEntity(
                businessDate = currentDate,
                nextSequentialNumber = 2,
                randomCycle = 1,
                randomSeed = 11L,
                randomSeedInitialized = true,
                randomPosition = 1,
                updatedAt = clock.now().toEpochMilli(),
            ),
        )
        val beforeOld = requireNotNull(database.numberingStateDao().getRaw(oldDate))
        val beforeCurrent = requireNotNull(database.numberingStateDao().getRaw(currentDate))
        insertAcceptedOrder(id = "old-1", businessDate = oldDate, displayNumber = "006")

        repository.purgeAcceptedBefore(LocalDate.parse(currentDate))

        assertEquals(beforeOld, database.numberingStateDao().getRaw(oldDate))
        assertEquals(beforeCurrent, database.numberingStateDao().getRaw(currentDate))
    }

    @Test
    fun retT006RepeatedPurgeIsIdempotent() = runBlocking {
        insertAcceptedOrder(id = "old-1", businessDate = "2026-09-14", displayNumber = "001")
        insertAcceptedOrder(id = "today-1", businessDate = "2026-09-15", displayNumber = "002")
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft

        val first = repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))
        val second = repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        assertEquals(1, (first as PurgeAcceptedBeforeResult.Purged).deletedOrderCount)
        assertEquals(0, (second as PurgeAcceptedBeforeResult.Purged).deletedOrderCount)
        assertNotNull(database.orderDao().getFullOrder("today-1"))
        assertEquals(draft.id, repository.getActiveDraft()?.id)
    }

    @Test
    fun retT008DraftAcceptedAfterCutoffSurvivesPurge() = runBlocking {
        clock.now = romeLocal("2026-09-15T04:30:00")
        val productId = insertPizzaWithIngredient()
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        assertTrue(
            repository.quickAddStandard(draft.id, productId) is QuickAddStandardResult.Added,
        )
        assertNull(requireNotNull(repository.getById(draft.id)).businessDate)

        clock.now = romeLocal("2026-09-15T05:30:00")
        val accepted = repository.acceptOrder(draft.id)
        assertTrue(accepted is AcceptOrderResult.Accepted)
        assertEquals(LocalDate.parse("2026-09-15"), (accepted as AcceptOrderResult.Accepted).businessDate)

        insertAcceptedOrder(id = "old-prev", businessDate = "2026-09-14", displayNumber = "099")

        repository.purgeAcceptedBefore(LocalDate.parse("2026-09-15"))

        assertNotNull(database.orderDao().getFullOrder(draft.id))
        assertEquals("2026-09-15", requireNotNull(database.orderDao().getFullOrder(draft.id)).order.businessDate)
        assertNull(database.orderDao().getFullOrder("old-prev"))
    }

    private suspend fun insertAcceptedOrder(
        id: String,
        businessDate: String,
        displayNumber: String,
    ) {
        database.orderDao().insertDraft(acceptedOrder(id, businessDate, displayNumber))
    }

    private fun acceptedOrder(
        id: String,
        businessDate: String,
        displayNumber: String,
    ): OrderEntity = OrderEntity(
        id = id,
        status = OrderStatus.ACCEPTED,
        draftSlot = null,
        displayNumber = displayNumber,
        numberingMode = NumberingMode.SEQUENTIAL,
        numberingCycle = null,
        businessDate = businessDate,
        createdAt = clock.now().toEpochMilli(),
        updatedAt = clock.now().toEpochMilli(),
        acceptedAt = clock.now().toEpochMilli(),
        totalCents = 700,
        generalNote = null,
        sourceOrderId = null,
    )

    private suspend fun insertPizzaWithIngredient(): Long {
        val productId = database.productDao().insert(
            ProductEntity(
                name = "Margherita",
                normalizedName = "margherita",
                printedName = "MARGHERITA",
                category = ProductCategory.PIZZA,
                priceCents = 700,
                automaticExtrasPricing = true,
                active = true,
                createdAt = clock.now().toEpochMilli(),
                updatedAt = clock.now().toEpochMilli(),
            ),
        )
        val ingredientId = database.ingredientDao().insert(
            IngredientEntity(
                name = "Pomodoro",
                normalizedName = "pomodoro",
                active = true,
            ),
        )
        database.productDao().insertProductIngredients(
            listOf(
                ProductIngredientEntity(
                    productId = productId,
                    ingredientId = ingredientId,
                    displayOrder = 0,
                ),
            ),
        )
        return productId
    }

    private suspend fun countRows(table: String): Int = database.query(
        "SELECT COUNT(*) FROM $table",
        emptyArray(),
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private class MutableClock(var now: Instant) : ClockProvider {
        override fun now(): Instant = now
    }

    private class FixedSeedProvider(private val seed: Long) : NumberingSeedProvider {
        override fun nextSeed(): Long = seed
    }

    private companion object {
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()
    }
}
