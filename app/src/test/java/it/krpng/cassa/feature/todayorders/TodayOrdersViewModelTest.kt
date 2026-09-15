package it.krpng.cassa.feature.todayorders

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
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
class TodayOrdersViewModelTest {
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
    fun `ARCH-T001 today lists only accepted of current businessDate`() = runTest(mainDispatcher) {
        val clock = FixedClock(romeLocal("2026-09-15T18:00:00"))
        val today = LocalDate.parse("2026-09-15")
        val yesterday = LocalDate.parse("2026-09-14")
        val repository = FakeOrderRepository(
            listOf(
                summary("a", "003", today, clock.now(), 900),
                summary("b", "001", yesterday, clock.now().minusSeconds(3_600), 700),
                summary("c", "002", today, clock.now().minusSeconds(60), 800),
            ),
        )
        val viewModel = TodayOrdersViewModel(
            orderRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            clockProvider = clock,
        )
        advanceUntilIdle()

        val content = viewModel.uiState.value as TodayOrdersUiState.Content
        assertEquals(today, content.businessDate)
        assertEquals(listOf("003", "002"), content.rows.map { it.displayNumber })
        assertEquals(today.toString(), repository.observedBusinessDates.single())
    }

    @Test
    fun `ARCH-T002 at 02-00 today uses previous businessDate`() = runTest(mainDispatcher) {
        val clock = FixedClock(romeLocal("2026-09-15T02:00:00"))
        val previous = LocalDate.parse("2026-09-14")
        val repository = FakeOrderRepository(
            listOf(summary("a", "001", previous, clock.now(), 700)),
        )
        val viewModel = TodayOrdersViewModel(
            orderRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            clockProvider = clock,
        )
        advanceUntilIdle()

        val content = viewModel.uiState.value as TodayOrdersUiState.Content
        assertEquals(previous, content.businessDate)
        assertEquals(listOf("001"), content.rows.map { it.displayNumber })
        assertEquals(previous.toString(), repository.observedBusinessDates.single())
    }

    @Test
    fun `ARCH-T003 orders are newest acceptedAt first independent of displayNumber`() =
        runTest(mainDispatcher) {
            val t1 = romeLocal("2026-09-15T10:00:00")
            val t2 = romeLocal("2026-09-15T11:00:00")
            val t3 = romeLocal("2026-09-15T12:00:00")
            val today = LocalDate.parse("2026-09-15")
            val repository = FakeOrderRepository(
                listOf(
                    summary("old", "099", today, t1, 100),
                    summary("mid", "001", today, t2, 200),
                    summary("new", "050", today, t3, 300),
                ),
            )
            val viewModel = TodayOrdersViewModel(
                orderRepository = repository,
                settingsRepository = FakeSettingsRepository(),
                clockProvider = FixedClock(romeLocal("2026-09-15T18:00:00")),
            )
            advanceUntilIdle()

            val content = viewModel.uiState.value as TodayOrdersUiState.Content
            assertEquals(listOf("050", "001", "099"), content.rows.map { it.displayNumber })
            assertEquals(listOf("12:00", "11:00", "10:00"), content.rows.map { it.acceptedAtLabel })
        }

    @Test
    fun `empty businessDate shows empty state`() = runTest(mainDispatcher) {
        val viewModel = TodayOrdersViewModel(
            orderRepository = FakeOrderRepository(emptyList()),
            settingsRepository = FakeSettingsRepository(),
            clockProvider = FixedClock(romeLocal("2026-09-15T18:00:00")),
        )
        advanceUntilIdle()

        val empty = viewModel.uiState.value as TodayOrdersUiState.Empty
        assertEquals(LocalDate.parse("2026-09-15"), empty.businessDate)
    }

    @Test
    fun `settings failure maps to error not empty`() = runTest(mainDispatcher) {
        val viewModel = TodayOrdersViewModel(
            orderRepository = FakeOrderRepository(emptyList()),
            settingsRepository = FakeSettingsRepository(fail = true),
            clockProvider = FixedClock(romeLocal("2026-09-15T18:00:00")),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TodayOrdersUiState.Error)
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
        private val all: List<AcceptedOrderSummary>,
    ) : OrderRepository {
        val observedBusinessDates = mutableListOf<String>()

        override suspend fun getById(orderId: String): Order? = null

        override fun observeById(orderId: String): Flow<Order?> = flowOf(null)

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
        ): Flow<List<AcceptedOrderSummary>> {
            observedBusinessDates += businessDate.toString()
            val ordered = all
                .filter { it.businessDate == businessDate }
                .sortedByDescending { it.acceptedAt }
            return MutableStateFlow(ordered)
        }
    }

    private companion object {
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()

        fun summary(
            id: String,
            displayNumber: String,
            businessDate: LocalDate,
            acceptedAt: Instant,
            totalCents: Long,
        ): AcceptedOrderSummary = AcceptedOrderSummary(
            id = id,
            displayNumber = displayNumber,
            acceptedAt = acceptedAt,
            total = Money.ofCents(totalCents),
            businessDate = businessDate,
        )
    }
}
