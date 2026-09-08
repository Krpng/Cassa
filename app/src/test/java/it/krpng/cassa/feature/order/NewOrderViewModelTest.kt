package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.usecase.AddProductToDraft
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
import org.junit.Assert.assertNull
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
            assertTrue(initial.isDraftEmpty)
            assertEquals(listOf("draft-id"), repository.observedIds)

            orders.value = draft().copy(items = listOf(orderItem()))
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as NewOrderUiState.Ready).isDraftEmpty)
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
    fun `active catalog is reactive and category filters combine with blank query`() =
        runTest(mainDispatcher) {
            val products = MutableStateFlow(
                listOf(
                    product(1, "Margherita", ProductCategory.PIZZA),
                    product(2, "Crocchè", ProductCategory.FRITTURA),
                    product(3, "Acqua", ProductCategory.BIBITA),
                    product(4, "Pizza inattiva", ProductCategory.PIZZA, active = false),
                ),
            )
            val viewModel = viewModel(
                repository = FakeOrderRepository(MutableStateFlow(draft())),
                draftId = "draft-id",
                products = products,
            )
            advanceUntilIdle()

            assertEquals(
                listOf("Acqua", "Crocchè", "Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            viewModel.selectFilter(OrderCatalogFilter.PIZZAS)
            advanceUntilIdle()
            assertEquals(
                listOf("Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            products.value = products.value + product(5, "Marinara", ProductCategory.PIZZA)
            advanceUntilIdle()
            assertEquals(
                listOf("Margherita", "Marinara"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )
        }

    @Test
    fun `search reuses documented ranking and exposes ingredient match reason`() =
        runTest(mainDispatcher) {
            val parmigiano = ProductIngredient(
                ingredient = ingredient(10, "Parmigiano Reggiano"),
                displayOrder = 0,
            )
            val products = MutableStateFlow(
                listOf(
                    product(1, "Pizza Margherita", ProductCategory.PIZZA),
                    product(2, "Marinara", ProductCategory.PIZZA),
                    product(
                        3,
                        "Quattro formaggi",
                        ProductCategory.PIZZA,
                        ingredients = listOf(parmigiano),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = FakeOrderRepository(MutableStateFlow(draft())),
                draftId = "draft-id",
                products = products,
            )

            viewModel.updateSearchQuery("  MAR  ")
            advanceUntilIdle()
            assertEquals(
                listOf("Marinara", "Pizza Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            viewModel.updateSearchQuery("PARMÌGIANO")
            advanceUntilIdle()
            val ingredientMatch =
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.single()
            assertEquals("Quattro formaggi", ingredientMatch.name)
            assertEquals("Parmigiano Reggiano", ingredientMatch.matchedIngredient)
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
                NewOrderUiState.Failure("Impossibile caricare l'ordine o il catalogo."),
                viewModel.uiState.value,
            )

            repository.observedOrders = MutableStateFlow(draft())
            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is NewOrderUiState.Ready)
            assertEquals(listOf("draft-id", "draft-id"), repository.observedIds)
        }

    @Test
    fun `quick add uses the route draft id and does not create another draft`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draft()))
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            viewModel.quickAdd(42)
            viewModel.quickAdd(42)
            advanceUntilIdle()

            assertEquals(
                listOf("draft-id" to 42L, "draft-id" to 42L),
                repository.quickAddCalls,
            )
            assertEquals(0, repository.createCalls)
            assertTrue(
                (viewModel.uiState.value as NewOrderUiState.Ready)
                    .quickAddInProgressProductIds.isEmpty(),
            )
        }

    @Test
    fun `quick add maps unavailable product to a dismissible user error`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draft())).apply {
                quickAddResult = QuickAddStandardResult.ProductUnavailable
            }
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            viewModel.quickAdd(42)
            advanceUntilIdle()

            assertEquals(
                "Il prodotto non è più disponibile.",
                (viewModel.uiState.value as NewOrderUiState.Ready).quickAddError,
            )

            viewModel.dismissQuickAddError()
            advanceUntilIdle()
            assertNull((viewModel.uiState.value as NewOrderUiState.Ready).quickAddError)
        }

    private fun viewModel(
        repository: OrderRepository,
        draftId: String,
        products: Flow<List<Product>> = MutableStateFlow(emptyList()),
    ): NewOrderViewModel = NewOrderViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(NewOrderViewModel.DRAFT_ID_ARGUMENT to draftId),
        ),
        orderRepository = repository,
        productRepository = FakeProductRepository(products),
        addProductToDraft = AddProductToDraft(repository),
    )

    private class FakeOrderRepository(
        var observedOrders: Flow<Order?>,
    ) : OrderRepository {
        val observedIds = mutableListOf<String>()
        var createCalls = 0
        var deleteCalls = 0
        var replaceCalls = 0
        var quickAddResult: QuickAddStandardResult =
            QuickAddStandardResult.Added("item-id")
        val quickAddCalls = mutableListOf<Pair<String, Long>>()

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

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult {
            quickAddCalls += orderId to productId
            return quickAddResult
        }
    }

    private class FakeProductRepository(
        private val products: Flow<List<Product>>,
    ) : ProductRepository {
        override fun observeAll(): Flow<List<Product>> = products

        override fun observeActive(): Flow<List<Product>> = products

        override suspend fun getById(productId: Long): Product? = null

        override suspend fun create(product: Product): Long = error("Not used")

        override suspend fun update(product: Product): Boolean = error("Not used")

        override suspend fun activate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")
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

    private fun product(
        id: Long,
        name: String,
        category: ProductCategory,
        active: Boolean = true,
        ingredients: List<ProductIngredient> = emptyList(),
    ): Product = Product(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        category = category,
        price = Money.ofCents(700),
        automaticExtrasPricing = true,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ingredients = ingredients,
    )

    private fun ingredient(id: Long, name: String): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        active = true,
    )
}
