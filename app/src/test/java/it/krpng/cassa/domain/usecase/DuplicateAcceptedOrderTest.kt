package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateAcceptedOrderTest {
    @Test
    fun `ARCH-T027 blank source is unavailable`() = runTest {
        val useCase = useCase(repoResult = DuplicateAcceptedOrderResult.Created("d1"))
        assertEquals(
            DuplicateAcceptedOrderOutcome.SourceUnavailable,
            useCase(""),
        )
    }

    @Test
    fun `ARCH-T027 settings failure maps to SettingsFailure`() = runTest {
        val useCase = useCase(
            settingsFail = true,
            repoResult = DuplicateAcceptedOrderResult.Created("d1"),
        )
        assertEquals(
            DuplicateAcceptedOrderOutcome.SettingsFailure,
            useCase("src"),
        )
    }

    @Test
    fun `ARCH-T027 passes currentBusinessDate from ClockProvider and settings`() = runTest {
        val repo = RecordingRepository(DuplicateAcceptedOrderResult.Created("new-draft"))
        val useCase = DuplicateAcceptedOrder(
            orderRepository = repo,
            settingsRepository = FakeSettings(),
            clockProvider = FixedClock(Instant.parse("2026-09-15T16:00:00Z")),
        )
        val outcome = useCase("accepted-1")
        assertEquals(DuplicateAcceptedOrderOutcome.Success("new-draft"), outcome)
        assertEquals("accepted-1", repo.lastSourceId)
        assertEquals(LocalDate.parse("2026-09-15"), repo.lastBusinessDate)
    }

    @Test
    fun mapsDraftConflict() = runTest {
        val useCase = useCase(repoResult = DuplicateAcceptedOrderResult.DraftConflict)
        assertEquals(DuplicateAcceptedOrderOutcome.DraftConflict, useCase("src"))
    }

    @Test
    fun mapsSourceUnavailable() = runTest {
        val useCase = useCase(repoResult = DuplicateAcceptedOrderResult.SourceUnavailable)
        assertEquals(DuplicateAcceptedOrderOutcome.SourceUnavailable, useCase("src"))
    }

    private fun useCase(
        repoResult: DuplicateAcceptedOrderResult,
        settingsFail: Boolean = false,
    ): DuplicateAcceptedOrder = DuplicateAcceptedOrder(
        orderRepository = RecordingRepository(repoResult),
        settingsRepository = FakeSettings(fail = settingsFail),
        clockProvider = FixedClock(Instant.parse("2026-09-15T16:00:00Z")),
    )

    private class FixedClock(private val now: Instant) : ClockProvider {
        override fun now(): Instant = now
    }

    private class FakeSettings(
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

        override suspend fun updateNumberingMode(mode: NumberingMode): UpdateNumberingModeResult =
            UpdateNumberingModeResult.PersistenceFailure
    }

    private class RecordingRepository(
        private val result: DuplicateAcceptedOrderResult,
    ) : OrderRepository {
        var lastSourceId: String? = null
        var lastBusinessDate: LocalDate? = null

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
        ): DuplicateAcceptedOrderResult {
            lastSourceId = sourceOrderId
            lastBusinessDate = currentBusinessDate
            return result
        }

        override suspend fun getById(orderId: String): Order? = null
        override fun observeById(orderId: String): Flow<Order?> = flowOf(null)
        override fun observeActiveDraft(): Flow<Order?> = flowOf(null)
        override suspend fun getActiveDraft(): Order? = null
        override suspend fun createDraft(): CreateDraftResult = CreateDraftResult.AlreadyExists
        override suspend fun deleteDraft(orderId: String) = DeleteDraftResult.NotFoundOrNotDraft
        override suspend fun replaceDraft(orderId: String) =
            ReplaceDraftResult.OriginalNotFoundOrNotDraft
        override suspend fun quickAddStandard(orderId: String, productId: Long) =
            QuickAddStandardResult.OrderNotFound
        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ) = UpdateOrderItemResult.OrderNotFound
        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ) = SplitStandardPizzaItemResult.OrderNotFound
        override suspend fun changeQuantity(orderId: String, orderItemId: String, quantity: Int) =
            ChangeQuantityResult.OrderNotFound
        override suspend fun removeOrderItem(orderId: String, orderItemId: String) =
            RemoveOrderItemResult.OrderNotFound
        override suspend fun updateGeneralNote(orderId: String, generalNote: String?) =
            UpdateGeneralNoteResult.OrderNotFound
        override suspend fun acceptOrder(orderId: String) = AcceptOrderResult.OrderNotFound
        override fun observeAcceptedByBusinessDate(businessDate: LocalDate) =
            flowOf(emptyList<AcceptedOrderSummary>())
        override suspend fun purgeAcceptedBefore(currentBusinessDate: LocalDate) =
            PurgeAcceptedBeforeResult.Purged(0)
    }
}
