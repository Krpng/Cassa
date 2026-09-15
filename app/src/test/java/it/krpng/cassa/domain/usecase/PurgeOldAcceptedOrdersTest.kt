package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.core.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RET-T007 wiring: confirms PurgeOldAcceptedOrders uses BusinessDateCalculator cutoff.
 * Calculator contract itself: BusinessDateCalculatorTest DATE-001 / DATE-002.
 */
class PurgeOldAcceptedOrdersTest {
    @Test
    fun `RET-T007 use case at 04-59 uses previous calendar day as currentBusinessDate`() = runTest {
        val repository = RecordingOrderRepository()
        val useCase = PurgeOldAcceptedOrders(
            orderRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            clockProvider = FixedClock(romeLocal("2026-09-15T04:59:00")),
        )

        val result = useCase()

        assertTrue(result is PurgeOldAcceptedOrdersResult.Completed)
        val completed = result as PurgeOldAcceptedOrdersResult.Completed
        assertEquals(LocalDate.parse("2026-09-14"), completed.currentBusinessDate)
        assertEquals(LocalDate.parse("2026-09-14"), repository.purgedBefore.single())
    }

    @Test
    fun `RET-T007 use case at 05-00 uses calendar day as currentBusinessDate`() = runTest {
        val repository = RecordingOrderRepository()
        val useCase = PurgeOldAcceptedOrders(
            orderRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            clockProvider = FixedClock(romeLocal("2026-09-15T05:00:00")),
        )

        val result = useCase()

        assertTrue(result is PurgeOldAcceptedOrdersResult.Completed)
        val completed = result as PurgeOldAcceptedOrdersResult.Completed
        assertEquals(LocalDate.parse("2026-09-15"), completed.currentBusinessDate)
        assertEquals(LocalDate.parse("2026-09-15"), repository.purgedBefore.single())
    }

    @Test
    fun `settings failure does not call repository purge`() = runTest {
        val repository = RecordingOrderRepository()
        val useCase = PurgeOldAcceptedOrders(
            orderRepository = repository,
            settingsRepository = FakeSettingsRepository(fail = true),
            clockProvider = FixedClock(romeLocal("2026-09-15T18:00:00")),
        )

        assertEquals(PurgeOldAcceptedOrdersResult.SettingsFailure, useCase())
        assertTrue(repository.purgedBefore.isEmpty())
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

    private class RecordingOrderRepository : OrderRepository {
        val purgedBefore = mutableListOf<LocalDate>()

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
        ): Flow<List<AcceptedOrderSummary>> = flowOf(emptyList())

        override suspend fun purgeAcceptedBefore(
            currentBusinessDate: LocalDate,
        ): PurgeAcceptedBeforeResult {
            purgedBefore += currentBusinessDate
            return PurgeAcceptedBeforeResult.Purged(deletedOrderCount = 0)
        }

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
        ): DuplicateAcceptedOrderResult = DuplicateAcceptedOrderResult.SourceUnavailable

        override suspend fun replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
            expectedDraftId: String,
        ): ReplaceDraftWithAcceptedOrderDuplicateResult =
            ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable
    }

    private companion object {
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()
    }
}
