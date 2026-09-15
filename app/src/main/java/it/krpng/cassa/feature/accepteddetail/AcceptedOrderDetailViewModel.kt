package it.krpng.cassa.feature.accepteddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.acceptance.AcceptancePreviewOrdering
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.pricing.CalculateOrderTotal
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrder
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrderResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AcceptedOrderDetailLineUi(
    val itemId: String,
    val quantity: Int,
    val productName: String,
    val productPrintedName: String,
    val additionNames: List<String>,
    val removalNames: List<String>,
    val note: String?,
    val finalUnitPrice: Money,
    val lineTotal: Money?,
    val manualUnitPrice: Money?,
)

data class AcceptedOrderDetailSectionUi(
    val title: String,
    val lines: List<AcceptedOrderDetailLineUi>,
)

sealed interface AcceptedOrderDetailUiState {
    data object Loading : AcceptedOrderDetailUiState

    data class Content(
        val orderId: String,
        val displayNumber: String,
        val acceptedAtLabel: String,
        val totalLabel: String,
        val total: Money,
        val generalNote: String?,
        val sections: List<AcceptedOrderDetailSectionUi>,
    ) : AcceptedOrderDetailUiState

    data object Unavailable : AcceptedOrderDetailUiState

    data class Error(
        val message: String,
    ) : AcceptedOrderDetailUiState
}

@HiltViewModel
class AcceptedOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCurrentDayAcceptedOrder: GetCurrentDayAcceptedOrder,
) : ViewModel() {
    private val orderId: String =
        savedStateHandle.get<String>(ORDER_ID_ARGUMENT).orEmpty()

    private val _uiState =
        MutableStateFlow<AcceptedOrderDetailUiState>(AcceptedOrderDetailUiState.Loading)
    val uiState: StateFlow<AcceptedOrderDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        _uiState.value = AcceptedOrderDetailUiState.Loading
        observeJob = viewModelScope.launch {
            getCurrentDayAcceptedOrder.observe(orderId)
                .catch {
                    _uiState.value = AcceptedOrderDetailUiState.Error(
                        message = "Impossibile caricare il dettaglio ordine.",
                    )
                }
                .collect { result ->
                    _uiState.value = when (result) {
                        is GetCurrentDayAcceptedOrderResult.Available ->
                            result.order.toContent(result.zoneId)
                        GetCurrentDayAcceptedOrderResult.Unavailable ->
                            AcceptedOrderDetailUiState.Unavailable
                        GetCurrentDayAcceptedOrderResult.SettingsFailure ->
                            AcceptedOrderDetailUiState.Error(
                                message = "Impossibile caricare le impostazioni della giornata operativa.",
                            )
                    }
                }
        }
    }

    private fun Order.toContent(zoneId: ZoneId): AcceptedOrderDetailUiState.Content {
        val sections = AcceptancePreviewOrdering.groupByCategory(items)
        val lineTotals = CalculateOrderTotal.fromPersistedItems(items)
            .lineTotals
            .associate { it.itemId to it.lineTotal }
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zoneId)
        return AcceptedOrderDetailUiState.Content(
            orderId = id,
            displayNumber = requireNotNull(displayNumber),
            acceptedAtLabel = formatter.format(requireNotNull(acceptedAt)),
            totalLabel = total.formatEur(),
            total = total,
            generalNote = generalNote,
            sections = sections.map { section ->
                AcceptedOrderDetailSectionUi(
                    title = section.title,
                    lines = section.items.map { item ->
                        AcceptedOrderDetailLineUi(
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
                            lineTotal = lineTotals[item.id],
                            manualUnitPrice = item.manualUnitPrice,
                        )
                    },
                )
            },
        )
    }

    companion object {
        const val ORDER_ID_ARGUMENT = "orderId"
    }
}
