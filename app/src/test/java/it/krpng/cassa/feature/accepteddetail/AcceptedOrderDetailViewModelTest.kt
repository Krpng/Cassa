package it.krpng.cassa.feature.accepteddetail

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptedOrderDetailViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ARCH-T011 current-day accepted is visible`() = runTest(mainDispatcher) {
        val order = acceptedOrder()
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals("042", content.displayNumber)
        assertEquals(Money.ofCents(1_400).formatEur(), content.totalLabel)
        assertEquals("15/09/2026 12:30", content.acceptedAtLabel)
    }

    @Test
    fun `ARCH-T012 old accepted is unavailable`() = runTest(mainDispatcher) {
        val order = acceptedOrder(businessDate = LocalDate.parse("2026-09-14"))
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()
        assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `ARCH-T013 draft is unavailable`() = runTest(mainDispatcher) {
        val draft = acceptedOrder().copy(
            status = OrderStatus.DRAFT,
            displayNumber = null,
            acceptedAt = null,
            businessDate = null,
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(draft))
        advanceUntilIdle()
        assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `ARCH-T014 missing and purged mid-open become unavailable without stale content`() =
        runTest(mainDispatcher) {
            val order = acceptedOrder()
            val flow = MutableStateFlow<Order?>(order)
            val viewModel = viewModel(orderFlow = flow)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Content)

            flow.value = null
            advanceUntilIdle()
            assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
        }

    @Test
    fun `ARCH-T015 snapshot names and prices ignore catalog changes`() = runTest(mainDispatcher) {
        val order = acceptedOrder(
            items = listOf(
                item(
                    id = "p1",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    name = "Margherita Snapshot",
                    printed = "MARGHERITA SNAP",
                    unitCents = 700,
                ),
            ),
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals("Margherita Snapshot", line.productName)
        assertEquals("MARGHERITA SNAP", line.productPrintedName)
        assertEquals(Money.ofCents(700), line.finalUnitPrice)
    }

    @Test
    fun `ARCH-T016 generalNote shown when present`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(acceptedOrder(generalNote = "Consegna alle 21")),
        )
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals("Consegna alle 21", content.generalNote)
    }

    @Test
    fun `ARCH-T017 item note shown when present`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(
                acceptedOrder(
                    items = listOf(
                        item(
                            id = "p1",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            note = "Ben cotta",
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals("Ben cotta", line.note)
    }

    @Test
    fun `ARCH-T018 additions and removals use accepted snapshots by displayOrder`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                orderFlow = MutableStateFlow(
                    acceptedOrder(
                        items = listOf(
                            item(
                                id = "p1",
                                category = ProductCategory.PIZZA,
                                sequence = 1,
                                additions = listOf(
                                    OrderItemAddition(
                                        id = "a2",
                                        additionId = 2,
                                        nameSnapshot = "Seconda",
                                        printedNameSnapshot = "SECONDA",
                                        listedPrice = Money.ofCents(50),
                                        chargedPrice = Money.ofCents(50),
                                        displayOrder = 1,
                                    ),
                                    OrderItemAddition(
                                        id = "a1",
                                        additionId = 1,
                                        nameSnapshot = "Prima",
                                        printedNameSnapshot = "PRIMA",
                                        listedPrice = Money.ofCents(100),
                                        chargedPrice = Money.ofCents(100),
                                        displayOrder = 0,
                                    ),
                                ),
                                removals = listOf(
                                    OrderItemRemoval(
                                        id = "r2",
                                        ingredientId = 2,
                                        nameSnapshot = "Secondo",
                                        displayOrder = 1,
                                    ),
                                    OrderItemRemoval(
                                        id = "r1",
                                        ingredientId = 1,
                                        nameSnapshot = "Primo",
                                        displayOrder = 0,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
                .sections.single().lines.single()
            assertEquals(listOf("Prima", "Seconda"), line.additionNames)
            assertEquals(listOf("Primo", "Secondo"), line.removalNames)
        }

    @Test
    fun `ARCH-T019 quantity finalUnitPrice and line total from snapshot`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(
                acceptedOrder(
                    items = listOf(
                        item(
                            id = "p1",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            qty = 2,
                            unitCents = 700,
                            manualCents = 650,
                        ),
                    ),
                    totalCents = 1_400,
                ),
            ),
        )
        advanceUntilIdle()
        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals(2, line.quantity)
        assertEquals(Money.ofCents(700), line.finalUnitPrice)
        assertEquals(Money.ofCents(1_400), line.lineTotal)
        assertEquals(Money.ofCents(650), line.manualUnitPrice)
    }

    @Test
    fun `ARCH-T020 category and createdSequence ordering`() = runTest(mainDispatcher) {
        val order = acceptedOrder(
            items = listOf(
                item(id = "b2", category = ProductCategory.BIBITA, sequence = 2, name = "Acqua"),
                item(id = "p2", category = ProductCategory.PIZZA, sequence = 2, name = "Diavola"),
                item(id = "f1", category = ProductCategory.FRITTURA, sequence = 1, name = "Crocche"),
                item(id = "p1", category = ProductCategory.PIZZA, sequence = 1, name = "Margherita"),
                item(id = "b1", category = ProductCategory.BIBITA, sequence = 1, name = "Coca"),
            ),
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals(listOf("PIZZE", "FRITTURA", "BIBITE"), content.sections.map { it.title })
        assertEquals(listOf("Margherita", "Diavola"), content.sections[0].lines.map { it.productName })
        assertEquals(listOf("Crocche"), content.sections[1].lines.map { it.productName })
        assertEquals(listOf("Coca", "Acqua"), content.sections[2].lines.map { it.productName })
    }

    @Test
    fun `settings failure maps to error not unavailable`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(acceptedOrder()),
            settingsFail = true,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Error)
    }

    private fun viewModel(
        orderFlow: MutableStateFlow<Order?>,
        settingsFail: Boolean = false,
        orderId: String = ORDER_ID,
    ): AcceptedOrderDetailViewModel {
        val repository = FakeOrderRepository(orderFlow)
        return AcceptedOrderDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(AcceptedOrderDetailViewModel.ORDER_ID_ARGUMENT to orderId),
            ),
            getCurrentDayAcceptedOrder = GetCurrentDayAcceptedOrder(
                orderRepository = repository,
                settingsRepository = FakeSettingsRepository(fail = settingsFail),
                clockProvider = FixedClock(romeLocal("2026-09-15T18:00:00")),
            ),
        )
    }

    private class FixedClock(private val now: Instant) : ClockProvider {
        override fun now(): Instant = now
    }

    private class FakeSettingsRepository(
        private val fail: Boolean = false,
    ) : SettingsRepository {
        override fun observeNumberingMode() = flowOf(NumberingModeLoadResult.PersistenceFailure)

        override suspend fun getNumberingMode() = NumberingModeLoadResult.PersistenceFailure

        override suspend fun getBusinessDaySettings(): BusinessDaySettingsLoadResult =
            if (fail) {
                BusinessDaySettingsLoadResult.PersistenceFailure
            } else {
                BusinessDaySettingsLoadResult.Loaded(
                    BusinessDaySettings(
                        timezoneId = "Europe/Rome",
                        businessDayStartMinutes = 300,
                    ),
                )
            }

        override suspend fun updateNumberingMode(
            mode: it.krpng.cassa.domain.model.NumberingMode,
        ): UpdateNumberingModeResult = UpdateNumberingModeResult.PersistenceFailure
    }

    private class FakeOrderRepository(
        private val orderFlow: MutableStateFlow<Order?>,
    ) : OrderRepository {
        override suspend fun getById(orderId: String): Order? = orderFlow.value

        override fun observeById(orderId: String): Flow<Order?> = orderFlow

        override fun observeActiveDraft(): Flow<Order?> = flowOf(null)

        override suspend fun getActiveDraft(): Order? = null

        override suspend fun createDraft(): CreateDraftResult = CreateDraftResult.AlreadyExists

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult =
            DeleteDraftResult.NotFoundOrNotDraft

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult =
            ReplaceDraftResult.OriginalNotFoundOrNotDraft

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult = QuickAddStandardResult.OrderNotFound

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult = UpdateOrderItemResult.OrderNotFound

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): SplitStandardPizzaItemResult = SplitStandardPizzaItemResult.OrderNotFound

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): ChangeQuantityResult = ChangeQuantityResult.OrderNotFound

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): RemoveOrderItemResult = RemoveOrderItemResult.OrderNotFound

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult = UpdateGeneralNoteResult.OrderNotFound

        override suspend fun acceptOrder(orderId: String): AcceptOrderResult =
            AcceptOrderResult.OrderNotFound

        override fun observeAcceptedByBusinessDate(
            businessDate: LocalDate,
        ) = flowOf(emptyList<it.krpng.cassa.domain.model.AcceptedOrderSummary>())

        override suspend fun purgeAcceptedBefore(
            currentBusinessDate: LocalDate,
        ): PurgeAcceptedBeforeResult = PurgeAcceptedBeforeResult.Purged(0)
    }

    private companion object {
        const val ORDER_ID = "accepted-1"
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")
        private val NOW: Instant = romeLocal("2026-09-15T12:30:00")

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()

        fun acceptedOrder(
            businessDate: LocalDate = LocalDate.parse("2026-09-15"),
            generalNote: String? = null,
            items: List<OrderItem> = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1),
            ),
            totalCents: Long = 1_400,
        ): Order = Order(
            id = ORDER_ID,
            status = OrderStatus.ACCEPTED,
            displayNumber = "042",
            numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
            numberingCycle = null,
            businessDate = businessDate,
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = NOW,
            total = Money.ofCents(totalCents),
            generalNote = generalNote,
            sourceOrderId = null,
            items = items,
        )

        fun item(
            id: String,
            category: ProductCategory,
            sequence: Int,
            name: String = "Prodotto $id",
            printed: String = name.uppercase(),
            note: String? = null,
            additions: List<OrderItemAddition> = emptyList(),
            removals: List<OrderItemRemoval> = emptyList(),
            unitCents: Long = 700,
            qty: Int = 1,
            manualCents: Long? = null,
        ): OrderItem = OrderItem(
            id = id,
            productId = null,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printed,
            categorySnapshot = category,
            quantity = qty,
            baseUnitPrice = Money.ofCents(unitCents),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = manualCents?.let(Money::ofCents),
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = true,
            note = note,
            createdSequence = sequence,
            additions = additions,
            removals = removals,
        )
    }
}
