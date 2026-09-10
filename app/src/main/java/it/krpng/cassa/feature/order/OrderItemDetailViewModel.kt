package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.usecase.UpdateOrderItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PizzaAdditionOption(
    val id: Long,
    val name: String,
    val price: Money,
    val isSelected: Boolean,
)

data class PizzaRemovalOption(
    val id: Long,
    val name: String,
    val isSelected: Boolean,
)

data class OrderItemDetailUiState(
    val isLoading: Boolean = true,
    val canSave: Boolean = false,
    val productName: String = "",
    val automaticUnitPrice: Money = Money.ZERO,
    val quantityInput: String = "1",
    val note: String = "",
    val manualPriceInput: String? = null,
    val category: ProductCategory? = null,
    val originalQuantity: Int = 1,
    val additionOptions: List<PizzaAdditionOption> = emptyList(),
    val selectedAdditionIds: List<Long> = emptyList(),
    val canEditAdditions: Boolean = false,
    val additionMessage: String? = null,
    val removalOptions: List<PizzaRemovalOption> = emptyList(),
    val selectedRemovalIngredientIds: List<Long> = emptyList(),
    val canEditRemovals: Boolean = false,
    val removalMessage: String? = null,
    val automaticExtrasPricingEnabled: Boolean = false,
    val hasExistingNonModifierCustomization: Boolean = false,
    val showQuantityIncreaseConfirmation: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val validationErrors: OrderItemFormErrors = OrderItemFormErrors(),
    val errorMessage: String? = null,
)

