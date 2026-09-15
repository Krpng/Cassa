package it.krpng.cassa.app.retention

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.AcceptOrderResult
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
import it.krpng.cassa.domain.usecase.PurgeOldAcceptedOrders
import it.krpng.cassa.domain.usecase.PurgeOldAcceptedOrdersResult
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptedOrdersRetentionInitializerTest {
    @Test
    fun onAppStartInvokesPurge() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CountingPurgeUseCase()
        val initializer = AcceptedOrdersRetentionInitializer(
            purgeOldAcceptedOrders = useCase,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        )

        initializer.onAppStart()
        advanceUntilIdle()

        assertEquals(1, useCase.calls.get())
    }

    @Test
    fun failureDoesNotCrashAndAllowsRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CountingPurgeUseCase(throwOnCall = true)
        val initializer = AcceptedOrdersRetentionInitializer(
            purgeOldAcceptedOrders = useCase,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        )

        initializer.onAppStart()
        advanceUntilIdle()
        assertEquals(1, useCase.calls.get())

        useCase.throwOnCall = false
        initializer.onAppStart()
        advanceUntilIdle()
        assertEquals(2, useCase.calls.get())
    }

    @Test
    fun successCompletesWithoutLeavingInFlight() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CountingPurgeUseCase()
        val initializer = AcceptedOrdersRetentionInitializer(
            purgeOldAcceptedOrders = useCase,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        )

        initializer.onAppStart()
        advanceUntilIdle()

        assertFalse(initializer.isPurgeInFlight())
        assertEquals(1, useCase.calls.get())
    }

    @Test
    fun repeatedOnStartWhileInFlightDoesNotStartDuplicate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CountingPurgeUseCase(delayMs = 1_000)
        val initializer = AcceptedOrdersRetentionInitializer(
            purgeOldAcceptedOrders = useCase,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        )

        initializer.onAppStart()
        testScheduler.runCurrent()
        assertTrue(initializer.isPurgeInFlight())

        initializer.onAppStart()
        testScheduler.runCurrent()
        assertEquals(1, useCase.calls.get())

        advanceUntilIdle()
        assertFalse(initializer.isPurgeInFlight())
        assertEquals(1, useCase.calls.get())
    }

    private class CountingPurgeUseCase(
        var throwOnCall: Boolean = false,
        private val delayMs: Long = 0,
    ) : PurgeOldAcceptedOrders(
        orderRepository = UnusedOrderRepository,
        settingsRepository = UnusedSettingsRepository,
        clockProvider = object : ClockProvider {
            override fun now(): Instant = Instant.EPOCH
        },
    ) {
        val calls = AtomicInteger(0)

        override suspend fun invoke(): PurgeOldAcceptedOrdersResult {
            calls.incrementAndGet()
            if (delayMs > 0) delay(delayMs)
            if (throwOnCall) {
                throw IllegalStateException("forced failure")
            }
            return PurgeOldAcceptedOrdersResult.Completed(
                currentBusinessDate = LocalDate.parse("2026-09-15"),
                deletedOrderCount = 0,
            )
        }
    }

    private object UnusedOrderRepository : OrderRepository {
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
        ): PurgeAcceptedBeforeResult = PurgeAcceptedBeforeResult.Purged(0)
    }

    private object UnusedSettingsRepository : SettingsRepository {
        override fun observeNumberingMode() = flowOf(NumberingModeLoadResult.PersistenceFailure)

        override suspend fun getNumberingMode() = NumberingModeLoadResult.PersistenceFailure

        override suspend fun getBusinessDaySettings() =
            BusinessDaySettingsLoadResult.PersistenceFailure

        override suspend fun updateNumberingMode(mode: NumberingMode) =
            UpdateNumberingModeResult.PersistenceFailure
    }
}
