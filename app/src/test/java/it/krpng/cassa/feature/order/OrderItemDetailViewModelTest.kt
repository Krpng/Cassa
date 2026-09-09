package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.AdditionRepository
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
                UpdateCall(
                    "draft-id",
                    "item-id",
                    3,
                    "Senza sale",
                    Money.ofCents(600),
                    emptyList(),
                ),
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

    @Test
    fun `pizza exposes only active additions including zero price and saves selection`() =
        runTest(dispatcher) {
            val singlePizza = draft().copy(
                items = draft().items.map { item -> item.copy(quantity = 1) },
            )
            val repository = FakeOrderRepository(singlePizza)
            val additions = listOf(
                addition(10, "Provola", 150),
                addition(11, "Basilico", 0),
            )
            val viewModel = viewModel(
                repository = repository,
                additionRepository = FakeAdditionRepository(additions),
            )
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertTrue(initial.canEditAdditions)
            assertEquals(listOf(10L, 11L), initial.additionOptions.map { it.id })
            assertEquals(Money.ZERO, initial.additionOptions.single { it.id == 11L }.price)

            viewModel.toggleAddition(10)
            viewModel.toggleAddition(11)
            viewModel.toggleAddition(10)
            viewModel.toggleAddition(10)
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(11L, 10L), repository.updates.single().selectedAdditionIds)
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `persisted pizza addition is selected again after reopening detail`() = runTest(dispatcher) {
        val persisted = OrderItemAddition(
            id = "relation-id",
            additionId = 10,
            nameSnapshot = "Provola storica",
            printedNameSnapshot = "PROVOLA STORICA",
            listedPrice = Money.ofCents(150),
            chargedPrice = Money.ofCents(150),
            displayOrder = 0,
        )
        val repository = FakeOrderRepository(
            draft().copy(
                items = draft().items.map { item -> item.copy(additions = listOf(persisted)) },
            ),
        )
        val additions = FakeAdditionRepository(listOf(addition(10, "Provola nuova", 200)))

        val reopened = viewModel(repository, additionRepository = additions)
        advanceUntilIdle()

        assertEquals(listOf(10L), reopened.uiState.value.selectedAdditionIds)
        assertTrue(reopened.uiState.value.additionOptions.single().isSelected)
    }

    @Test
    fun `non pizza and ambiguous or deferred pizza additions cannot be edited`() =
        runTest(dispatcher) {
            val active = FakeAdditionRepository(listOf(addition(10, "Provola", 150)))
            val nonPizza = draft().copy(
                items = draft().items.map { item ->
                    item.copy(categorySnapshot = ProductCategory.FRITTURA, quantity = 1)
                },
            )
            val nonPizzaViewModel = viewModel(FakeOrderRepository(nonPizza), additionRepository = active)
            advanceUntilIdle()
            assertTrue(nonPizzaViewModel.uiState.value.additionOptions.isEmpty())
            assertFalse(nonPizzaViewModel.uiState.value.canEditAdditions)

            val aggregated = draft().copy(
                items = draft().items.map { item -> item.copy(note = null) },
            )
            val aggregatedViewModel = viewModel(
                FakeOrderRepository(aggregated),
                additionRepository = active,
            )
            advanceUntilIdle()
            assertFalse(aggregatedViewModel.uiState.value.canEditAdditions)
            assertTrue(
                aggregatedViewModel.uiState.value.additionMessage.orEmpty()
                    .contains("contiene 2 pizze"),
            )

            aggregatedViewModel.updateQuantity("1")
            assertTrue(aggregatedViewModel.uiState.value.canEditAdditions)
            assertNull(aggregatedViewModel.uiState.value.additionMessage)

            val deferred = draft().copy(
                items = draft().items.map { item ->
                    item.copy(quantity = 1, automaticExtrasPricingSnapshot = false)
                },
            )
            val deferredViewModel = viewModel(
                FakeOrderRepository(deferred),
                additionRepository = active,
            )
            advanceUntilIdle()
            assertFalse(deferredViewModel.uiState.value.canEditAdditions)
            assertTrue(deferredViewModel.uiState.value.additionMessage.orEmpty().contains("dedicato"))
        }

    @Test
    fun `increasing customized pizza quantity requires confirmation and cancel does not write`() =
        runTest(dispatcher) {
            val repository = FakeOrderRepository(draftWithAddition(quantity = 1))
            val additions = FakeAdditionRepository(listOf(addition(10, "Provola", 150)))
            val viewModel = viewModel(repository, additionRepository = additions)
            advanceUntilIdle()

            viewModel.updateQuantity("2")
            viewModel.save()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(repository.updates.isEmpty())

            viewModel.cancelQuantityIncrease()
            assertFalse(viewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(repository.updates.isEmpty())

            viewModel.save()
            viewModel.confirmQuantityIncrease()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertEquals(1, repository.updates.size)
            assertEquals(2, repository.updates.single().quantity)
            assertEquals(listOf(10L), repository.updates.single().selectedAdditionIds)
        }

    @Test
    fun `customized quantity two saves unchanged and only a further increase asks confirmation`() =
        runTest(dispatcher) {
            val unchangedRepository = FakeOrderRepository(draftWithAddition(quantity = 2))
            val additions = FakeAdditionRepository(listOf(addition(10, "Provola", 150)))
            val unchanged = viewModel(unchangedRepository, additionRepository = additions)
            advanceUntilIdle()

            unchanged.save()
            advanceUntilIdle()

            assertFalse(unchanged.uiState.value.showQuantityIncreaseConfirmation)
            assertEquals(2, unchangedRepository.updates.single().quantity)
            assertEquals(listOf(10L), unchangedRepository.updates.single().selectedAdditionIds)

            val increasedRepository = FakeOrderRepository(draftWithAddition(quantity = 2))
            val increased = viewModel(increasedRepository, additionRepository = additions)
            advanceUntilIdle()
            increased.updateQuantity("3")
            increased.save()
            advanceUntilIdle()

            assertTrue(increased.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(increasedRepository.updates.isEmpty())
        }

    private fun viewModel(
        repository: OrderRepository,
        itemId: String = "item-id",
        additionRepository: AdditionRepository = FakeAdditionRepository(),
    ): OrderItemDetailViewModel = OrderItemDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                OrderItemDetailViewModel.ORDER_ID_ARGUMENT to "draft-id",
                OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT to itemId,
            ),
        ),
        orderRepository = repository,
        additionRepository = additionRepository,
        updateOrderItem = UpdateOrderItem(repository),
    )

    private class FakeAdditionRepository(
        private val activeAdditions: List<Addition> = emptyList(),
    ) : AdditionRepository {
        override fun observeAll(): Flow<List<Addition>> = MutableStateFlow(activeAdditions)

        override fun observeActive(): Flow<List<Addition>> = MutableStateFlow(activeAdditions)

        override suspend fun getById(additionId: Long): Addition? =
            activeAdditions.firstOrNull { it.id == additionId }

        override suspend fun create(addition: Addition): Long = error("Not used")

        override suspend fun update(addition: Addition): Boolean = error("Not used")

        override suspend fun activate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")
    }

    private class FakeOrderRepository(initialOrder: Order?) : OrderRepository {
        private var order: Order? = initialOrder
        val requestedOrderIds = mutableListOf<String>()
        val updates = mutableListOf<UpdateCall>()
        var updateResult: UpdateOrderItemResult = UpdateOrderItemResult.Updated

        override suspend fun getById(orderId: String): Order? {
            requestedOrderIds += orderId
            return order?.takeIf { it.id == orderId }
        }

        override fun observeById(orderId: String): Flow<Order?> {
            requestedOrderIds += orderId
            return MutableStateFlow(order?.takeIf { it.id == orderId })
        }

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
            selectedAdditionIds: List<Long>?,
        ): UpdateOrderItemResult {
            updates += UpdateCall(
                orderId,
                orderItemId,
                quantity,
                note,
                manualUnitPrice,
                selectedAdditionIds,
            )
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
        val selectedAdditionIds: List<Long>? = null,
    )

    private fun addition(id: Long, name: String, priceCents: Long): Addition = Addition(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        printedName = null,
        price = Money.ofCents(priceCents),
        active = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun draftWithAddition(quantity: Int): Order {
        val persisted = OrderItemAddition(
            id = "relation-id",
            additionId = 10,
            nameSnapshot = "Provola",
            printedNameSnapshot = "PROVOLA",
            listedPrice = Money.ofCents(150),
            chargedPrice = Money.ofCents(150),
            displayOrder = 0,
        )
        return draft().copy(
            items = draft().items.map { item ->
                item.copy(
                    quantity = quantity,
                    note = null,
                    automaticExtrasTotal = Money.ofCents(150),
                    finalUnitPrice = Money.ofCents(850),
                    additions = listOf(persisted),
                )
            },
        )
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
