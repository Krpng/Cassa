package it.krpng.cassa.feature.acceptance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.acceptance.AcceptancePreviewOrdering
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.pricing.CalculateOrderTotal
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.OrderRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AcceptancePreviewLineUi(
    val itemId: String,
    val quantity: Int,
    val productName: String,
    val productPrintedName: String,
    val additionNames: List<String>,
    val removalNames: List<String>,
    val note: String?,
    val finalUnitPrice: Money,
    val lineTotal: Money?,
)

data class AcceptancePreviewSectionUi(
    val title: String,
    val lines: List<AcceptancePreviewLineUi>,
)

sealed interface AcceptancePreviewUiState {
    data object Loading : AcceptancePreviewUiState

    data object NotFound : AcceptancePreviewUiState

    data object NotDraft : AcceptancePreviewUiState

    data object EmptyDraft : AcceptancePreviewUiState

    data class Ready(
        val draftId: String,
        val sections: List<AcceptancePreviewSectionUi>,
        val generalNote: String?,
        val orderTotal: OrderTotalResult,
    ) : AcceptancePreviewUiState {
        /** ACCEPT-001: AcceptOrder not wired; always false. */
        val isAcceptEnabled: Boolean
            get() = false

        val hasTotalOverflow: Boolean
            get() = orderTotal is OrderTotalResult.AmountOverflow
    }

    data class Failure(
        val message: String,
    ) : AcceptancePreviewUiState
}

/**
 * Read-only acceptance preview. Observes Room draft; never mutates order or numbering.
 */
@HiltViewModel
class AcceptancePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val draftId: String =
        checkNotNull(savedStateHandle.get<String>(DRAFT_ID_ARGUMENT)) {
            "Missing draft id for acceptance preview"
        }

    private val _uiState =
        MutableStateFlow<AcceptancePreviewUiState>(AcceptancePreviewUiState.Loading)
    val uiState: StateFlow<AcceptancePreviewUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeDraft()
    }

    fun retry() {
        observeDraft()
    }

    private fun observeDraft() {
        observationJob?.cancel()
        _uiState.value = AcceptancePreviewUiState.Loading
        observationJob = viewModelScope.launch {
            orderRepository.observeById(draftId)
                .catch {
                    _uiState.value = AcceptancePreviewUiState.Failure(
                        message = "Impossibile caricare l'anteprima.",
                    )
                }
                .collect { order ->
                    _uiState.value = when {
                        order == null -> AcceptancePreviewUiState.NotFound
                        order.status != OrderStatus.DRAFT -> AcceptancePreviewUiState.NotDraft
                        order.items.isEmpty() -> AcceptancePreviewUiState.EmptyDraft
                        else -> {
                            val sections = AcceptancePreviewOrdering.groupByCategory(order.items)
                            val totals = CalculateOrderTotal.fromPersistedItems(
                                sections.flatMap { it.items },
                            )
                            val lineTotalById = totals.lineTotals.associate { it.itemId to it.lineTotal }
                            AcceptancePreviewUiState.Ready(
                                draftId = order.id,
                                sections = sections.map { section ->
                                    AcceptancePreviewSectionUi(
                                        title = section.title,
                                        lines = section.items.map { item ->
                                            AcceptancePreviewLineUi(
                                                itemId = item.id,
                                                quantity = item.quantity,
                                                productName = item.productNameSnapshot,
                                                productPrintedName = item.productPrintedNameSnapshot,
                                                additionNames = item.additions
                                                    .sortedBy { it.displayOrder }
                                                    .map { it.nameSnapshot },
                                                removalNames = item.removals
                                                    .sortedBy { it.displayOrder }
                                                    .map { it.nameSnapshot },
                                                note = item.note,
                                                finalUnitPrice = item.finalUnitPrice,
                                                lineTotal = lineTotalById[item.id],
                                            )
                                        },
                                    )
                                },
                                generalNote = order.generalNote,
                                // Derived from persisted items — never orders.totalCents.
                                orderTotal = totals.orderTotal,
                            )
                        }
                    }
                }
        }
    }

    companion object {
        const val DRAFT_ID_ARGUMENT = "draftId"
    }
}
