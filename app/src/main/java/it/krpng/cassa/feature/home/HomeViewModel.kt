package it.krpng.cassa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeDraftSummary(
    val draftId: String,
    val itemCount: Int,
    val total: Money,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val activeDraft: HomeDraftSummary? = null,
    val newOrderConflict: HomeDraftSummary? = null,
    val showReplaceConfirmation: Boolean = false,
    val isNewOrderOperationInProgress: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeNavigationEvent {
    data class OpenDraft(val draftId: String) : HomeNavigationEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private var observationJob: Job? = null
    private var newOrderJob: Job? = null

    init {
        observeActiveDraft()
    }

    fun startNewOrder() {
        if (_uiState.value.isNewOrderOperationInProgress || newOrderJob?.isActive == true) return

        _uiState.update {
            it.copy(
                isNewOrderOperationInProgress = true,
                errorMessage = null,
            )
        }
        newOrderJob = viewModelScope.launch {
            try {
                val existingDraft = orderRepository.getActiveDraft()
                if (existingDraft != null) {
                    handleExistingDraft(existingDraft)
                } else {
                    handleCreateResult(orderRepository.createDraft())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showOperationError("Impossibile creare un nuovo ordine.")
            }
        }
    }

    fun resumeConflictingDraft() {
        if (_uiState.value.isNewOrderOperationInProgress) return
        val draftId = _uiState.value.newOrderConflict?.draftId ?: return
        clearConflict()
        _navigationEvents.trySend(HomeNavigationEvent.OpenDraft(draftId))
    }

    fun requestReplaceDraft() {
        val current = _uiState.value
        if (current.isNewOrderOperationInProgress || current.newOrderConflict == null) return
        _uiState.update { it.copy(showReplaceConfirmation = true, errorMessage = null) }
    }

    fun cancelReplaceDraft() {
        if (_uiState.value.isNewOrderOperationInProgress) return
        _uiState.update { it.copy(showReplaceConfirmation = false) }
    }

    fun cancelNewOrderConflict() {
        val current = _uiState.value
        // Secondary confirmation replaces the conflict dialog in composition; the disposed
        // conflict AlertDialog may still invoke onDismissRequest. Do not clear the replace
        // flow when that happens (regression: ELIMINA → confirmation wiped → conflict loops).
        if (current.isNewOrderOperationInProgress || current.showReplaceConfirmation) return
        clearConflict()
    }

    fun confirmReplaceDraft() {
        val current = _uiState.value
        val conflict = current.newOrderConflict ?: return
        if (!current.showReplaceConfirmation ||
            current.isNewOrderOperationInProgress ||
            newOrderJob?.isActive == true
        ) {
            return
        }

        // Close both dialogs before the mutation so Flow / dismiss callbacks cannot reopen them.
        _uiState.update {
            it.copy(
                newOrderConflict = null,
                showReplaceConfirmation = false,
                isNewOrderOperationInProgress = true,
                errorMessage = null,
            )
        }
        newOrderJob = viewModelScope.launch {
            try {
                when (val result = orderRepository.replaceDraft(conflict.draftId)) {
                    is ReplaceDraftResult.Created -> openDraft(result.draft.id)
                    ReplaceDraftResult.OriginalNotFoundOrNotDraft ->
                        showReplacementError(
                            conflict,
                            "L'ordine in corso non è più disponibile.",
                        )
                    ReplaceDraftResult.Conflict ->
                        showReplacementError(conflict, "Esiste già un altro ordine in corso.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showReplacementError(conflict, "Impossibile sostituire l'ordine in corso.")
            }
        }
    }

    private suspend fun handleCreateResult(result: CreateDraftResult) {
        when (result) {
            is CreateDraftResult.Created -> openDraft(result.draft.id)
            CreateDraftResult.AlreadyExists -> {
                val existingDraft = orderRepository.getActiveDraft()
                if (existingDraft == null) {
                    showOperationError("Impossibile recuperare l'ordine in corso.")
                } else {
                    handleExistingDraft(existingDraft)
                }
            }
        }
    }

    private suspend fun handleExistingDraft(draft: Order) {
        if (draft.items.isEmpty()) {
            openDraft(draft.id)
        } else {
            _uiState.update {
                it.copy(
                    newOrderConflict = draft.toSummary(),
                    showReplaceConfirmation = false,
                    isNewOrderOperationInProgress = false,
                    errorMessage = null,
                )
            }
        }
    }

    private suspend fun openDraft(draftId: String) {
        _uiState.update {
            it.copy(
                newOrderConflict = null,
                showReplaceConfirmation = false,
                isNewOrderOperationInProgress = false,
                errorMessage = null,
            )
        }
        _navigationEvents.send(HomeNavigationEvent.OpenDraft(draftId))
    }

    private fun clearConflict() {
        _uiState.update {
            it.copy(
                newOrderConflict = null,
                showReplaceConfirmation = false,
                errorMessage = null,
            )
        }
    }

    private fun showOperationError(message: String) {
        _uiState.update {
            it.copy(
                showReplaceConfirmation = false,
                isNewOrderOperationInProgress = false,
                errorMessage = message,
            )
        }
    }

    private fun showReplacementError(conflict: HomeDraftSummary, message: String) {
        _uiState.update {
            it.copy(
                newOrderConflict = conflict,
                showReplaceConfirmation = false,
                isNewOrderOperationInProgress = false,
                errorMessage = message,
            )
        }
    }

    private fun observeActiveDraft() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            orderRepository.observeActiveDraft()
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeDraft = null,
                            errorMessage = "Impossibile controllare l'ordine in corso.",
                        )
                    }
                }
                .collect { draft ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeDraft = draft?.takeIf { order -> order.items.isNotEmpty() }
                                ?.toSummary(),
                        )
                    }
                }
        }
    }

    private fun Order.toSummary(): HomeDraftSummary = HomeDraftSummary(
        draftId = id,
        itemCount = items.sumOf { it.quantity },
        total = total,
    )
}
