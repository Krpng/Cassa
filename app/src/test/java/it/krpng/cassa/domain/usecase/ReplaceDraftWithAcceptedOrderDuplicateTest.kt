package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceDraftWithAcceptedOrderDuplicateTest {
    @Test
    fun mapsSuccessAndPassesExpectedDraftId() = runTest {
        val repo = RecordingRepository(
            ReplaceDraftWithAcceptedOrderDuplicateResult.Created("new-draft"),
        )
        val outcome = useCase(repo)("src", "expected-draft")
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.Success("new-draft"),
            outcome,
        )
        assertEquals("src", repo.lastSourceId)
        assertEquals(LocalDate.parse("2026-09-15"), repo.lastBusinessDate)
        assertEquals("expected-draft", repo.lastExpectedDraftId)
    }

    @Test
    fun blankSourceIsUnavailable() = runTest {
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.SourceUnavailable,
            useCase(RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.Created("x")))(
                "  ",
                "draft",
            ),
        )
    }

    @Test
    fun blankExpectedDraftIsMissing() = runTest {
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftMissing,
            useCase(RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.Created("x")))(
                "src",
                " ",
            ),
        )
    }

    @Test
    fun mapsDraftMissingChangedAndSourceUnavailable() = runTest {
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftMissing,
            useCase(
                RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.DraftMissing),
            )("src", "d"),
        )
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftChanged,
            useCase(
                RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.DraftChanged),
            )("src", "d"),
        )
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.SourceUnavailable,
            useCase(
                RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable),
            )("src", "d"),
        )
    }

    @Test
    fun settingsFailure() = runTest {
        assertEquals(
            ReplaceDraftWithAcceptedOrderDuplicateOutcome.SettingsFailure,
            useCase(
                RecordingRepository(ReplaceDraftWithAcceptedOrderDuplicateResult.Created("x")),
                settingsFail = true,
            )("src", "d"),
        )
    }

    private fun useCase(
        repo: RecordingRepository,
        settingsFail: Boolean = false,
    ): ReplaceDraftWithAcceptedOrderDuplicate = ReplaceDraftWithAcceptedOrderDuplicate(
        orderRepository = repo,
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
        private val result: ReplaceDraftWithAcceptedOrderDuplicateResult,
    ) : OrderRepository {
        var lastSourceId: String? = null
        var lastBusinessDate: LocalDate? = null
        var lastExpectedDraftId: String? = null

        override suspend fun replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
            expectedDraftId: String,
        ): ReplaceDraftWithAcceptedOrderDuplicateResult {
            lastSourceId = sourceOrderId
            lastBusinessDate = currentBusinessDate
            lastExpectedDraftId = expectedDraftId
            return result
        }

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
        ): DuplicateAcceptedOrderResult = DuplicateAcceptedOrderResult.SourceUnavailable

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
            flowOf(emptyList<it.krpng.cassa.domain.model.AcceptedOrderSummary>())
        override suspend fun purgeAcceptedBefore(currentBusinessDate: LocalDate) =
            PurgeAcceptedBeforeResult.Purged(0)
    }
}
