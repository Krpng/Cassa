package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.usecase.UpdateOrderItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderItemDetailUiState(
    val isLoading: Boolean = true,
    val canSave: Boolean = false,
    val productName: String = "",
    val automaticUnitPrice: Money = Money.ZERO,
    val quantityInput: String = "1",
    val note: String = "",
    val manualPriceInput: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val validationErrors: OrderItemFormErrors = OrderItemFormErrors(),
    val errorMessage: String? = null,
)

@HiltViewModel
class OrderItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val updateOrderItem: UpdateOrderItem,
) : ViewModel() {
    private val orderId = savedStateHandle.get<String>(ORDER_ID_ARGUMENT).orEmpty()
    private val orderItemId = savedStateHandle.get<String>(ORDER_ITEM_ID_ARGUMENT).orEmpty()

    private val _uiState = MutableStateFlow(OrderItemDetailUiState())
    val uiState: StateFlow<OrderItemDetailUiState> = _uiState.asStateFlow()

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
            )
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
        _uiState.value = OrderItemDetailUiState()
        viewModelScope.launch {
            try {
                val order = orderRepository.getById(orderId)
                when {
                    order == null -> showUnavailable("Ordine non disponibile.")
                    order.status != OrderStatus.DRAFT ->
                        showUnavailable("Questo ordine non è modificabile.")

                    else -> {
                        val item = order.items.firstOrNull { candidate ->
                            candidate.id == orderItemId
                        }
                        if (item == null) {
                            showUnavailable("Riga ordine non disponibile.")
                        } else {
                            _uiState.value = OrderItemDetailUiState(
                                isLoading = false,
                                canSave = true,
                                productName = item.productNameSnapshot,
                                automaticUnitPrice = item.baseUnitPrice +
                                    item.automaticExtrasTotal,
                                quantityInput = item.quantity.toString(),
                                note = item.note.orEmpty(),
                                manualPriceInput = item.manualUnitPrice?.toInputString(),
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showUnavailable("Impossibile caricare la riga ordine.")
            }
        }
    }

    private fun saveValidated(fields: ValidatedOrderItemFields) {
        _uiState.update { state ->
            state.copy(
                isSaving = true,
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

    private fun Money.toInputString(): String =
        "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        const val ORDER_ID_ARGUMENT = "orderId"
        const val ORDER_ITEM_ID_ARGUMENT = "orderItemId"
    }
}
