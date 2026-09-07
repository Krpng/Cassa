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
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewOrderViewModelTest {
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
    fun `loads the requested draft id and reflects repository emissions without writes`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(draft())
            val repository = FakeOrderRepository(orders)
            val viewModel = viewModel(repository, "draft-id")

            advanceUntilIdle()

            val initial = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals("draft-id", initial.draftId)
            assertTrue(initial.isEmpty)
            assertEquals(listOf("draft-id"), repository.observedIds)

            orders.value = draft().copy(items = listOf(orderItem()))
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as NewOrderUiState.Ready).isEmpty)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
            assertEquals(0, repository.replaceCalls)
        }

    @Test
    fun `missing id and deleted draft are handled without creating another draft`() =
        runTest(mainDispatcher) {
            val missingIdRepository = FakeOrderRepository(MutableStateFlow(draft()))
            val missingIdViewModel = viewModel(missingIdRepository, "")
            advanceUntilIdle()

            assertEquals(NewOrderUiState.NotFound, missingIdViewModel.uiState.value)
            assertTrue(missingIdRepository.observedIds.isEmpty())

            val orders = MutableStateFlow<Order?>(draft())
            val deletedRepository = FakeOrderRepository(orders)
            val deletedViewModel = viewModel(deletedRepository, "draft-id")
            advanceUntilIdle()
            orders.value = null
            advanceUntilIdle()

            assertEquals(NewOrderUiState.NotFound, deletedViewModel.uiState.value)
            assertEquals(0, deletedRepository.createCalls)
        }

    @Test
    fun `accepted order is never exposed as an editable draft`() = runTest(mainDispatcher) {
        val accepted = draft().copy(status = OrderStatus.ACCEPTED)
        val repository = FakeOrderRepository(MutableStateFlow(accepted))

        val viewModel = viewModel(repository, accepted.id)
        advanceUntilIdle()

        assertEquals(NewOrderUiState.NotEditable, viewModel.uiState.value)
        assertEquals(0, repository.createCalls)
        assertEquals(0, repository.deleteCalls)
        assertEquals(0, repository.replaceCalls)
    }

    @Test
    fun `repository failure is safe and retry re-subscribes to the same id`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                observedOrders = flow { error("database unavailable") },
            )
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            assertEquals(
                NewOrderUiState.Failure("Impossibile caricare l'ordine."),
                viewModel.uiState.value,
            )

            repository.observedOrders = MutableStateFlow(draft())
            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is NewOrderUiState.Ready)
            assertEquals(listOf("draft-id", "draft-id"), repository.observedIds)
        }

    private fun viewModel(
        repository: OrderRepository,
        draftId: String,
    ): NewOrderViewModel = NewOrderViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(NewOrderViewModel.DRAFT_ID_ARGUMENT to draftId),
        ),
        orderRepository = repository,
    )

    private class FakeOrderRepository(
        var observedOrders: Flow<Order?>,
    ) : OrderRepository {
        val observedIds = mutableListOf<String>()
        var createCalls = 0
        var deleteCalls = 0
        var replaceCalls = 0

        override suspend fun getById(orderId: String): Order? = null

        override fun observeById(orderId: String): Flow<Order?> {
            observedIds += orderId
            return observedOrders
        }

        override fun observeActiveDraft(): Flow<Order?> = MutableStateFlow(null)

        override suspend fun getActiveDraft(): Order? = null

        override suspend fun createDraft(): CreateDraftResult {
            createCalls += 1
            return CreateDraftResult.AlreadyExists
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            deleteCalls += 1
            return DeleteDraftResult.NotFoundOrNotDraft
        }

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult {
            replaceCalls += 1
            return ReplaceDraftResult.OriginalNotFoundOrNotDraft
        }
    }

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
        items = emptyList(),
    )

    private fun orderItem(): OrderItem = OrderItem(
        id = "item-id",
        productId = null,
        productNameSnapshot = "Margherita",
        productPrintedNameSnapshot = "MARGHERITA",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = 1,
        baseUnitPrice = Money.ofCents(700),
        automaticExtrasTotal = Money.ZERO,
        manualUnitPrice = null,
        finalUnitPrice = Money.ofCents(700),
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = 1,
        additions = emptyList(),
        removals = emptyList(),
    )
}
