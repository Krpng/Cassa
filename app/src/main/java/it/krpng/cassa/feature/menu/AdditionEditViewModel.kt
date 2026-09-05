package it.krpng.cassa.feature.menu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.repository.AdditionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdditionEditUiState(
    val isLoading: Boolean = true,
    val additionId: Long? = null,
    val name: String = "",
    val printedName: String = "",
    val priceInput: String = "0,00",
    val active: Boolean = true,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val savedAdditionId: Long? = null,
    val validationErrors: AdditionFormErrors = AdditionFormErrors(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AdditionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val additionRepository: AdditionRepository,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private val requestedAdditionId = savedStateHandle.get<Long>(ADDITION_ID_ARGUMENT)
        ?.takeIf { additionId -> additionId > 0 }

    private val _uiState = MutableStateFlow(
        AdditionEditUiState(
            isLoading = requestedAdditionId != null,
            additionId = requestedAdditionId,
            canSave = requestedAdditionId == null,
        ),
    )
    val uiState: StateFlow<AdditionEditUiState> = _uiState.asStateFlow()

    private var originalAddition: Addition? = null

    init {
        loadAddition()
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

    fun updateActive(value: Boolean) {
        _uiState.update { state -> state.copy(active = value, errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || !state.canSave) return

        when (
            val validation = AdditionFormValidator.validate(
                name = state.name,
                printedName = state.printedName,
                priceInput = state.priceInput,
            )
        ) {
            is AdditionFormValidationResult.Invalid -> {
                _uiState.update { current ->
                    current.copy(validationErrors = validation.errors, errorMessage = null)
                }
            }

            is AdditionFormValidationResult.Valid -> saveValidated(validation.fields)
        }
    }

    private fun loadAddition() {
        val additionId = requestedAdditionId ?: return

        viewModelScope.launch {
            try {
                val addition = additionRepository.getById(additionId)
                if (addition == null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = false,
                            errorMessage = "Aggiunta non trovata.",
                        )
                    }
                    return@launch
                }

                originalAddition = addition
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        canSave = true,
                        name = addition.name,
                        printedName = addition.printedName.orEmpty(),
                        priceInput = addition.price.toInputString(),
                        active = addition.active,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        canSave = false,
                        errorMessage = "Impossibile caricare l'aggiunta.",
                    )
                }
            }
        }
    }

    private fun saveValidated(fields: ValidatedAdditionFields) {
        val state = _uiState.value
        val now = clockProvider.now()
        val existing = originalAddition
        val addition = Addition(
            id = existing?.id ?: 0,
            name = fields.name,
            normalizedName = TextNormalizer.normalize(fields.name),
            printedName = fields.printedName,
            price = fields.price,
            active = state.active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        _uiState.update { current ->
            current.copy(
                isSaving = true,
                validationErrors = AdditionFormErrors(),
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val savedId = if (existing == null) {
                    additionRepository.create(addition)
                } else {
                    if (!additionRepository.update(addition)) {
                        error("Addition no longer exists")
                    }
                    addition.id
                }
                _uiState.update { current ->
                    current.copy(isSaving = false, savedAdditionId = savedId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        errorMessage = "Impossibile salvare l'aggiunta. Verifica che il nome non sia già usato.",
                    )
                }
            }
        }
    }

    private fun Money.toInputString(): String =
        "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

    companion object {
        const val ADDITION_ID_ARGUMENT = "additionId"
    }
}
