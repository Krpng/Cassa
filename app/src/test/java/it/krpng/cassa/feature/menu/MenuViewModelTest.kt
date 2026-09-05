package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.ProductRepository
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
class MenuViewModelTest {
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
    fun `catalog Flow drives sections and preserves inactive state`() = runTest(mainDispatcher) {
        val products = MutableStateFlow(
            listOf(
                product(id = 1, name = "Margherita", category = ProductCategory.PIZZA),
                product(id = 2, name = "Crocchè", category = ProductCategory.FRITTURA, active = false),
                product(id = 3, name = "Acqua", category = ProductCategory.BIBITA),
            ),
        )
        val additions = MutableStateFlow(listOf(addition(id = 4, name = "Prosciutto")))
        val viewModel = MenuViewModel(
            productRepository = FakeProductRepository(products),
            additionRepository = FakeAdditionRepository(additions),
        )

        observe(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("Margherita"), viewModel.uiState.value.items.map { it.name })

        viewModel.selectSection(MenuSection.FRIED)
        advanceUntilIdle()
        assertEquals("Crocchè", viewModel.uiState.value.items.single().name)
        assertFalse(viewModel.uiState.value.items.single().active)

        viewModel.selectSection(MenuSection.ADDITIONS)
        advanceUntilIdle()
        assertEquals(listOf("Prosciutto"), viewModel.uiState.value.items.map { it.name })
    }

    @Test
    fun `product search reuses ranking and exposes matched ingredient`() = runTest(mainDispatcher) {
        val matchingIngredient = ProductIngredient(
            ingredient = ingredient(id = 11, name = "Parmigiano Reggiano"),
            displayOrder = 0,
        )
        val products = MutableStateFlow(
            listOf(
                product(
                    id = 1,
                    name = "Quattro formaggi",
                    category = ProductCategory.PIZZA,
                    ingredients = listOf(matchingIngredient),
                ),
                product(
                    id = 2,
                    name = "Parmigiana",
                    category = ProductCategory.PIZZA,
                    active = false,
                ),
            ),
        )
        val viewModel = MenuViewModel(
            productRepository = FakeProductRepository(products),
            additionRepository = FakeAdditionRepository(MutableStateFlow(emptyList())),
        )

        observe(viewModel)
        viewModel.updateSearchQuery("  PARMÌGIANO ")
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.single()
        assertEquals("Quattro formaggi", item.name)
        assertEquals("Parmigiano Reggiano", item.matchedIngredient)
        assertTrue(item.active)
    }

    @Test
    fun `addition search is normalized and deterministic`() = runTest(mainDispatcher) {
        val additions = MutableStateFlow(
            listOf(
                addition(id = 2, name = "Prosciutto cotto"),
                addition(id = 1, name = "Olìve nere"),
            ),
        )
        val viewModel = MenuViewModel(
            productRepository = FakeProductRepository(MutableStateFlow(emptyList())),
            additionRepository = FakeAdditionRepository(additions),
        )

        observe(viewModel)
        viewModel.selectSection(MenuSection.ADDITIONS)
        viewModel.updateSearchQuery("  OLIVE ")
        advanceUntilIdle()

        assertEquals(listOf("Olìve nere"), viewModel.uiState.value.items.map { it.name })
        assertNull(viewModel.uiState.value.items.single().matchedIngredient)
    }

    @Test
    fun `repository failure becomes explicit error state`() = runTest(mainDispatcher) {
        val failingProducts = flow<List<Product>> {
            throw IllegalStateException("database unavailable")
        }
        val viewModel = MenuViewModel(
            productRepository = FakeProductRepository(failingProducts),
            additionRepository = FakeAdditionRepository(MutableStateFlow(emptyList())),
        )

        assertTrue(viewModel.uiState.value.isLoading)
        observe(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Impossibile caricare il menu.", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.observe(viewModel: MenuViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
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

    private class FakeAdditionRepository(
        private val additions: Flow<List<Addition>>,
    ) : AdditionRepository {
        override fun observeAll(): Flow<List<Addition>> = additions

        override fun observeActive(): Flow<List<Addition>> = additions

        override suspend fun getById(additionId: Long): Addition? = null

        override suspend fun create(addition: Addition): Long = error("Not used")

        override suspend fun update(addition: Addition): Boolean = error("Not used")

        override suspend fun activate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(additionId: Long, updatedAt: Instant): Boolean =
            error("Not used")
    }

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

    private fun addition(id: Long, name: String): Addition = Addition(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        price = Money.ofCents(200),
        active = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun ingredient(id: Long, name: String): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        active = true,
    )
}
