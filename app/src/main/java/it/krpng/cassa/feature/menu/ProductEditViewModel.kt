package it.krpng.cassa.feature.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.IngredientRepository
import it.krpng.cassa.domain.repository.ProductRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductEditUiState(
    val isLoading: Boolean = true,
    val productId: Long? = null,
    val name: String = "",
    val printedName: String = "",
    val priceInput: String = "0,00",
    val category: ProductCategory = ProductCategory.PIZZA,
    val selectedIngredients: List<Ingredient> = emptyList(),
    val availableIngredients: List<Ingredient> = emptyList(),
    val automaticExtrasPricing: Boolean = true,
    val active: Boolean = true,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val savedProductId: Long? = null,
    val validationErrors: ProductFormErrors = ProductFormErrors(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val ingredientRepository: IngredientRepository,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private val requestedProductId = savedStateHandle.get<Long>(PRODUCT_ID_ARGUMENT)
        ?.takeIf { productId -> productId > 0 }
    private val initialCategory = savedStateHandle.get<String>(CATEGORY_ARGUMENT)
        ?.let { category -> runCatching { ProductCategory.valueOf(category) }.getOrNull() }
        ?: ProductCategory.PIZZA

    private val _uiState = MutableStateFlow(
        ProductEditUiState(
            productId = requestedProductId,
            category = initialCategory,
        ),
    )
    val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

    private var originalProduct: Product? = null
    private var productLoaded = requestedProductId == null
    private var ingredientsLoaded = false
    private var ingredientsLoadFailed = false

    init {
        observeIngredients()
        loadProduct()
    }

    fun updateName(value: String) {
        _uiState.update { state ->
            state.copy(
                name = value,
                validationErrors = state.validationErrors.copy(name = null),
                errorMessage = null,
            )
        }
    }

    fun updatePrintedName(value: String) {
        _uiState.update { state -> state.copy(printedName = value, errorMessage = null) }
    }

    fun updatePrice(value: String) {
        _uiState.update { state ->
            state.copy(
                priceInput = value,
                validationErrors = state.validationErrors.copy(price = null),
                errorMessage = null,
            )
        }
    }

    fun updateCategory(value: ProductCategory) {
        _uiState.update { state -> state.copy(category = value, errorMessage = null) }
    }

    fun updateAutomaticExtrasPricing(value: Boolean) {
        _uiState.update { state ->
            state.copy(automaticExtrasPricing = value, errorMessage = null)
        }
    }

    fun updateActive(value: Boolean) {
        _uiState.update { state -> state.copy(active = value, errorMessage = null) }
    }

    fun addIngredient(ingredientId: Long) {
        _uiState.update { state ->
            val ingredient = state.availableIngredients.firstOrNull { it.id == ingredientId }
                ?: return@update state
            if (state.selectedIngredients.any { it.id == ingredientId }) return@update state
            state.copy(
                selectedIngredients = state.selectedIngredients + ingredient,
                errorMessage = null,
            )
        }
    }

    fun removeIngredient(ingredientId: Long) {
        _uiState.update { state ->
            state.copy(
                selectedIngredients = state.selectedIngredients.filterNot { it.id == ingredientId },
                errorMessage = null,
            )
        }
    }

    fun moveIngredient(ingredientId: Long, offset: Int) {
        if (offset != -1 && offset != 1) return
        _uiState.update { state ->
            val currentIndex = state.selectedIngredients.indexOfFirst { it.id == ingredientId }
            val targetIndex = currentIndex + offset
            if (currentIndex == -1 || targetIndex !in state.selectedIngredients.indices) {
                return@update state
            }
            val reordered = state.selectedIngredients.toMutableList().apply {
                add(targetIndex, removeAt(currentIndex))
            }
            state.copy(selectedIngredients = reordered, errorMessage = null)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || !state.canSave) return

        when (
            val validation = ProductFormValidator.validate(
                name = state.name,
                printedName = state.printedName,
                priceInput = state.priceInput,
            )
        ) {
            is ProductFormValidationResult.Invalid -> {
                _uiState.update { current ->
                    current.copy(validationErrors = validation.errors, errorMessage = null)
                }
            }

            is ProductFormValidationResult.Valid -> saveValidated(validation.fields)
        }
    }

    private fun observeIngredients() {
        viewModelScope.launch {
            ingredientRepository.observeActive()
                .catch {
                    ingredientsLoaded = true
                    ingredientsLoadFailed = true
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = false,
                            errorMessage = "Impossibile caricare gli ingredienti.",
                        )
                    }
                }
                .collect { ingredients ->
                    ingredientsLoaded = true
                    _uiState.update { state ->
                        state.copy(
                            availableIngredients = ingredients,
                            isLoading = !productLoaded,
                            canSave = productLoaded,
                        )
                    }
                }
        }
    }

    private fun loadProduct() {
        val productId = requestedProductId
        if (productId == null) {
            _uiState.update { state ->
                state.copy(
                    isLoading = !ingredientsLoaded,
                    canSave = ingredientsLoaded && !ingredientsLoadFailed,
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val product = productRepository.getById(productId)
                productLoaded = true
                if (product == null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = false,
                            errorMessage = "Prodotto non trovato.",
                        )
                    }
                    return@launch
                }

                originalProduct = product
                _uiState.update { state ->
                    state.copy(
                        isLoading = !ingredientsLoaded,
                        canSave = ingredientsLoaded && !ingredientsLoadFailed,
                        name = product.name,
                        printedName = product.printedName.orEmpty(),
                        priceInput = product.price.toInputString(),
                        category = product.category,
                        selectedIngredients = product.ingredients
                            .sortedBy(ProductIngredient::displayOrder)
                            .map(ProductIngredient::ingredient),
                        automaticExtrasPricing = product.automaticExtrasPricing,
                        active = product.active,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                productLoaded = true
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        canSave = false,
                        errorMessage = "Impossibile caricare il prodotto.",
                    )
                }
            }
        }
    }

    private fun saveValidated(fields: ValidatedProductFields) {
        val state = _uiState.value
        val now = clockProvider.now()
        val existing = originalProduct
        val product = Product(
            id = existing?.id ?: 0,
            name = fields.name,
            normalizedName = TextNormalizer.normalize(fields.name),
            printedName = fields.printedName,
            category = state.category,
            price = fields.price,
            automaticExtrasPricing = state.automaticExtrasPricing,
            active = state.active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            ingredients = state.selectedIngredients.mapIndexed { index, ingredient ->
                ProductIngredient(ingredient = ingredient, displayOrder = index)
            },
        )

        _uiState.update { current ->
            current.copy(
                isSaving = true,
                validationErrors = ProductFormErrors(),
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val savedId = if (existing == null) {
                    productRepository.create(product)
                } else {
                    if (!productRepository.update(product)) {
                        error("Product no longer exists")
                    }
                    product.id
                }
                _uiState.update { current ->
                    current.copy(isSaving = false, savedProductId = savedId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        errorMessage = "Impossibile salvare il prodotto. Verifica che il nome non sia già usato.",
                    )
                }
            }
        }
    }

    private fun it.krpng.cassa.core.money.Money.toInputString(): String =
        "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        const val PRODUCT_ID_ARGUMENT = "productId"
        const val CATEGORY_ARGUMENT = "category"
    }
}
