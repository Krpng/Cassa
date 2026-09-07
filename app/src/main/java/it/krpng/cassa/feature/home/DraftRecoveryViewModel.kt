package it.krpng.cassa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface DraftRecoveryUiState {
    data object Loading : DraftRecoveryUiState

    data object NoRecoveryNeeded : DraftRecoveryUiState

    data class DraftAvailable(
        val draft: Order,
        val showDeleteConfirmation: Boolean = false,
        val isDeleting: Boolean = false,
        val errorMessage: String? = null,
    ) : DraftRecoveryUiState

    data class Failure(
        val message: String,
    ) : DraftRecoveryUiState
}

@HiltViewModel
class DraftRecoveryViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DraftRecoveryUiState>(DraftRecoveryUiState.Loading)
    val uiState: StateFlow<DraftRecoveryUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        observeActiveDraft()
    }

    fun requestDelete() {
        val current = _uiState.value as? DraftRecoveryUiState.DraftAvailable ?: return
        if (current.isDeleting) return
        _uiState.value = current.copy(
            showDeleteConfirmation = true,
            errorMessage = null,
        )
    }

    fun cancelDelete() {
        val current = _uiState.value as? DraftRecoveryUiState.DraftAvailable ?: return
        if (current.isDeleting) return
        _uiState.value = current.copy(showDeleteConfirmation = false)
    }

    fun confirmDelete() {
        val current = _uiState.value as? DraftRecoveryUiState.DraftAvailable ?: return
        if (!current.showDeleteConfirmation || current.isDeleting) return

        _uiState.value = current.copy(
            showDeleteConfirmation = false,
            isDeleting = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                when (orderRepository.deleteDraft(current.draft.id)) {
                    DeleteDraftResult.Deleted -> {
                        _uiState.value = DraftRecoveryUiState.NoRecoveryNeeded
                    }

                    DeleteDraftResult.NotFoundOrNotDraft -> {
                        _uiState.value = current.copy(
                            showDeleteConfirmation = false,
                            isDeleting = false,
                            errorMessage = "L'ordine in corso non è più disponibile.",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = current.copy(
                    showDeleteConfirmation = false,
                    isDeleting = false,
                    errorMessage = "Impossibile eliminare l'ordine in corso.",
                )
            }
        }
    }

    fun retry() {
        _uiState.value = DraftRecoveryUiState.Loading
        observeActiveDraft()
    }

    private fun observeActiveDraft() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            orderRepository.observeActiveDraft()
                .catch {
                    _uiState.value = DraftRecoveryUiState.Failure(
                        message = "Impossibile controllare l'ordine in corso.",
                    )
                }
                .collect { draft ->
                    _uiState.value = if (draft == null || draft.items.isEmpty()) {
                        DraftRecoveryUiState.NoRecoveryNeeded
                    } else {
                        DraftRecoveryUiState.DraftAvailable(draft = draft)
                    }
                }
        }
    }
}
