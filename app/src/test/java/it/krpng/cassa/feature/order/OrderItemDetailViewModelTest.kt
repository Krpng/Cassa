package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.usecase.UpdateOrderItem
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderItemDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the exact item using order and item ids`() = runTest(dispatcher) {
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.canSave)
        assertEquals("Margherita snapshot", state.productName)
        assertEquals("2", state.quantityInput)
        assertEquals("Ben cotta", state.note)
        assertEquals(Money.ofCents(800), state.automaticUnitPrice)
        assertNull(state.manualPriceInput)
        assertEquals(listOf("draft-id"), repository.requestedOrderIds)
    }

    @Test
    fun `quantity note and manual price are saved once and survive reopen`() =
        runTest(dispatcher) {
            val repository = FakeOrderRepository(draft())
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.updateQuantity("3")
            viewModel.updateNote("  Senza sale  ")
            viewModel.startManualPriceEdit()
            viewModel.updateManualPrice("6,00")
            viewModel.save()
            viewModel.save()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertEquals(1, repository.updates.size)
            assertEquals(
                UpdateCall("draft-id", "item-id", 3, "Senza sale", Money.ofCents(600)),
                repository.updates.single(),
            )

            val reopened = viewModel(repository)
            advanceUntilIdle()
            val reopenedState = reopened.uiState.value
            assertEquals("3", reopenedState.quantityInput)
            assertEquals("Senza sale", reopenedState.note)
            assertEquals("6,00", reopenedState.manualPriceInput)
        }

    @Test
    fun `invalid fields block repository writes`() = runTest(dispatcher) {
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.updateQuantity("0")
        viewModel.startManualPriceEdit()
        viewModel.updateManualPrice("7,123")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationErrors.hasErrors)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `accepted order and item from another order are not editable`() = runTest(dispatcher) {
        val acceptedRepository = FakeOrderRepository(draft().copy(status = OrderStatus.ACCEPTED))
        val acceptedViewModel = viewModel(acceptedRepository)
        advanceUntilIdle()

        assertFalse(acceptedViewModel.uiState.value.canSave)
        acceptedViewModel.save()
        assertTrue(acceptedRepository.updates.isEmpty())

        val wrongItemRepository = FakeOrderRepository(draft())
        val wrongItemViewModel = viewModel(wrongItemRepository, itemId = "other-item")
        advanceUntilIdle()

        assertFalse(wrongItemViewModel.uiState.value.canSave)
        assertTrue(wrongItemRepository.updates.isEmpty())
    }

    @Test
    fun `persistence failure leaves the editor available for retry`() = runTest(dispatcher) {
        val repository = FakeOrderRepository(draft()).apply {
            updateResult = UpdateOrderItemResult.PersistenceFailure
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canSave)
        assertFalse(state.isSaving)
        assertEquals("Impossibile salvare la riga. Riprova.", state.errorMessage)
    }

    private fun viewModel(
        repository: OrderRepository,
        itemId: String = "item-id",
    ): OrderItemDetailViewModel = OrderItemDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                OrderItemDetailViewModel.ORDER_ID_ARGUMENT to "draft-id",
                OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT to itemId,
            ),
        ),
        orderRepository = repository,
        updateOrderItem = UpdateOrderItem(repository),
    )

    private class FakeOrderRepository(initialOrder: Order?) : OrderRepository {
        private var order: Order? = initialOrder
        val requestedOrderIds = mutableListOf<String>()
        val updates = mutableListOf<UpdateCall>()
        var updateResult: UpdateOrderItemResult = UpdateOrderItemResult.Updated

        override suspend fun getById(orderId: String): Order? {
            requestedOrderIds += orderId
            return order?.takeIf { it.id == orderId }
        }

        override fun observeById(orderId: String): Flow<Order?> = MutableStateFlow(order)

        override fun observeActiveDraft(): Flow<Order?> = MutableStateFlow(order)

        override suspend fun getActiveDraft(): Order? = order

        override suspend fun createDraft(): CreateDraftResult = error("Not used")

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult = error("Not used")

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult = error("Not used")

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult = error("Not used")

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
        ): UpdateOrderItemResult {
            updates += UpdateCall(orderId, orderItemId, quantity, note, manualUnitPrice)
            if (updateResult == UpdateOrderItemResult.Updated) {
                order = order?.copy(
                    items = order.orEmptyItems().map { item ->
                        if (item.id == orderItemId) {
                            item.copy(
                                quantity = quantity,
                                note = note,
                                manualUnitPrice = manualUnitPrice,
                                finalUnitPrice = manualUnitPrice ?: item.baseUnitPrice +
                                    item.automaticExtrasTotal,
                            )
                        } else {
                            item
                        }
                    },
                )
            }
            return updateResult
        }

        private fun Order?.orEmptyItems(): List<OrderItem> = this?.items.orEmpty()
    }

    private data class UpdateCall(
        val orderId: String,
        val itemId: String,
        val quantity: Int,
        val note: String?,
        val manualPrice: Money?,
    )

    private fun draft(): Order = Order(
        id = "draft-id",
        status = OrderStatus.DRAFT,
        displayNumber = null,
        numberingMode = null,
        numberingCycle = null,
        businessDate = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        acceptedAt = null,
        total = Money.ZERO,
        generalNote = null,
        sourceOrderId = null,
        items = listOf(
            OrderItem(
                id = "item-id",
                productId = 42,
                productNameSnapshot = "Margherita snapshot",
                productPrintedNameSnapshot = "MARGHERITA SNAPSHOT",
                categorySnapshot = ProductCategory.PIZZA,
                quantity = 2,
                baseUnitPrice = Money.ofCents(700),
                automaticExtrasTotal = Money.ofCents(100),
                manualUnitPrice = null,
                finalUnitPrice = Money.ofCents(800),
                automaticExtrasPricingSnapshot = true,
                note = "Ben cotta",
                createdSequence = 1,
                additions = emptyList(),
                removals = emptyList(),
            ),
        ),
    )
}
