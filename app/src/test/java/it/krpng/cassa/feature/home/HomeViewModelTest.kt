package it.krpng.cassa.feature.home

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HomeViewModelTest {
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
    fun `non-empty draft is exposed as a banner summary without writes`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draftWithItems()))
            val viewModel = HomeViewModel(repository)
            collectState(viewModel)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("draft-id", state.activeDraft?.draftId)
            assertEquals(3, state.activeDraft?.itemCount)
            assertEquals(Money.ofCents(2_200), state.activeDraft?.total)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `missing and empty drafts do not produce a banner or writes`() =
        runTest(mainDispatcher) {
            val source = MutableStateFlow<Order?>(null)
            val repository = FakeOrderRepository(source)
            val viewModel = HomeViewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.activeDraft)

            source.value = emptyDraft()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.activeDraft)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `home follows repository emissions while preserving the same draft id`() =
        runTest(mainDispatcher) {
            val source = MutableStateFlow<Order?>(null)
            val repository = FakeOrderRepository(source)
            val viewModel = HomeViewModel(repository)
            collectState(viewModel)
            advanceUntilIdle()

            source.value = draftWithItems()
            advanceUntilIdle()
            assertEquals("draft-id", viewModel.uiState.value.activeDraft?.draftId)

            source.value = draftWithItems().copy(total = Money.ofCents(2_500))
            advanceUntilIdle()
            assertEquals("draft-id", viewModel.uiState.value.activeDraft?.draftId)
            assertEquals(Money.ofCents(2_500), viewModel.uiState.value.activeDraft?.total)

            source.value = null
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.activeDraft)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `repository failure becomes a safe home error`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(
            activeDrafts = flow { throw IllegalStateException("database unavailable") },
        )
        val viewModel = HomeViewModel(repository)
        collectState(viewModel)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.activeDraft)
        assertEquals("Impossibile controllare l'ordine in corso.", state.errorMessage)
        assertEquals(0, repository.createCalls)
        assertEquals(0, repository.deleteCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class FakeOrderRepository(
        private val activeDrafts: Flow<Order?>,
    ) : OrderRepository {
        var createCalls: Int = 0
        var deleteCalls: Int = 0

        override suspend fun getById(orderId: String): Order? = null

        override fun observeActiveDraft(): Flow<Order?> = activeDrafts

        override suspend fun getActiveDraft(): Order? = null

        override suspend fun createDraft(): CreateDraftResult {
            createCalls += 1
            return CreateDraftResult.AlreadyExists
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            deleteCalls += 1
            return DeleteDraftResult.NotFoundOrNotDraft
        }
    }

    private fun emptyDraft(): Order = Order(
        id = "empty-draft-id",
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

    private fun draftWithItems(): Order = emptyDraft().copy(
        id = "draft-id",
        total = Money.ofCents(2_200),
        items = listOf(
            orderItem(id = "item-1", quantity = 2, finalUnitPrice = Money.ofCents(700)),
            orderItem(id = "item-2", quantity = 1, finalUnitPrice = Money.ofCents(800)),
        ),
    )

    private fun orderItem(
        id: String,
        quantity: Int,
        finalUnitPrice: Money,
    ): OrderItem = OrderItem(
        id = id,
        productId = null,
        productNameSnapshot = "Prodotto $id",
        productPrintedNameSnapshot = "PRODOTTO $id",
        categorySnapshot = ProductCategory.PIZZA,
        quantity = quantity,
        baseUnitPrice = finalUnitPrice,
        automaticExtrasTotal = Money.ZERO,
        manualUnitPrice = null,
        finalUnitPrice = finalUnitPrice,
        automaticExtrasPricingSnapshot = true,
        note = null,
        createdSequence = 1,
        additions = emptyList(),
        removals = emptyList(),
    )
}
