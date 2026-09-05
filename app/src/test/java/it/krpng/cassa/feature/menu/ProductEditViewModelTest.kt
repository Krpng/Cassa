package it.krpng.cassa.feature.menu

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.IngredientRepository
import it.krpng.cassa.domain.repository.ProductRepository
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
class ProductEditViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-05T10:15:30Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create validates fields and persists flags with ordered ingredients`() =
        runTest(mainDispatcher) {
            val mozzarella = ingredient(id = 2, name = "Mozzarella")
            val pomodoro = ingredient(id = 1, name = "Pomodoro")
            val productRepository = FakeProductRepository(createdId = 44)
            val viewModel = viewModel(
                savedState = mapOf(ProductEditViewModel.CATEGORY_ARGUMENT to ProductCategory.PIZZA.name),
                productRepository = productRepository,
                ingredients = listOf(pomodoro, mozzarella),
            )
            advanceUntilIdle()

            viewModel.updateName("  Pìzza Speciale  ")
            viewModel.updatePrintedName(" SPECIALE ")
            viewModel.updatePrice("8,50")
            viewModel.updateAutomaticExtrasPricing(false)
            viewModel.updateActive(false)
            viewModel.addIngredient(mozzarella.id)
            viewModel.addIngredient(pomodoro.id)
            viewModel.moveIngredient(pomodoro.id, -1)
            viewModel.save()
            advanceUntilIdle()

            val saved = checkNotNull(productRepository.createdProduct)
            assertEquals(44L, viewModel.uiState.value.savedProductId)
            assertEquals("Pìzza Speciale", saved.name)
            assertEquals("pizza speciale", saved.normalizedName)
            assertEquals("SPECIALE", saved.printedName)
            assertEquals(Money.ofCents(850), saved.price)
            assertEquals(ProductCategory.PIZZA, saved.category)
            assertFalse(saved.automaticExtrasPricing)
            assertFalse(saved.active)
            assertEquals(now, saved.createdAt)
            assertEquals(now, saved.updatedAt)
            assertEquals(
                listOf(1L to 0, 2L to 1),
                saved.ingredients.map { it.ingredient.id to it.displayOrder },
            )
        }

    @Test
    fun `edit loads product and preserves id and creation time on update`() =
        runTest(mainDispatcher) {
            val createdAt = Instant.parse("2026-01-01T08:00:00Z")
            val existing = product(
                id = 7,
                name = "Margherita",
                category = ProductCategory.PIZZA,
                createdAt = createdAt,
                ingredients = listOf(
                    ProductIngredient(ingredient(1, "Pomodoro"), displayOrder = 0),
                ),
            )
            val productRepository = FakeProductRepository(existing = existing)
            val viewModel = viewModel(
                savedState = mapOf(ProductEditViewModel.PRODUCT_ID_ARGUMENT to 7L),
                productRepository = productRepository,
                ingredients = listOf(ingredient(1, "Pomodoro")),
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("Margherita", viewModel.uiState.value.name)
            assertEquals("7,00", viewModel.uiState.value.priceInput)

            viewModel.updateCategory(ProductCategory.FRITTURA)
            viewModel.updatePrice("6.5")
            viewModel.removeIngredient(1)
            viewModel.save()
            advanceUntilIdle()

            val updated = checkNotNull(productRepository.updatedProduct)
            assertEquals(7L, updated.id)
            assertEquals(createdAt, updated.createdAt)
            assertEquals(now, updated.updatedAt)
            assertEquals(ProductCategory.FRITTURA, updated.category)
            assertEquals(Money.ofCents(650), updated.price)
            assertTrue(updated.ingredients.isEmpty())
            assertEquals(7L, viewModel.uiState.value.savedProductId)
        }

    @Test
    fun `invalid form does not write and exposes field errors`() = runTest(mainDispatcher) {
        val productRepository = FakeProductRepository()
        val viewModel = viewModel(productRepository = productRepository)
        advanceUntilIdle()

        viewModel.updateName(" ")
        viewModel.updatePrice("1,234")
        viewModel.save()
        advanceUntilIdle()

        assertNull(productRepository.createdProduct)
        assertTrue(viewModel.uiState.value.validationErrors.hasErrors)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `repository save failure becomes user safe error`() = runTest(mainDispatcher) {
        val productRepository = FakeProductRepository(failOnCreate = true)
        val viewModel = viewModel(productRepository = productRepository)
        advanceUntilIdle()

        viewModel.updateName("Marinara")
        viewModel.updatePrice("6,00")
        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedProductId)
        assertEquals(
            "Impossibile salvare il prodotto. Verifica che il nome non sia già usato.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `missing product is reported without creating a replacement`() = runTest(mainDispatcher) {
        val productRepository = FakeProductRepository()
        val viewModel = viewModel(
            savedState = mapOf(ProductEditViewModel.PRODUCT_ID_ARGUMENT to 404L),
            productRepository = productRepository,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Prodotto non trovato.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.updateName("Non deve essere creato")
        viewModel.updatePrice("5,00")
        viewModel.save()
        advanceUntilIdle()

        assertNull(productRepository.createdProduct)
    }

    private fun viewModel(
        savedState: Map<String, Any?> = emptyMap(),
        productRepository: FakeProductRepository = FakeProductRepository(),
        ingredients: List<Ingredient> = emptyList(),
    ): ProductEditViewModel = ProductEditViewModel(
        savedStateHandle = SavedStateHandle(savedState),
        productRepository = productRepository,
        ingredientRepository = FakeIngredientRepository(MutableStateFlow(ingredients)),
        clockProvider = object : ClockProvider {
            override fun now(): Instant = now
        },
    )

    private class FakeProductRepository(
        private val existing: Product? = null,
        private val createdId: Long = 1,
        private val failOnCreate: Boolean = false,
    ) : ProductRepository {
        var createdProduct: Product? = null
        var updatedProduct: Product? = null

        override fun observeAll(): Flow<List<Product>> = MutableStateFlow(emptyList())

        override fun observeActive(): Flow<List<Product>> = MutableStateFlow(emptyList())

        override suspend fun getById(productId: Long): Product? =
            existing?.takeIf { it.id == productId }

        override suspend fun create(product: Product): Long {
            if (failOnCreate) error("duplicate")
            createdProduct = product
            return createdId
        }

        override suspend fun update(product: Product): Boolean {
            updatedProduct = product
            return existing != null
        }

        override suspend fun activate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")
    }

    private class FakeIngredientRepository(
        private val ingredients: Flow<List<Ingredient>>,
    ) : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> = ingredients

        override suspend fun getById(ingredientId: Long): Ingredient? = null

        override suspend fun create(ingredient: Ingredient): Long = error("Not used")

        override suspend fun update(ingredient: Ingredient): Boolean = error("Not used")

        override suspend fun activate(ingredientId: Long): Boolean = error("Not used")

        override suspend fun deactivate(ingredientId: Long): Boolean = error("Not used")

        override suspend fun replaceProductIngredients(
            productId: Long,
            ingredients: List<ProductIngredient>,
        ) = error("Not used")
    }

    private fun product(
        id: Long,
        name: String,
        category: ProductCategory,
        createdAt: Instant,
        ingredients: List<ProductIngredient>,
    ): Product = Product(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        category = category,
        price = Money.ofCents(700),
        automaticExtrasPricing = true,
        active = true,
        createdAt = createdAt,
        updatedAt = createdAt,
        ingredients = ingredients,
    )

    private fun ingredient(id: Long, name: String): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        active = true,
    )
}
