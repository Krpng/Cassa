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
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.OrderRepository
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
    val automaticExtrasPricingEnabled: Boolean = false,
    val hasExistingNonAdditionCustomization: Boolean = false,
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
    private val updateOrderItem: UpdateOrderItem,
) : ViewModel() {
    private val orderId = savedStateHandle.get<String>(ORDER_ID_ARGUMENT).orEmpty()
    private val orderItemId = savedStateHandle.get<String>(ORDER_ITEM_ID_ARGUMENT).orEmpty()

    private val _uiState = MutableStateFlow(OrderItemDetailUiState())
    val uiState: StateFlow<OrderItemDetailUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var fieldsInitialized = false
    private var hasLocalAdditionChanges = false

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
            ).withAdditionAvailability()
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
            ).withAdditionAvailability()
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
        _uiState.value = OrderItemDetailUiState()
        observationJob = viewModelScope.launch {
            try {
                combine(
                    orderRepository.observeById(orderId),
                    additionRepository.observeActive(),
                ) { order, additions -> order to additions }
                    .collect { (order, additions) ->
                        applyObservedState(order, additions)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showUnavailable("Impossibile caricare la riga ordine.")
            }
        }
    }

    private fun applyObservedState(order: Order?, additions: List<Addition>) {
        when {
            order == null -> showUnavailable("Ordine non disponibile.")
            order.status != OrderStatus.DRAFT ->
                showUnavailable("Questo ordine non è modificabile.")

            else -> {
                val item = order.items.firstOrNull { candidate -> candidate.id == orderItemId }
                if (item == null) {
                    showUnavailable("Riga ordine non disponibile.")
                } else {
                    showEditable(item, additions)
                }
            }
        }
    }

    private fun showEditable(item: OrderItem, activeAdditions: List<Addition>) {
        val persistedSelectedIds = item.additions.mapNotNull { addition -> addition.additionId }
        val selectedIds = if (hasLocalAdditionChanges) {
            _uiState.value.selectedAdditionIds
        } else {
            persistedSelectedIds
        }
        val isPizza = item.categorySnapshot == ProductCategory.PIZZA
        val hasExistingNonAdditionCustomization = item.removals.isNotEmpty() ||
            !item.note.isNullOrBlank() || item.manualUnitPrice != null
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
                automaticExtrasPricingEnabled = item.automaticExtrasPricingSnapshot,
                hasExistingNonAdditionCustomization = hasExistingNonAdditionCustomization,
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
            initialized.withAdditionAvailability()
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
            state.selectedAdditionIds.isNotEmpty()
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

            UpdateOrderItemResult.AmbiguousPizzaQuantity ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage =
                            "Scegli prima se modificare una pizza o tutte quelle della riga.",
                    )
                }

            UpdateOrderItemResult.AutomaticExtrasPricingNotSupported ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Gestione aggiunte non disponibile per questa pizza.",
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

    private fun OrderItemDetailUiState.withAdditionAvailability(): OrderItemDetailUiState {
        if (category != ProductCategory.PIZZA) {
            return copy(canEditAdditions = false, additionMessage = null)
        }
        if (!automaticExtrasPricingEnabled) {
            return copy(
                canEditAdditions = false,
                additionMessage =
                    "Le aggiunte per questa pizza saranno gestite nel passaggio dedicato.",
            )
        }

        val quantity = quantityInput.toIntOrNull()
        val isUncustomizedMultiple = quantity != null &&
            quantity > 1 &&
            selectedAdditionIds.isEmpty() &&
            !hasExistingNonAdditionCustomization
        return if (isUncustomizedMultiple) {
            copy(
                canEditAdditions = false,
                additionMessage = "Questa riga contiene $quantity pizze. " +
                    "Le aggiunte possono essere modificate da questa schermata " +
                    "solo quando la quantità è 1.",
            )
        } else {
            copy(canEditAdditions = quantity == 1 || selectedAdditionIds.isNotEmpty() ||
                hasExistingNonAdditionCustomization, additionMessage = null)
        }
    }

    private fun Money.toInputString(): String =
        "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        const val ORDER_ID_ARGUMENT = "orderId"
        const val ORDER_ITEM_ID_ARGUMENT = "orderItemId"
    }
}
