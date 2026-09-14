package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.entity.NumberingStateRow
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.numbering.RandomCodeFormatter
import it.krpng.cassa.domain.numbering.StableRandomPermutation
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ACCEPT-003 normative coverage: ACCEPT-T001, T002 (accept side), T004, T005,
 * T013..T016, T018 (atomicity), NUM-T032, NUM-T033.
 * ACCEPT-004: ACCEPT-T003, ACCEPT-T017, NUM-T005 (concurrent / double accept).
 * ACCEPT-005 (ACCEPT-T007, SNAP-001..004): AcceptedOrderImmutabilityRepositoryTest.
 */
@RunWith(AndroidJUnit4::class)
class AcceptOrderRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var clock: MutableClock
    private lateinit var seedProvider: FixedSeedProvider
    private lateinit var settingsRepository: RoomSettingsRepository
    private lateinit var numberingRepository: RoomNumberingRepository
    private lateinit var repository: RoomOrderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(DEFAULT_NOW)
        seedProvider = FixedSeedProvider(FIXED_SEED)
        settingsRepository = RoomSettingsRepository(
            appSettingsDao = database.appSettingsDao(),
            clockProvider = clock,
        )
        numberingRepository = RoomNumberingRepository(
            numberingStateDao = database.numberingStateDao(),
            numberingSeedProvider = seedProvider,
        )
        repository = acceptRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acceptT001SequentialAcceptAssignsNumberDatesAndTotal() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 700, qty = 2, staleTotalCents = 1L)

        val result = repository.acceptOrder(draftId)

        assertTrue(result is AcceptOrderResult.Accepted)
        val accepted = result as AcceptOrderResult.Accepted
        assertEquals(draftId, accepted.orderId)
        assertEquals("001", accepted.displayNumber)
        assertEquals(Money.ofCents(1_400), accepted.total)
        assertEquals(DEFAULT_NOW, accepted.acceptedAt)
        assertEquals(LocalDate.parse("2026-09-14"), accepted.businessDate)
        assertEquals(NumberingMode.SEQUENTIAL, accepted.numberingMode)
        assertNull(accepted.numberingCycle)

        val persistedFull = requireNotNull(database.orderDao().getFullOrder(draftId))
        val persisted = persistedFull.order
        assertEquals(OrderStatus.ACCEPTED, persisted.status)
        assertEquals("001", persisted.displayNumber)
        assertEquals(1_400L, persisted.totalCents)
        assertEquals(DEFAULT_NOW.toEpochMilli(), persisted.acceptedAt)
        assertEquals(DEFAULT_NOW.toEpochMilli(), persisted.updatedAt)
        assertEquals("2026-09-14", persisted.businessDate)
        assertNull(persisted.draftSlot)
        assertEquals(1, persistedFull.items.size)
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)
    }

    @Test
    fun acceptT001RandomAcceptAllocatesOneA00Z99Code() = runBlocking {
        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        val draftId = createDraftWithItem(unitCents = 500, qty = 1)

        val result = repository.acceptOrder(draftId)

        val accepted = result as AcceptOrderResult.Accepted
        val expectedCode = RandomCodeFormatter.format(
            StableRandomPermutation.generate(FIXED_SEED, 1)[0],
        )
        assertEquals(expectedCode, accepted.displayNumber)
        assertEquals(NumberingMode.RANDOM, accepted.numberingMode)
        assertEquals(1, accepted.numberingCycle)

        val state = numberingState(accepted.businessDate.toString())!!
        assertTrue(state.randomSeedInitialized)
        assertEquals(FIXED_SEED, state.randomSeed)
        assertEquals(1, state.randomPosition)
        assertEquals(1L, state.nextSequentialNumber)
    }

    @Test
    fun acceptT002EmptyDraftRejectedWithoutNumberingConsume() = runBlocking {
        val created = repository.createDraft() as CreateDraftResult.Created

        val result = repository.acceptOrder(created.draft.id)

        assertSame(AcceptOrderResult.EmptyDraft, result)
        assertEquals(OrderStatus.DRAFT, repository.getById(created.draft.id)!!.status)
        assertNull(numberingState("2026-09-14"))
    }

    @Test
    fun acceptT004DraftSlotBecomesNullOnSuccess() = runBlocking {
        val draftId = createDraftWithItem()

        repository.acceptOrder(draftId)

        val persisted = requireNotNull(database.orderDao().getFullOrder(draftId)).order
        assertNull(persisted.draftSlot)
        assertNull(database.orderDao().getActiveDraft())
    }

    @Test
    fun acceptT005AcceptedAtAndBusinessDateUseSameLogicalNow() = runBlocking {
        clock.current = romeLocal("2026-09-14T04:59:59")
        val beforeCutoffId = createDraftWithItem(unitCents = 100, qty = 1)
        val before = repository.acceptOrder(beforeCutoffId) as AcceptOrderResult.Accepted
        assertEquals(clock.current, before.acceptedAt)
        assertEquals(LocalDate.parse("2026-09-13"), before.businessDate)

        clock.current = romeLocal("2026-09-14T05:00:00")
        val afterCutoffId = createDraftWithItem(unitCents = 100, qty = 1)
        val after = repository.acceptOrder(afterCutoffId) as AcceptOrderResult.Accepted
        assertEquals(clock.current, after.acceptedAt)
        assertEquals(LocalDate.parse("2026-09-14"), after.businessDate)
        assertEquals(clock.current.toEpochMilli(), after.acceptedAt.toEpochMilli())
        val entity = requireNotNull(database.orderDao().getFullOrder(afterCutoffId)).order
        assertEquals(entity.acceptedAt, entity.updatedAt)
    }

    @Test
    fun acceptT013SnapshotsCheckedTotalNotStaleDraftTotal() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 800, qty = 3, staleTotalCents = 1L)

        val result = repository.acceptOrder(draftId) as AcceptOrderResult.Accepted

        assertEquals(Money.ofCents(2_400), result.total)
        assertEquals(2_400L, database.orderDao().getFullOrder(draftId)!!.order.totalCents)
    }

    @Test
    fun acceptT014SuccessAdvancesNumberingOnceFailureLeavesBothUnchanged() = runBlocking {
        val okId = createDraftWithItem(unitCents = 200, qty = 1)
        assertTrue(repository.acceptOrder(okId) is AcceptOrderResult.Accepted)
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)

        val empty = (repository.createDraft() as CreateDraftResult.Created).draft.id
        assertSame(AcceptOrderResult.EmptyDraft, repository.acceptOrder(empty))
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)
        assertEquals(OrderStatus.DRAFT, repository.getById(empty)!!.status)
    }

    @Test
    fun acceptT015PreconditionFailureConsumesZeroNumber() = runBlocking {
        val missing = repository.acceptOrder("missing-id")
        assertSame(AcceptOrderResult.OrderNotFound, missing)
        assertNull(numberingState("2026-09-14"))

        val acceptedEntity = acceptedOrderEntity("already-accepted")
        database.orderDao().insertDraft(acceptedEntity)
        assertSame(
            AcceptOrderResult.OrderNotDraft,
            repository.acceptOrder(acceptedEntity.id),
        )
        assertNull(numberingState("2026-09-14"))
    }

    @Test
    fun acceptT016OverflowBlocksAcceptWithZeroWriteAndZeroNumber() = runBlocking {
        val draftId = createDraftWithItem(
            unitCents = Long.MAX_VALUE / 2,
            qty = 3,
            staleTotalCents = 0L,
        )

        val result = repository.acceptOrder(draftId)

        assertSame(AcceptOrderResult.AmountOverflow, result)
        val order = requireNotNull(database.orderDao().getFullOrder(draftId)).order
        assertEquals(OrderStatus.DRAFT, order.status)
        assertNull(order.displayNumber)
        assertNull(order.acceptedAt)
        assertEquals(1, order.draftSlot)
        assertNull(numberingState("2026-09-14"))
    }

    @Test
    fun acceptT018WriteConflictAfterAllocateRollsBackNumberingAndDraft() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 300, qty = 1)
        val failing = RoomOrderRepository(
            orderDao = FailingAcceptOrderDao(database.orderDao()),
            clockProvider = clock,
            transactionRunner = RoomDatabaseTransactionRunner(database),
            numberingRepository = numberingRepository,
            settingsRepository = settingsRepository,
        )

        val result = failing.acceptOrder(draftId)

        assertSame(AcceptOrderResult.PersistenceFailure, result)
        val order = requireNotNull(database.orderDao().getFullOrder(draftId)).order
        assertEquals(OrderStatus.DRAFT, order.status)
        assertNull(order.displayNumber)
        assertEquals(1, order.draftSlot)
        assertNull(numberingState("2026-09-14"))
    }

    @Test
    fun numT033AcceptOrderReadsNumberingModeAtAcceptTime() = runBlocking {
        val sequentialId = createDraftWithItem(unitCents = 100, qty = 1)
        val sequential = repository.acceptOrder(sequentialId) as AcceptOrderResult.Accepted
        assertEquals("001", sequential.displayNumber)
        assertEquals(NumberingMode.SEQUENTIAL, sequential.numberingMode)

        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        val randomId = createDraftWithItem(unitCents = 100, qty = 1)
        val random = repository.acceptOrder(randomId) as AcceptOrderResult.Accepted
        assertEquals(NumberingMode.RANDOM, random.numberingMode)
        assertTrue(random.displayNumber.matches(Regex("[A-Z]\\d{2}")))
    }

    @Test
    fun numT032AcceptedOrderFieldsUnchangedAfterLaterModeChange() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 450, qty = 2)
        val accepted = repository.acceptOrder(draftId) as AcceptOrderResult.Accepted
        val beforeFull = requireNotNull(database.orderDao().getFullOrder(draftId))
        val before = beforeFull.order

        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )

        val afterFull = requireNotNull(database.orderDao().getFullOrder(draftId))
        val after = afterFull.order
        assertEquals(before.displayNumber, after.displayNumber)
        assertEquals(before.acceptedAt, after.acceptedAt)
        assertEquals(before.businessDate, after.businessDate)
        assertEquals(before.totalCents, after.totalCents)
        assertEquals(before.numberingMode, after.numberingMode)
        assertEquals(accepted.displayNumber, after.displayNumber)
        assertEquals(
            beforeFull.items.map { it.item.finalUnitPriceCents },
            afterFull.items.map { it.item.finalUnitPriceCents },
        )
    }

    @Test
    fun sequentialAcceptPreservesRandomNumberingState() = runBlocking {
        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        val randomId = createDraftWithItem(unitCents = 100, qty = 1)
        repository.acceptOrder(randomId)
        val afterRandom = numberingState("2026-09-14")!!

        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )
        val sequentialId = createDraftWithItem(unitCents = 100, qty = 1)
        repository.acceptOrder(sequentialId)
        val afterSequential = numberingState("2026-09-14")!!

        assertEquals(afterRandom.randomSeed, afterSequential.randomSeed)
        assertEquals(afterRandom.randomSeedInitialized, afterSequential.randomSeedInitialized)
        assertEquals(afterRandom.randomCycle, afterSequential.randomCycle)
        assertEquals(afterRandom.randomPosition, afterSequential.randomPosition)
        assertEquals(2L, afterSequential.nextSequentialNumber)
    }

    @Test
    fun randomAcceptPreservesSequentialNumberingState() = runBlocking {
        val sequentialId = createDraftWithItem(unitCents = 100, qty = 1)
        repository.acceptOrder(sequentialId)
        val afterSequential = numberingState("2026-09-14")!!
        assertEquals(2L, afterSequential.nextSequentialNumber)

        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        val randomId = createDraftWithItem(unitCents = 100, qty = 1)
        repository.acceptOrder(randomId)
        val afterRandom = numberingState("2026-09-14")!!

        assertEquals(2L, afterRandom.nextSequentialNumber)
        assertTrue(afterRandom.randomSeedInitialized)
        assertEquals(1, afterRandom.randomPosition)
    }

    @Test
    fun acceptT003SecondAcceptAfterSuccessIsRejected() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 100, qty = 1)
        assertTrue(repository.acceptOrder(draftId) is AcceptOrderResult.Accepted)
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)

        assertSame(AcceptOrderResult.OrderNotDraft, repository.acceptOrder(draftId))
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)

        val order = requireNotNull(repository.getById(draftId))
        assertEquals(OrderStatus.ACCEPTED, order.status)
        assertEquals("001", order.displayNumber)
        assertNull(database.orderDao().getFullOrder(draftId)!!.order.draftSlot)
    }

    @Test
    fun acceptT017AndNumT005ConcurrentSequentialAcceptsOneSuccessOneNumber() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 100, qty = 1)

        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) { repository.acceptOrder(draftId) },
                async(Dispatchers.IO) { repository.acceptOrder(draftId) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is AcceptOrderResult.Accepted })
        assertEquals(
            1,
            results.count {
                it is AcceptOrderResult.OrderNotDraft ||
                    it is AcceptOrderResult.PersistenceFailure
            },
        )
        assertTrue(
            results.none {
                it !is AcceptOrderResult.Accepted &&
                    it !is AcceptOrderResult.OrderNotDraft &&
                    it !is AcceptOrderResult.PersistenceFailure
            },
        )

        val accepted = results.filterIsInstance<AcceptOrderResult.Accepted>().single()
        val order = requireNotNull(repository.getById(draftId))
        assertEquals(OrderStatus.ACCEPTED, order.status)
        assertEquals(accepted.displayNumber, order.displayNumber)
        assertEquals("001", order.displayNumber)
        assertNull(database.orderDao().getFullOrder(draftId)!!.order.draftSlot)
        assertEquals(DEFAULT_NOW.toEpochMilli(), order.acceptedAt?.toEpochMilli())
        assertEquals(LocalDate.parse("2026-09-14"), order.businessDate)
        assertEquals(2L, numberingState("2026-09-14")!!.nextSequentialNumber)
        assertEquals(0, numberingState("2026-09-14")!!.randomPosition)
        assertEquals(false, numberingState("2026-09-14")!!.randomSeedInitialized)
    }

    @Test
    fun acceptT017ConcurrentRandomAcceptsOneSuccessOneCode() = runBlocking {
        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        val draftId = createDraftWithItem(unitCents = 100, qty = 1)

        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) { repository.acceptOrder(draftId) },
                async(Dispatchers.IO) { repository.acceptOrder(draftId) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is AcceptOrderResult.Accepted })
        assertEquals(
            1,
            results.count {
                it is AcceptOrderResult.OrderNotDraft ||
                    it is AcceptOrderResult.PersistenceFailure
            },
        )

        val accepted = results.filterIsInstance<AcceptOrderResult.Accepted>().single()
        val expectedCode = RandomCodeFormatter.format(
            StableRandomPermutation.generate(FIXED_SEED, 1)[0],
        )
        assertEquals(expectedCode, accepted.displayNumber)

        val order = requireNotNull(repository.getById(draftId))
        assertEquals(OrderStatus.ACCEPTED, order.status)
        assertEquals(expectedCode, order.displayNumber)
        assertNull(database.orderDao().getFullOrder(draftId)!!.order.draftSlot)

        val state = numberingState("2026-09-14")!!
        assertEquals(1L, state.nextSequentialNumber)
        assertTrue(state.randomSeedInitialized)
        assertEquals(FIXED_SEED, state.randomSeed)
        assertEquals(1, state.randomPosition)
        assertEquals(1, state.randomCycle)
    }

    @Test
    fun invalidSequentialNumberingStateRollsBackWithZeroConsume() = runBlocking {
        val draftId = createDraftWithItem(unitCents = 100, qty = 1)
        // Corrupted DB fixture: entity require(nextSequentialNumber > 0) blocks Kotlin construction.
        // SQLite has no CHECK; insert 0 raw to exercise AcceptOrder defensive handling.
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO numbering_state (
                businessDate,
                nextSequentialNumber,
                randomCycle,
                randomSeed,
                randomSeedInitialized,
                randomPosition,
                updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                "2026-09-14",
                0L,
                1,
                0L,
                0,
                0,
                1L,
            ),
        )

        val result = repository.acceptOrder(draftId)

        assertSame(AcceptOrderResult.InvalidNumberingState, result)
        val order = requireNotNull(repository.getById(draftId))
        assertEquals(OrderStatus.DRAFT, order.status)
        assertNull(order.displayNumber)
        assertNull(order.acceptedAt)
        assertEquals(0L, rawNextSequentialNumber("2026-09-14"))
    }

    @Test
    fun singleNowIsReusedForAcceptUpdatedAtAndBusinessDate() = runBlocking {
        var orderClockCalls = 0
        val orderClock = object : ClockProvider {
            override fun now(): Instant {
                orderClockCalls += 1
                return DEFAULT_NOW
            }
        }
        val countingRepo = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = orderClock,
            transactionRunner = RoomDatabaseTransactionRunner(database),
            numberingRepository = numberingRepository,
            settingsRepository = settingsRepository,
        )
        val draftId = createDraftWithItem(unitCents = 100, qty = 1)

        countingRepo.acceptOrder(draftId)

        assertEquals(1, orderClockCalls)
    }

    private fun acceptRepository(): RoomOrderRepository = RoomOrderRepository(
        orderDao = database.orderDao(),
        clockProvider = clock,
        transactionRunner = RoomDatabaseTransactionRunner(database),
        numberingRepository = numberingRepository,
        settingsRepository = settingsRepository,
    )

    private suspend fun createDraftWithItem(
        unitCents: Long = 700,
        qty: Int = 1,
        staleTotalCents: Long = 999_999L,
    ): String {
        val created = repository.createDraft() as CreateDraftResult.Created
        val orderId = created.draft.id
        database.orderDao().insertOrderItem(
            OrderItemEntity(
                id = "item-$orderId",
                orderId = orderId,
                productId = null,
                productNameSnapshot = "Margherita",
                productPrintedNameSnapshot = "MARGHERITA",
                categorySnapshot = ProductCategory.PIZZA,
                quantity = qty,
                baseUnitPriceCents = unitCents,
                automaticExtrasTotalCents = 0L,
                manualUnitPriceCents = null,
                finalUnitPriceCents = unitCents,
                automaticExtrasPricingSnapshot = true,
                note = null,
                createdSequence = 1,
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE orders SET totalCents = ? WHERE id = ?",
            arrayOf<Any?>(staleTotalCents, orderId),
        )
        return orderId
    }

    private suspend fun numberingState(businessDate: String): NumberingStateRow? =
        database.numberingStateDao().getRaw(businessDate)

    private fun rawNextSequentialNumber(businessDate: String): Long {
        database.openHelper.writableDatabase.query(
            "SELECT nextSequentialNumber FROM numbering_state WHERE businessDate = ?",
            arrayOf(businessDate),
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing numbering_state for $businessDate" }
            return cursor.getLong(0)
        }
    }

    private fun acceptedOrderEntity(id: String): OrderEntity = OrderEntity(
        id = id,
        status = OrderStatus.ACCEPTED,
        draftSlot = null,
        displayNumber = "099",
        numberingMode = NumberingMode.SEQUENTIAL,
        numberingCycle = null,
        businessDate = "2026-09-14",
        acceptedAt = DEFAULT_NOW.toEpochMilli(),
        totalCents = 700L,
        generalNote = null,
        sourceOrderId = null,
        createdAt = DEFAULT_NOW.toEpochMilli(),
        updatedAt = DEFAULT_NOW.toEpochMilli(),
    )

    private class MutableClock(var current: Instant) : ClockProvider {
        override fun now(): Instant = current
    }

    private class FixedSeedProvider(private val seed: Long) : NumberingSeedProvider {
        override fun nextSeed(): Long = seed
    }

    private class FailingAcceptOrderDao(
        private val delegate: it.krpng.cassa.data.database.dao.OrderDao,
    ) : it.krpng.cassa.data.database.dao.OrderDao by delegate {
        override suspend fun acceptDraftOrder(
            orderId: String,
            displayNumber: String,
            numberingMode: String,
            numberingCycle: Int?,
            businessDate: String,
            acceptedAt: Long,
            totalCents: Long,
            updatedAt: Long,
        ): Int = 0
    }

    private companion object {
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val DEFAULT_NOW: Instant = romeLocal("2026-09-14T18:00:00")
        const val FIXED_SEED = 42L

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()
    }
}