@HiltViewModel
class OrderItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val additionRepository: AdditionRepository,
    private val productRepository: ProductRepository,
    private val updateOrderItem: UpdateOrderItem,
) : ViewModel() {
    private val orderId = savedStateHandle.get<String>(ORDER_ID_ARGUMENT).orEmpty()
    private val orderItemId = savedStateHandle.get<String>(ORDER_ITEM_ID_ARGUMENT).orEmpty()

    private val _uiState = MutableStateFlow(OrderItemDetailUiState())
    val uiState: StateFlow<OrderItemDetailUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var fieldsInitialized = false
    private var hasLocalAdditionChanges = false
    private var hasLocalRemovalChanges = false

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun updateQuantity(value: String) {
        _uiState.update { state ->
            state.copy(
                quantityInput = value,
                validationErrors = state.validationErrors.copy(quantity = null),
                errorMessage = null,
            ).withModifierAvailability()
        }
    }

    fun decreaseQuantity() {
        val quantity = _uiState.value.quantityInput.toIntOrNull() ?: return
        if (quantity > 1) updateQuantity((quantity - 1).toString())
    }

    fun increaseQuantity() {
        val quantity = _uiState.value.quantityInput.toIntOrNull() ?: return
        if (quantity < Int.MAX_VALUE) updateQuantity((quantity + 1).toString())
    }

    fun updateNote(value: String) {
        _uiState.update { state -> state.copy(note = value, errorMessage = null) }
    }

    fun startManualPriceEdit() {
        _uiState.update { state ->
            if (state.manualPriceInput != null) {
                state
            } else {
                state.copy(manualPriceInput = state.automaticUnitPrice.toInputString())
            }
        }
    }

    fun updateManualPrice(value: String) {
        _uiState.update { state ->
            if (state.manualPriceInput == null) {
                state
            } else {
                state.copy(
                    manualPriceInput = value,
                    validationErrors = state.validationErrors.copy(manualPrice = null),
                    errorMessage = null,
                )
            }
        }
    }

    fun resetManualPrice() {
        _uiState.update { state ->
            if (state.manualPriceInput == null || state.isSaving) {
                state
            } else {
                state.copy(
                    manualPriceInput = null,
                    validationErrors = state.validationErrors.copy(manualPrice = null),
                    errorMessage = null,
                )
            }
        }
    }

    fun toggleAddition(additionId: Long) {
        _uiState.update { state ->
            if (!state.canEditAdditions || state.isSaving) return@update state
            hasLocalAdditionChanges = true
            val selectedIds = if (additionId in state.selectedAdditionIds) {
                state.selectedAdditionIds - additionId
            } else {
                state.selectedAdditionIds + additionId
            }
            state.copy(
                selectedAdditionIds = selectedIds,
                additionOptions = state.additionOptions.map { option ->
                    option.copy(isSelected = option.id in selectedIds)
                },
                errorMessage = null,
            ).withModifierAvailability()
        }
    }

    fun toggleRemoval(ingredientId: Long) {
        _uiState.update { state ->
            if (!state.canEditRemovals || state.isSaving) return@update state
            hasLocalRemovalChanges = true
            val selectedIds = if (ingredientId in state.selectedRemovalIngredientIds) {
                state.selectedRemovalIngredientIds - ingredientId
            } else {
                state.selectedRemovalIngredientIds + ingredientId
            }
            state.copy(
                selectedRemovalIngredientIds = selectedIds,
                removalOptions = state.removalOptions.map { option ->
                    option.copy(isSelected = option.id in selectedIds)
                },
                errorMessage = null,
            ).withModifierAvailability()
        }
    }

    fun cancelQuantityIncrease() {
        _uiState.update { state -> state.copy(showQuantityIncreaseConfirmation = false) }
    }

    fun confirmQuantityIncrease() {
        val state = _uiState.value
        if (!state.showQuantityIncreaseConfirmation || state.isSaving) return
        when (
            val validation = OrderItemFormValidator.validate(
                quantityInput = state.quantityInput,
                note = state.note,
                manualPriceInput = state.manualPriceInput,
            )
        ) {
            is OrderItemFormValidationResult.Invalid -> {
                _uiState.update { current ->
                    current.copy(
                        showQuantityIncreaseConfirmation = false,
                        validationErrors = validation.errors,
                    )
                }
            }

            is OrderItemFormValidationResult.Valid -> saveValidated(
                fields = validation.fields,
                quantityIncreaseConfirmed = true,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || state.isLoading || state.isSaving) return

        when (
            val validation = OrderItemFormValidator.validate(
                quantityInput = state.quantityInput,
                note = state.note,
                manualPriceInput = state.manualPriceInput,
            )
        ) {
            is OrderItemFormValidationResult.Invalid -> {
                _uiState.update { current ->
                    current.copy(validationErrors = validation.errors, errorMessage = null)
                }
            }

            is OrderItemFormValidationResult.Valid -> saveValidated(validation.fields)
        }
    }

    private fun load() {
        if (orderId.isBlank() || orderItemId.isBlank()) {
            showUnavailable("Riga ordine non disponibile.")
            return
        }
        observationJob?.cancel()
        fieldsInitialized = false
        hasLocalAdditionChanges = false
        hasLocalRemovalChanges = false
        _uiState.value = OrderItemDetailUiState()
        observationJob = viewModelScope.launch {
            try {
                combine(
                    orderRepository.observeById(orderId),
                    additionRepository.observeActive(),
                    productRepository.observeAll(),
                ) { order, additions, products -> Triple(order, additions, products) }
                    .collect { (order, additions, products) ->
                        applyObservedState(order, additions, products)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showUnavailable("Impossibile caricare la riga ordine.")
            }
        }
    }

    private fun applyObservedState(
        order: Order?,
        additions: List<Addition>,
        products: List<Product>,
    ) {
        when {
            order == null -> showUnavailable("Ordine non disponibile.")
            order.status != OrderStatus.DRAFT ->
                showUnavailable("Questo ordine non è modificabile.")

            else -> {
                val item = order.items.firstOrNull { candidate -> candidate.id == orderItemId }
                if (item == null) {
                    showUnavailable("Riga ordine non disponibile.")
                } else {
                    showEditable(item, additions, products)
                }
            }
        }
    }

    private fun showEditable(
        item: OrderItem,
        activeAdditions: List<Addition>,
        products: List<Product>,
    ) {
        val persistedSelectedIds = item.additions.mapNotNull { addition -> addition.additionId }
        val selectedIds = if (hasLocalAdditionChanges) {
            _uiState.value.selectedAdditionIds
        } else {
            persistedSelectedIds
        }
        val isPizza = item.categorySnapshot == ProductCategory.PIZZA
        val persistedRemovalIds = item.removals.mapNotNull { removal -> removal.ingredientId }
        val selectedRemovalIds = if (hasLocalRemovalChanges) {
            _uiState.value.selectedRemovalIngredientIds
        } else {
            persistedRemovalIds
        }
        val hasExistingNonModifierCustomization = !item.note.isNullOrBlank() ||
            item.manualUnitPrice != null ||
            item.additions.any { addition -> addition.additionId == null } ||
            item.removals.any { removal -> removal.ingredientId == null }
        val options = if (isPizza) {
            activeAdditions.map { addition ->
                PizzaAdditionOption(
                    id = addition.id,
                    name = addition.name,
                    price = addition.price,
                    isSelected = addition.id in selectedIds,
                )
            }
        } else {
            emptyList()
        }
        val persistedRemovalNames = item.removals
            .mapNotNull { removal -> removal.ingredientId?.let { it to removal.nameSnapshot } }
            .toMap()
        val productIngredients = products
            .firstOrNull { product -> product.id == item.productId }
            ?.ingredients
            .orEmpty()
            .sortedWith(compareBy({ it.displayOrder }, { it.ingredient.id }))
        val removalOptions = if (isPizza) {
            val fromComposition = productIngredients.map { productIngredient ->
                PizzaRemovalOption(
                    id = productIngredient.ingredient.id,
                    name = persistedRemovalNames[productIngredient.ingredient.id]
                        ?: productIngredient.ingredient.name,
                    isSelected = productIngredient.ingredient.id in selectedRemovalIds,
                )
            }
            val compositionIds = fromComposition.map { option -> option.id }.toSet()
            val historicalSelections = item.removals.mapNotNull { removal ->
                val ingredientId = removal.ingredientId ?: return@mapNotNull null
                if (ingredientId in compositionIds) return@mapNotNull null
                PizzaRemovalOption(
                    id = ingredientId,
                    name = removal.nameSnapshot,
                    isSelected = ingredientId in selectedRemovalIds,
                )
            }
            fromComposition + historicalSelections
        } else {
            emptyList()
        }

        _uiState.update { current ->
            val common = current.copy(
                isLoading = false,
                canSave = true,
                productName = item.productNameSnapshot,
                automaticUnitPrice = item.baseUnitPrice + item.automaticExtrasTotal,
                category = item.categorySnapshot,
                originalQuantity = item.quantity,
                additionOptions = options,
                selectedAdditionIds = selectedIds,
                removalOptions = removalOptions,
                selectedRemovalIngredientIds = selectedRemovalIds,
                automaticExtrasPricingEnabled = item.automaticExtrasPricingSnapshot,
                hasExistingNonModifierCustomization = hasExistingNonModifierCustomization,
            )
            val initialized = if (fieldsInitialized) {
                common
            } else {
                fieldsInitialized = true
                common.copy(
                    quantityInput = item.quantity.toString(),
                    note = item.note.orEmpty(),
                    manualPriceInput = item.manualUnitPrice?.toInputString(),
                )
            }
            initialized.withModifierAvailability()
        }
    }

    private fun saveValidated(
        fields: ValidatedOrderItemFields,
        quantityIncreaseConfirmed: Boolean = false,
    ) {
        val state = _uiState.value
        if (
            !quantityIncreaseConfirmed &&
            fields.quantity > state.originalQuantity &&
            state.category == ProductCategory.PIZZA &&
            (state.selectedAdditionIds.isNotEmpty() ||
                state.selectedRemovalIngredientIds.isNotEmpty() ||
                fields.note != null ||
                fields.manualUnitPrice != null)
        ) {
            _uiState.update { current ->
                current.copy(showQuantityIncreaseConfirmation = true)
            }
            return
        }
        _uiState.update { state ->
            state.copy(
                isSaving = true,
                showQuantityIncreaseConfirmation = false,
                validationErrors = OrderItemFormErrors(),
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                updateOrderItem(
                    orderId = orderId,
                    orderItemId = orderItemId,
                    quantity = fields.quantity,
                    note = fields.note,
                    manualUnitPrice = fields.manualUnitPrice,
                    selectedAdditionIds = if (
                        _uiState.value.category == ProductCategory.PIZZA
                    ) {
                        _uiState.value.selectedAdditionIds
                    } else {
                        null
                    },
                    selectedRemovalIngredientIds = if (
                        _uiState.value.category == ProductCategory.PIZZA
                    ) {
                        _uiState.value.selectedRemovalIngredientIds
                    } else {
                        null
                    },
                    customizationQuantityIntent = if (quantityIncreaseConfirmed) {
                        CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED
                    } else {
                        CustomizationQuantityIntent.KEEP_CURRENT_SCOPE
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateOrderItemResult.PersistenceFailure
            }
            applySaveResult(result)
        }
    }

    private fun applySaveResult(result: UpdateOrderItemResult) {
        when (result) {
            UpdateOrderItemResult.Updated ->
                _uiState.update { state -> state.copy(isSaving = false, isSaved = true) }

            UpdateOrderItemResult.InvalidQuantity ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        validationErrors = state.validationErrors.copy(
                            quantity = "Inserisci una quantità valida maggiore di zero.",
                        ),
                    )
                }

            UpdateOrderItemResult.AmountOverflow ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Quantità o prezzo troppo elevati.",
                    )
                }

            UpdateOrderItemResult.OrderNotFound,
            UpdateOrderItemResult.ItemNotFound,
            -> showUnavailable("La riga ordine non è più disponibile.")

            UpdateOrderItemResult.OrderNotEditable ->
                showUnavailable("Questo ordine non è modificabile.")

            UpdateOrderItemResult.ItemNotPizza ->
                showUnavailable("Le aggiunte sono disponibili solo per le pizze.")

            UpdateOrderItemResult.AdditionUnavailable ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Una delle aggiunte selezionate non è più disponibile.",
                    )
                }

            UpdateOrderItemResult.IngredientNotRemovable ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage =
                            "Uno degli ingredienti selezionati non appartiene più al prodotto.",
                    )
                }

            UpdateOrderItemResult.AmbiguousPizzaQuantity ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage =
                            "Scegli prima se modificare una pizza o tutte quelle della riga.",
                    )
                }

            UpdateOrderItemResult.PersistenceFailure ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Impossibile salvare la riga. Riprova.",
                    )
                }
        }
    }

    private fun showUnavailable(message: String) {
        _uiState.value = OrderItemDetailUiState(
            isLoading = false,
            canSave = false,
            errorMessage = message,
        )
    }

    private fun OrderItemDetailUiState.withModifierAvailability(): OrderItemDetailUiState {
        if (category != ProductCategory.PIZZA) {
            return copy(
                canEditAdditions = false,
                additionMessage = null,
                canEditRemovals = false,
                removalMessage = null,
            )
        }

        val quantity = quantityInput.toIntOrNull()
        val isUncustomizedMultiple = quantity != null &&
            quantity > 1 &&
            selectedAdditionIds.isEmpty() &&
            selectedRemovalIngredientIds.isEmpty() &&
            !hasExistingNonModifierCustomization
        val hasCustomization = selectedAdditionIds.isNotEmpty() ||
            selectedRemovalIngredientIds.isNotEmpty() ||
            hasExistingNonModifierCustomization
        val canEditModifiers = quantity == 1 || hasCustomization
        val quantityMessage = if (isUncustomizedMultiple) {
            "Questa riga contiene $quantity pizze. Le personalizzazioni possono essere " +
                "modificate da questa schermata solo quando la quantità è 1."
        } else {
            null
        }
        return copy(
            canEditAdditions = canEditModifiers,
            additionMessage = when {
                quantityMessage != null -> quantityMessage
                !automaticExtrasPricingEnabled ->
                    "Le aggiunte non modificano automaticamente il prezzo di questo prodotto."
                else -> null
            },
            canEditRemovals = canEditModifiers,
            removalMessage = quantityMessage,
        )
    }

    private fun Money.toInputString(): String =
        "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        const val ORDER_ID_ARGUMENT = "orderId"
        const val ORDER_ITEM_ID_ARGUMENT = "orderItemId"
    }
}
