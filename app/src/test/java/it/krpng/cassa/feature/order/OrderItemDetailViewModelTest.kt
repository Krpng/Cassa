package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
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

            assertTrue(viewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(repository.updates.isEmpty())

            viewModel.confirmQuantityIncrease()
            viewModel.confirmQuantityIncrease()
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
                    emptyList(),
                    CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
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
    fun `reset manual price updates the form then save restores automatic price`() =
        runTest(dispatcher) {
            val itemWithManualPrice = draft().items.single().copy(
                quantity = 3,
                manualUnitPrice = Money.ofCents(550),
                finalUnitPrice = Money.ofCents(550),
            )
            val repository = FakeOrderRepository(
                draft().copy(items = listOf(itemWithManualPrice)),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            assertEquals("5,50", viewModel.uiState.value.manualPriceInput)

            viewModel.resetManualPrice()

            assertNull(viewModel.uiState.value.manualPriceInput)
            assertTrue(repository.updates.isEmpty())

            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, repository.updates.size)
            assertNull(repository.updates.single().manualPrice)
            val reopened = viewModel(repository)
            advanceUntilIdle()
            assertNull(reopened.uiState.value.manualPriceInput)
            assertEquals(Money.ofCents(800), reopened.uiState.value.automaticUnitPrice)
        }

    @Test
    fun `reset is a safe no-op when no manual override is active`() = runTest(dispatcher) {
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.manualPriceInput)
        viewModel.resetManualPrice()

        assertNull(viewModel.uiState.value.manualPriceInput)
        assertTrue(repository.updates.isEmpty())
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
    fun `non pizza and ambiguous pizza additions are blocked while no-auto additions remain editable`() =
        runTest(dispatcher) {
            val active = FakeAdditionRepository(listOf(addition(10, "Provola", 150)))
            listOf(ProductCategory.FRITTURA, ProductCategory.BIBITA).forEach { category ->
                val nonPizza = draft().copy(
                    items = draft().items.map { item ->
                        item.copy(categorySnapshot = category, quantity = 1)
                    },
                )
                val nonPizzaViewModel = viewModel(
                    FakeOrderRepository(nonPizza),
                    additionRepository = active,
                )
                advanceUntilIdle()
                assertTrue(nonPizzaViewModel.uiState.value.additionOptions.isEmpty())
                assertFalse(nonPizzaViewModel.uiState.value.canEditAdditions)
                assertTrue(nonPizzaViewModel.uiState.value.removalOptions.isEmpty())
                assertFalse(nonPizzaViewModel.uiState.value.canEditRemovals)
            }

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
            assertTrue(deferredViewModel.uiState.value.canEditAdditions)
            assertTrue(
                deferredViewModel.uiState.value.additionMessage.orEmpty()
                    .contains("non modificano automaticamente il prezzo"),
            )
        }

    @Test
    fun `pizza with no catalog ingredients exposes an empty removal state without inventing data`() =
        runTest(dispatcher) {
            val singlePizza = draft().copy(
                items = draft().items.map { item -> item.copy(quantity = 1, note = null) },
            )
            val products = FakeProductRepository(
                listOf(productWithIngredients(productId = 42, ingredients = emptyList())),
            )

            val viewModel = viewModel(
                FakeOrderRepository(singlePizza),
                productRepository = products,
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.canEditRemovals)
            assertTrue(viewModel.uiState.value.removalOptions.isEmpty())
            assertTrue(viewModel.uiState.value.selectedRemovalIngredientIds.isEmpty())
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
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                repository.updates.single().customizationQuantityIntent,
            )
        }

    @Test
    fun `new customization on persisted standard quantity one can increase after confirmation`() =
        runTest(dispatcher) {
            val standardOne = draft().copy(
                items = draft().items.map { item ->
                    item.copy(
                        quantity = 1,
                        automaticExtrasTotal = Money.ZERO,
                        finalUnitPrice = item.baseUnitPrice,
                        note = null,
                    )
                },
            )
            val repository = FakeOrderRepository(standardOne)
            val additions = FakeAdditionRepository(listOf(addition(10, "Acciughe", 150)))
            val viewModel = viewModel(repository, additionRepository = additions)
            advanceUntilIdle()

            viewModel.toggleAddition(10)
            viewModel.updateQuantity("2")
            viewModel.save()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(repository.updates.isEmpty())

            viewModel.confirmQuantityIncrease()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertEquals(1, repository.updates.size)
            assertEquals(2, repository.updates.single().quantity)
            assertEquals(listOf(10L), repository.updates.single().selectedAdditionIds)
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                repository.updates.single().customizationQuantityIntent,
            )
        }

    @Test
    fun `note and zero manual price count as customization for quantity confirmation`() =
        runTest(dispatcher) {
            val standardOne = draft().copy(
                items = draft().items.map { item ->
                    item.copy(
                        quantity = 1,
                        note = null,
                        manualUnitPrice = null,
                    )
                },
            )
            val noteRepository = FakeOrderRepository(standardOne)
            val noteViewModel = viewModel(noteRepository)
            advanceUntilIdle()

            noteViewModel.updateNote("Ben cotta")
            noteViewModel.updateQuantity("2")
            noteViewModel.save()
            advanceUntilIdle()

            assertTrue(noteViewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(noteRepository.updates.isEmpty())

            noteViewModel.confirmQuantityIncrease()
            advanceUntilIdle()

            assertEquals("Ben cotta", noteRepository.updates.single().note)
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                noteRepository.updates.single().customizationQuantityIntent,
            )

            val manualRepository = FakeOrderRepository(standardOne)
            val manualViewModel = viewModel(manualRepository)
            advanceUntilIdle()

            manualViewModel.startManualPriceEdit()
            manualViewModel.updateManualPrice("0")
            manualViewModel.updateQuantity("2")
            manualViewModel.save()
            advanceUntilIdle()

            assertTrue(manualViewModel.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(manualRepository.updates.isEmpty())

            manualViewModel.confirmQuantityIncrease()
            advanceUntilIdle()

            assertEquals(Money.ZERO, manualRepository.updates.single().manualPrice)
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                manualRepository.updates.single().customizationQuantityIntent,
            )
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

            increased.confirmQuantityIncrease()
            advanceUntilIdle()

            assertTrue(increased.uiState.value.isSaved)
            assertEquals(1, increasedRepository.updates.size)
            assertEquals(3, increasedRepository.updates.single().quantity)
            assertEquals(listOf(10L), increasedRepository.updates.single().selectedAdditionIds)
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                increasedRepository.updates.single().customizationQuantityIntent,
            )
        }

    @Test
    fun `pizza exposes only its ingredients in composition order and saves removals`() =
        runTest(dispatcher) {
            val singlePizza = draft().copy(
                items = draft().items.map { item -> item.copy(quantity = 1, note = null) },
            )
            val repository = FakeOrderRepository(singlePizza)
            val product = productWithIngredients(
                productId = 42,
                ingredients = listOf(
                    productIngredient(21, "Mozzarella", displayOrder = 1),
                    productIngredient(20, "Pomodoro", displayOrder = 0),
                ),
            )
            val unrelated = productWithIngredients(
                productId = 99,
                ingredients = listOf(productIngredient(22, "Basilico", displayOrder = 0)),
            )
            val viewModel = viewModel(
                repository = repository,
                productRepository = FakeProductRepository(listOf(product, unrelated)),
            )
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertTrue(initial.canEditRemovals)
            assertEquals(listOf(20L, 21L), initial.removalOptions.map { it.id })

            viewModel.toggleRemoval(21)
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(21L), repository.updates.single().selectedRemovalIngredientIds)
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `persisted removal reopens selected with its historical snapshot name`() =
        runTest(dispatcher) {
            val persisted = OrderItemRemoval(
                id = "removal-id",
                ingredientId = 20,
                nameSnapshot = "Pomodoro storico",
                displayOrder = 0,
            )
            val repository = FakeOrderRepository(
                draft().copy(
                    items = draft().items.map { item ->
                        item.copy(quantity = 1, note = null, removals = listOf(persisted))
                    },
                ),
            )
            val products = FakeProductRepository(
                listOf(
                    productWithIngredients(
                        42,
                        listOf(productIngredient(20, "Pomodoro nuovo", displayOrder = 0)),
                    ),
                ),
            )

            val reopened = viewModel(repository, productRepository = products)
            advanceUntilIdle()

            val state = reopened.uiState.value
            assertEquals(listOf(20L), state.selectedRemovalIngredientIds)
            assertTrue(state.removalOptions.single().isSelected)
            assertEquals("Pomodoro storico", state.removalOptions.single().name)
        }

    @Test
    fun `standard pizza quantity two blocks removals then confirmation protects customized increase`() =
        runTest(dispatcher) {
            val repository = FakeOrderRepository(
                draft().copy(items = draft().items.map { item -> item.copy(note = null) }),
            )
            val products = FakeProductRepository(
                listOf(
                    productWithIngredients(
                        42,
                        listOf(productIngredient(20, "Pomodoro", displayOrder = 0)),
                    ),
                ),
            )
            val viewModel = viewModel(repository, productRepository = products)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canEditRemovals)
            assertTrue(viewModel.uiState.value.removalMessage.orEmpty().contains("contiene 2 pizze"))

            viewModel.updateQuantity("1")
            assertTrue(viewModel.uiState.value.canEditRemovals)

            val customizedRepository = FakeOrderRepository(
                draft().copy(
                    items = draft().items.map { item -> item.copy(quantity = 1, note = null) },
                ),
            )
            val customized = viewModel(
                customizedRepository,
                productRepository = products,
            )
            advanceUntilIdle()
            customized.toggleRemoval(20)
            customized.updateQuantity("2")
            customized.save()
            advanceUntilIdle()

            assertTrue(customized.uiState.value.showQuantityIncreaseConfirmation)
            assertTrue(customizedRepository.updates.isEmpty())

            customized.cancelQuantityIncrease()
            assertTrue(customizedRepository.updates.isEmpty())
            customized.save()
            customized.confirmQuantityIncrease()
            advanceUntilIdle()

            assertEquals(
                listOf(20L),
                customizedRepository.updates.single().selectedRemovalIngredientIds,
            )
            assertEquals(
                CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED,
                customizedRepository.updates.single().customizationQuantityIntent,
            )
        }

    private fun viewModel(
        repository: OrderRepository,
        itemId: String = "item-id",
        additionRepository: AdditionRepository = FakeAdditionRepository(),
        productRepository: ProductRepository = FakeProductRepository(),
    ): OrderItemDetailViewModel = OrderItemDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                OrderItemDetailViewModel.ORDER_ID_ARGUMENT to "draft-id",
                OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT to itemId,
            ),
        ),
        orderRepository = repository,
        additionRepository = additionRepository,
        productRepository = productRepository,
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

    private class FakeProductRepository(
        private val products: List<Product> = emptyList(),
    ) : ProductRepository {
        override fun observeAll(): Flow<List<Product>> = MutableStateFlow(products)

        override fun observeActive(): Flow<List<Product>> = MutableStateFlow(
            products.filter(Product::active),
        )

        override suspend fun getById(productId: Long): Product? =
            products.firstOrNull { it.id == productId }

        override suspend fun create(product: Product): Long = error("Not used")

        override suspend fun update(product: Product): Boolean = error("Not used")

        override suspend fun activate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean =
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
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult {
            updates += UpdateCall(
                orderId,
                orderItemId,
                quantity,
                note,
                manualUnitPrice,
                selectedAdditionIds,
                selectedRemovalIngredientIds,
                customizationQuantityIntent,
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
        val selectedRemovalIngredientIds: List<Long>? = null,
        val customizationQuantityIntent: CustomizationQuantityIntent =
            CustomizationQuantityIntent.KEEP_CURRENT_SCOPE,
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

    private fun productIngredient(
        id: Long,
        name: String,
        displayOrder: Int,
    ): ProductIngredient = ProductIngredient(
        ingredient = Ingredient(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            active = true,
        ),
        displayOrder = displayOrder,
    )

    private fun productWithIngredients(
        productId: Long,
        ingredients: List<ProductIngredient>,
    ): Product = Product(
        id = productId,
        name = "Margherita",
        normalizedName = "margherita",
        printedName = "MARGHERITA",
        category = ProductCategory.PIZZA,
        price = Money.ofCents(700),
        automaticExtrasPricing = true,
        active = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ingredients = ingredients,
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
