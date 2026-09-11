package it.krpng.cassa.feature.home

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
            assertEquals(0, repository.replaceCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `new order without a draft creates once and opens the created id`() =
        runTest(mainDispatcher) {
            val created = emptyDraft("new-draft-id")
            val repository = FakeOrderRepository(
                activeDrafts = MutableStateFlow(null),
                createResult = CreateDraftResult.Created(created),
            )
            val viewModel = HomeViewModel(repository)
            val event = async(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigationEvents.first()
            }

            viewModel.startNewOrder()
            viewModel.startNewOrder()
            advanceUntilIdle()

            assertEquals(HomeNavigationEvent.OpenDraft("new-draft-id"), event.await())
            assertEquals(1, repository.createCalls)
            assertEquals(0, repository.replaceCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `existing non-empty draft opens conflict and resume keeps the same id without writes`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draftWithItems()))
            val viewModel = HomeViewModel(repository)

            viewModel.startNewOrder()
            advanceUntilIdle()

            assertEquals("draft-id", viewModel.uiState.value.newOrderConflict?.draftId)
            assertEquals(0, repository.createCalls)

            val event = async(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigationEvents.first()
            }
            viewModel.resumeConflictingDraft()

            assertEquals(HomeNavigationEvent.OpenDraft("draft-id"), event.await())
            assertNull(viewModel.uiState.value.newOrderConflict)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.replaceCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `existing empty draft is reopened without conflict or replacement`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(emptyDraft("empty-draft-id")))
            val viewModel = HomeViewModel(repository)
            val event = async(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigationEvents.first()
            }

            viewModel.startNewOrder()
            advanceUntilIdle()

            assertEquals(HomeNavigationEvent.OpenDraft("empty-draft-id"), event.await())
            assertNull(viewModel.uiState.value.newOrderConflict)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.replaceCalls)
        }

    @Test
    fun `cancel conflict leaves the existing draft unchanged`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(draftWithItems()))
        val viewModel = HomeViewModel(repository)

        viewModel.startNewOrder()
        advanceUntilIdle()
        viewModel.cancelNewOrderConflict()

        assertNull(viewModel.uiState.value.newOrderConflict)
        assertEquals("draft-id", repository.activeDrafts.value?.id)
        assertEquals(0, repository.createCalls)
        assertEquals(0, repository.replaceCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `confirmed replacement is one repository operation and opens the new id once`() =
        runTest(mainDispatcher) {
            val replacement = emptyDraft("replacement-id")
            val repository = FakeOrderRepository(
                activeDrafts = MutableStateFlow(draftWithItems()),
                replaceResult = ReplaceDraftResult.Created(replacement),
            )
            val viewModel = HomeViewModel(repository)
            val events = mutableListOf<HomeNavigationEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigationEvents.collect(events::add)
            }

            viewModel.startNewOrder()
            advanceUntilIdle()
            viewModel.requestReplaceDraft()
            assertTrue(viewModel.uiState.value.showReplaceConfirmation)

            viewModel.confirmReplaceDraft()
            viewModel.confirmReplaceDraft()
            assertFalse(viewModel.uiState.value.showReplaceConfirmation)
            assertNull(viewModel.uiState.value.newOrderConflict)
            advanceUntilIdle()

            assertEquals(listOf(HomeNavigationEvent.OpenDraft("replacement-id")), events)
            assertEquals(listOf("draft-id"), repository.replacedIds)
            assertEquals(1, repository.replaceCalls)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)

            repository.activeDrafts.value = draftWithItems()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.newOrderConflict)
            assertFalse(viewModel.uiState.value.showReplaceConfirmation)
            assertEquals(listOf(HomeNavigationEvent.OpenDraft("replacement-id")), events)
        }

    @Test
    fun `replacement failure keeps conflict recoverable and does not create separately`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                activeDrafts = MutableStateFlow(draftWithItems()),
                replaceResult = ReplaceDraftResult.OriginalNotFoundOrNotDraft,
            )
            val viewModel = HomeViewModel(repository)

            viewModel.startNewOrder()
            advanceUntilIdle()
            viewModel.requestReplaceDraft()
            viewModel.confirmReplaceDraft()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("draft-id", state.newOrderConflict?.draftId)
            assertFalse(state.isNewOrderOperationInProgress)
            assertEquals("L'ordine in corso non è più disponibile.", state.errorMessage)
            assertEquals(1, repository.replaceCalls)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)

            viewModel.requestReplaceDraft()
            assertTrue(viewModel.uiState.value.showReplaceConfirmation)
        }

    @Test
    fun `create conflict reloads persisted draft and never creates a second one`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                activeDrafts = MutableStateFlow(null),
                createResult = CreateDraftResult.AlreadyExists,
                activeDraftAfterCreateConflict = draftWithItems(),
            )
            val viewModel = HomeViewModel(repository)

            viewModel.startNewOrder()
            advanceUntilIdle()

            assertEquals("draft-id", viewModel.uiState.value.newOrderConflict?.draftId)
            assertEquals(1, repository.createCalls)
            assertEquals(0, repository.replaceCalls)
        }

    @Test
    fun `repository observation failure becomes a safe home error`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(
            activeDrafts = MutableStateFlow(null),
            observedDrafts = flow { throw IllegalStateException("database unavailable") },
        )
        val viewModel = HomeViewModel(repository)
        collectState(viewModel)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.activeDraft)
        assertEquals("Impossibile controllare l'ordine in corso.", state.errorMessage)
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private class FakeOrderRepository(
        val activeDrafts: MutableStateFlow<Order?>,
        private val createResult: CreateDraftResult = CreateDraftResult.AlreadyExists,
        private val replaceResult: ReplaceDraftResult =
            ReplaceDraftResult.OriginalNotFoundOrNotDraft,
        private val activeDraftAfterCreateConflict: Order? = null,
        private val observedDrafts: Flow<Order?> = activeDrafts,
    ) : OrderRepository {
        var createCalls: Int = 0
        var replaceCalls: Int = 0
        var deleteCalls: Int = 0
        val replacedIds = mutableListOf<String>()

        override suspend fun getById(orderId: String): Order? = activeDrafts.value

        override fun observeById(orderId: String): Flow<Order?> = activeDrafts

        override fun observeActiveDraft(): Flow<Order?> = observedDrafts

        override suspend fun getActiveDraft(): Order? = activeDrafts.value

        override suspend fun createDraft(): CreateDraftResult {
            createCalls += 1
            if (createResult == CreateDraftResult.AlreadyExists) {
                activeDraftAfterCreateConflict?.let { activeDrafts.value = it }
            }
            return createResult
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            deleteCalls += 1
            return DeleteDraftResult.NotFoundOrNotDraft
        }

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult {
            replaceCalls += 1
            replacedIds += orderId
            if (replaceResult is ReplaceDraftResult.Created) {
                activeDrafts.value = replaceResult.draft
            }
            return replaceResult
        }

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
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: it.krpng.cassa.domain.repository.CustomizationQuantityIntent,
        ): UpdateOrderItemResult = error("Not used")

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult = error("Not used")

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): it.krpng.cassa.domain.repository.ChangeQuantityResult = error("Not used")

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): it.krpng.cassa.domain.repository.RemoveOrderItemResult = error("Not used")

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): it.krpng.cassa.domain.repository.UpdateGeneralNoteResult = error("Not used")
    }

    private fun emptyDraft(id: String = "empty-draft-id"): Order = Order(
        id = id,
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

    private fun draftWithItems(): Order = emptyDraft("draft-id").copy(
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
