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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftRecoveryViewModelTest {
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
    fun `non-empty persisted draft is offered for recovery without creating another`() =
        runTest(mainDispatcher) {
            val draft = draftWithItem()
            val repository = FakeOrderRepository(MutableStateFlow(draft))
            val viewModel = DraftRecoveryViewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value as DraftRecoveryUiState.DraftAvailable
            assertEquals(draft.id, state.draft.id)
            assertEquals(Money.ofCents(850), state.draft.items.single().manualUnitPrice)
            assertEquals(0, repository.createCalls)
            assertTrue(repository.deletedIds.isEmpty())
        }

    @Test
    fun `a new startup observer recovers the same draft id and children`() =
        runTest(mainDispatcher) {
            val persistedDraft = draftWithItem()
            val repository = FakeOrderRepository(MutableStateFlow(persistedDraft))

            val firstStartup = DraftRecoveryViewModel(repository)
            advanceUntilIdle()
            val first = firstStartup.uiState.value as DraftRecoveryUiState.DraftAvailable

            val recreatedStartup = DraftRecoveryViewModel(repository)
            advanceUntilIdle()
            val recovered = recreatedStartup.uiState.value as DraftRecoveryUiState.DraftAvailable

            assertEquals(first.draft.id, recovered.draft.id)
            assertEquals(first.draft.items, recovered.draft.items)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `missing or empty draft does not show recovery and is not auto-deleted`() =
        runTest(mainDispatcher) {
            val missingRepository = FakeOrderRepository(MutableStateFlow(null))
            val emptyRepository = FakeOrderRepository(MutableStateFlow(emptyDraft()))

            val missingViewModel = DraftRecoveryViewModel(missingRepository)
            val emptyViewModel = DraftRecoveryViewModel(emptyRepository)
            advanceUntilIdle()

            assertSame(DraftRecoveryUiState.NoRecoveryNeeded, missingViewModel.uiState.value)
            assertSame(DraftRecoveryUiState.NoRecoveryNeeded, emptyViewModel.uiState.value)
            assertTrue(missingRepository.deletedIds.isEmpty())
            assertTrue(emptyRepository.deletedIds.isEmpty())
        }

    @Test
    fun `delete first tap asks confirmation and cancel preserves draft`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draftWithItem()))
            val viewModel = DraftRecoveryViewModel(repository)
            advanceUntilIdle()

            viewModel.requestDelete()
            assertTrue(
                (viewModel.uiState.value as DraftRecoveryUiState.DraftAvailable)
                    .showDeleteConfirmation,
            )
            assertTrue(repository.deletedIds.isEmpty())

            viewModel.cancelDelete()

            val state = viewModel.uiState.value as DraftRecoveryUiState.DraftAvailable
            assertFalse(state.showDeleteConfirmation)
            assertTrue(repository.deletedIds.isEmpty())
        }

    @Test
    fun `confirmed delete removes only the offered draft and completes recovery`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draftWithItem()))
            val viewModel = DraftRecoveryViewModel(repository)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("draft-id"), repository.deletedIds)
            assertSame(DraftRecoveryUiState.NoRecoveryNeeded, viewModel.uiState.value)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `rejected delete remains recoverable and exposes a safe error`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                activeDraft = MutableStateFlow(draftWithItem()),
                deleteResult = DeleteDraftResult.NotFoundOrNotDraft,
            )
            val viewModel = DraftRecoveryViewModel(repository)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            val state = viewModel.uiState.value as DraftRecoveryUiState.DraftAvailable
            assertFalse(state.isDeleting)
            assertEquals("L'ordine in corso non è più disponibile.", state.errorMessage)
        }

    private class FakeOrderRepository(
        private val activeDraft: MutableStateFlow<Order?>,
        private val deleteResult: DeleteDraftResult = DeleteDraftResult.Deleted,
    ) : OrderRepository {
        var createCalls: Int = 0
        val deletedIds = mutableListOf<String>()

        override suspend fun getById(orderId: String): Order? = activeDraft.value

        override fun observeActiveDraft(): Flow<Order?> = activeDraft

        override suspend fun getActiveDraft(): Order? = activeDraft.value

        override suspend fun createDraft(): CreateDraftResult {
            createCalls += 1
            return CreateDraftResult.AlreadyExists
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            deletedIds += orderId
            if (deleteResult == DeleteDraftResult.Deleted) {
                activeDraft.value = null
            }
            return deleteResult
        }

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult =
            ReplaceDraftResult.OriginalNotFoundOrNotDraft
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

    private fun draftWithItem(): Order = emptyDraft().copy(
        id = "draft-id",
        items = listOf(
            OrderItem(
                id = "item-id",
                productId = null,
                productNameSnapshot = "Margherita",
                productPrintedNameSnapshot = "MARGHERITA",
                categorySnapshot = ProductCategory.PIZZA,
                quantity = 1,
                baseUnitPrice = Money.ofCents(700),
                automaticExtrasTotal = Money.ZERO,
                manualUnitPrice = Money.ofCents(850),
                finalUnitPrice = Money.ofCents(850),
                automaticExtrasPricingSnapshot = true,
                note = "Ben cotta",
                createdSequence = 1,
                additions = emptyList(),
                removals = emptyList(),
            ),
        ),
    )
}
