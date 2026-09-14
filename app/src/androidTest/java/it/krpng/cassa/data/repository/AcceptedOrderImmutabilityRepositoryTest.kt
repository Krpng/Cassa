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
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ACCEPT-005 — Accepted order immutability.
 * Test IDs: ACCEPT-T007; SNAP-001..SNAP-004.
 */
@RunWith(AndroidJUnit4::class)
class AcceptedOrderImmutabilityRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var clock: CountingClock
    private lateinit var settingsRepository: RoomSettingsRepository
    private lateinit var numberingRepository: RoomNumberingRepository
    private lateinit var repository: RoomOrderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = CountingClock(DEFAULT_NOW)
        settingsRepository = RoomSettingsRepository(
            appSettingsDao = database.appSettingsDao(),
            clockProvider = clock,
        )
        numberingRepository = RoomNumberingRepository(
            numberingStateDao = database.numberingStateDao(),
            numberingSeedProvider = FixedSeedProvider(FIXED_SEED),
        )
        repository = buildRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acceptT007AcceptedUpdateMethodsRejectedWithZeroMutation() = runBlocking {
        val fixture = acceptRichOrder()
        val before = requireNotNull(database.orderDao().getFullOrder(fixture.orderId))
        val clockBefore = clock.calls

        // Simulate app restart: new repository over same Room DB (status is SoT).
        repository = buildRepository()
        clock.current = LATER_NOW

        assertSame(
            UpdateGeneralNoteResult.OrderNotEditable,
            repository.updateGeneralNote(fixture.orderId, "Nota vietata"),
        )
        assertSame(
            ChangeQuantityResult.OrderNotEditable,
            repository.changeQuantity(fixture.orderId, fixture.itemId, fixture.quantity + 1),
        )
        assertSame(
            RemoveOrderItemResult.OrderNotEditable,
            repository.removeOrderItem(fixture.orderId, fixture.itemId),
        )
        assertSame(
            UpdateOrderItemResult.OrderNotEditable,
            repository.updateOrderItem(
                orderId = fixture.orderId,
                orderItemId = fixture.itemId,
                quantity = fixture.quantity,
                note = "Nota riga vietata",
                manualUnitPrice = Money.ofCents(1),
            ),
        )
        assertSame(
            UpdateOrderItemResult.OrderNotEditable,
            repository.updateOrderItem(
                orderId = fixture.orderId,
                orderItemId = fixture.itemId,
                quantity = fixture.quantity,
                note = fixture.itemNote,
                manualUnitPrice = fixture.manualUnitPrice,
                selectedAdditionIds = emptyList(),
                selectedRemovalIngredientIds = emptyList(),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertSame(
            SplitStandardPizzaItemResult.OrderNotEditable,
            repository.splitStandardPizzaItem(
                orderId = fixture.orderId,
                orderItemId = fixture.itemId,
                note = "Split vietato",
                manualUnitPrice = null,
                selectedAdditionIds = listOf(fixture.additionId),
                selectedRemovalIngredientIds = emptyList(),
            ),
        )
        assertSame(
            QuickAddStandardResult.OrderNotEditable,
            repository.quickAddStandard(fixture.orderId, fixture.productId),
        )
        assertSame(
            DeleteDraftResult.NotFoundOrNotDraft,
            repository.deleteDraft(fixture.orderId),
        )
        // Content mutations reject before ClockProvider; updatedAt must stay frozen.
        assertEquals(clockBefore, clock.calls)

        // replaceDraft allocates a candidate draft (clock tick) then DAO rejects —
        // ACCEPTED rows must still be identical.
        assertSame(
            ReplaceDraftResult.OriginalNotFoundOrNotDraft,
            repository.replaceDraft(fixture.orderId),
        )
        assertEquals(clockBefore + 1, clock.calls)

        val after = requireNotNull(database.orderDao().getFullOrder(fixture.orderId))
        assertAcceptedFingerprintUnchanged(before, after)
    }

    @Test
    fun snap001MenuPriceChangeLeavesAcceptedHistoricalPriceUnchanged() = runBlocking {
        val productId = insertProduct(name = "Margherita", priceCents = 700)
        val orderId = acceptStandardProduct(productId, quantity = 2)
        val before = requireNotNull(database.orderDao().getFullOrder(orderId))

        val catalog = requireNotNull(database.productDao().getWithIngredients(productId)).product
        database.productDao().update(catalog.copy(priceCents = 999, updatedAt = LATER_NOW.toEpochMilli()))

        val after = requireNotNull(database.orderDao().getFullOrder(orderId))
        assertAcceptedFingerprintUnchanged(before, after)
        assertEquals(700L, after.items.single().item.baseUnitPriceCents)
        assertEquals(700L, after.items.single().item.finalUnitPriceCents)
        assertEquals(1_400L, after.order.totalCents)
    }

    @Test
    fun snap002ProductNamePrintedNameChangeLeavesAcceptedSnapshotsUnchanged() = runBlocking {
        val productId = insertProduct(
            name = "Margherita",
            printedName = "MARGHERITA",
            priceCents = 700,
        )
        val orderId = acceptStandardProduct(productId, quantity = 1)
        val before = requireNotNull(database.orderDao().getFullOrder(orderId))

        val catalog = requireNotNull(database.productDao().getWithIngredients(productId)).product
        database.productDao().update(
            catalog.copy(
                name = "Margherita nuova",
                normalizedName = "margherita nuova",
                printedName = "NUOVA",
                updatedAt = LATER_NOW.toEpochMilli(),
            ),
        )

        val after = requireNotNull(database.orderDao().getFullOrder(orderId))
        assertAcceptedFingerprintUnchanged(before, after)
        assertEquals("Margherita", after.items.single().item.productNameSnapshot)
        assertEquals("MARGHERITA", after.items.single().item.productPrintedNameSnapshot)
    }

    @Test
    fun snap003AdditionPriceChangeLeavesHistoricalAdditionSnapshotUnchanged() = runBlocking {
        val fixture = acceptRichOrder()
        val before = requireNotNull(database.orderDao().getFullOrder(fixture.orderId))
        val addition = requireNotNull(database.additionDao().getById(fixture.additionId))

        database.additionDao().update(
            addition.copy(
                name = "Provola nuova",
                normalizedName = "provola nuova",
                printedName = "NUOVA",
                priceCents = 500,
                updatedAt = LATER_NOW.toEpochMilli(),
            ),
        )

        val after = requireNotNull(database.orderDao().getFullOrder(fixture.orderId))
        assertAcceptedFingerprintUnchanged(before, after)
        val snap = after.items.single().additions.single()
        assertEquals("Provola", snap.additionNameSnapshot)
        assertEquals("Provola", snap.additionPrintedNameSnapshot)
        assertEquals(150L, snap.listedPriceCents)
        assertEquals(150L, snap.chargedPriceCents)
    }

    @Test
    fun snap004DeactivateProductLeavesAcceptedOrderReadable() = runBlocking {
        val productId = insertProduct(name = "Margherita", priceCents = 700)
        val orderId = acceptStandardProduct(productId, quantity = 1)
        val before = requireNotNull(database.orderDao().getFullOrder(orderId))

        database.productDao().updateActive(
            productId = productId,
            active = false,
            updatedAt = LATER_NOW.toEpochMilli(),
        )

        repository = buildRepository()
        val domain = requireNotNull(repository.getById(orderId))
        assertEquals(OrderStatus.ACCEPTED, domain.status)
        assertEquals("Margherita", domain.items.single().productNameSnapshot)
        assertEquals(700L, domain.items.single().baseUnitPrice.cents)

        val after = requireNotNull(database.orderDao().getFullOrder(orderId))
        assertAcceptedFingerprintUnchanged(before, after)
        assertNotNull(after.items.single().item.productId)
    }

    private fun buildRepository(): RoomOrderRepository = RoomOrderRepository(
        orderDao = database.orderDao(),
        clockProvider = clock,
        transactionRunner = RoomDatabaseTransactionRunner(database),
        numberingRepository = numberingRepository,
        settingsRepository = settingsRepository,
    )

    private suspend fun acceptRichOrder(): AcceptedFixture {
        val productId = insertProduct(name = "Margherita", priceCents = 700)
        val additionId = database.additionDao().insert(addition("Provola", null, 150))
        val mozzarellaId = database.ingredientDao().insert(ingredient("Mozzarella"))
        database.productDao().insertProductIngredients(
            listOf(ProductIngredientEntity(productId, mozzarellaId, 0)),
        )

        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        assertTrue(repository.quickAddStandard(draft.id, productId) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        val note = "Ben cotta"
        val manual = Money.ofCents(1_000)
        assertSame(
            UpdateOrderItemResult.Updated,
            repository.updateOrderItem(
                orderId = draft.id,
                orderItemId = itemId,
                quantity = 2,
                note = note,
                manualUnitPrice = manual,
                selectedAdditionIds = listOf(additionId),
                selectedRemovalIngredientIds = listOf(mozzarellaId),
                customizationQuantityIntent =
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
            ),
        )
        assertSame(
            UpdateGeneralNoteResult.Updated,
            repository.updateGeneralNote(draft.id, "Consegna alle 21"),
        )

        val accepted = repository.acceptOrder(draft.id)
        assertTrue(accepted is AcceptOrderResult.Accepted)
        return AcceptedFixture(
            orderId = draft.id,
            itemId = itemId,
            productId = productId,
            additionId = additionId,
            quantity = 2,
            itemNote = note,
            manualUnitPrice = manual,
        )
    }

    private suspend fun acceptStandardProduct(productId: Long, quantity: Int): String {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        assertTrue(repository.quickAddStandard(draft.id, productId) is QuickAddStandardResult.Added)
        val itemId = requireNotNull(repository.getById(draft.id)).items.single().id
        if (quantity != 1) {
            assertSame(
                ChangeQuantityResult.Updated,
                repository.changeQuantity(draft.id, itemId, quantity),
            )
        }
        val accepted = repository.acceptOrder(draft.id)
        assertTrue(accepted is AcceptOrderResult.Accepted)
        return draft.id
    }

    private suspend fun insertProduct(
        name: String,
        priceCents: Long,
        printedName: String? = name.uppercase(),
    ): Long {
        return database.productDao().insert(
            ProductEntity(
                name = name,
                normalizedName = name.lowercase(),
                printedName = printedName,
                category = ProductCategory.PIZZA,
                priceCents = priceCents,
                automaticExtrasPricing = true,
                active = true,
                createdAt = DEFAULT_NOW.toEpochMilli(),
                updatedAt = DEFAULT_NOW.toEpochMilli(),
            ),
        )
    }

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
        createdAt = DEFAULT_NOW.toEpochMilli(),
        updatedAt = DEFAULT_NOW.toEpochMilli(),
    )

    private fun ingredient(name: String): IngredientEntity = IngredientEntity(
        name = name,
        normalizedName = name.lowercase(),
        active = true,
    )

    private fun assertAcceptedFingerprintUnchanged(before: FullOrder, after: FullOrder) {
        assertEquals(normalize(before), normalize(after))
        assertEquals(OrderStatus.ACCEPTED, after.order.status)
        assertNull(after.order.draftSlot)
        assertEquals(before.order.displayNumber, after.order.displayNumber)
        assertEquals(before.order.acceptedAt, after.order.acceptedAt)
        assertEquals(before.order.businessDate, after.order.businessDate)
        assertEquals(before.order.totalCents, after.order.totalCents)
        assertEquals(before.order.updatedAt, after.order.updatedAt)
        assertEquals(before.order.generalNote, after.order.generalNote)
    }

    private fun normalize(full: FullOrder): FullOrder = full.copy(
        items = full.items
            .sortedBy { it.item.createdSequence }
            .map { item ->
                item.copy(
                    additions = item.additions.sortedBy { it.displayOrder },
                    removals = item.removals.sortedBy { it.displayOrder },
                )
            },
    )

    private data class AcceptedFixture(
        val orderId: String,
        val itemId: String,
        val productId: Long,
        val additionId: Long,
        val quantity: Int,
        val itemNote: String,
        val manualUnitPrice: Money,
    )

    private class CountingClock(var current: Instant) : ClockProvider {
        var calls: Int = 0
            private set

        override fun now(): Instant {
            calls += 1
            return current
        }
    }

    private class FixedSeedProvider(private val seed: Long) : NumberingSeedProvider {
        override fun nextSeed(): Long = seed
    }

    private companion object {
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val DEFAULT_NOW: Instant = romeLocal("2026-09-14T18:00:00")
        val LATER_NOW: Instant = romeLocal("2026-09-14T19:30:00")
        const val FIXED_SEED = 42L

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()
    }
}
