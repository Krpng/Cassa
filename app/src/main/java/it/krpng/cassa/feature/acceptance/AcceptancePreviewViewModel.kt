package it.krpng.cassa.feature.acceptance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.acceptance.AcceptancePreviewCategorySection
import it.krpng.cassa.domain.acceptance.AcceptancePreviewOrdering
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.pricing.CalculateOrderTotal
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.usecase.AcceptOrder
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
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
        val isAccepting: Boolean = false,
        val acceptError: String? = null,
    ) : AcceptancePreviewUiState {
        val hasTotalOverflow: Boolean
            get() = orderTotal is OrderTotalResult.AmountOverflow

        val isAcceptEnabled: Boolean
            get() = !hasTotalOverflow && !isAccepting
    }

    data class Accepted(
        val orderId: String,
        val displayNumber: String,
        val total: Money,
        val acceptedAt: Instant,
        val businessDate: LocalDate,
        val sections: List<AcceptancePreviewSectionUi>,
        val generalNote: String?,
        val isCreatingNewOrder: Boolean = false,
        val actionError: String? = null,
    ) : AcceptancePreviewUiState

    data class Failure(
        val message: String,
    ) : AcceptancePreviewUiState
}

sealed interface AcceptanceNavigationEvent {
    data object GoHome : AcceptanceNavigationEvent

    data class OpenNewOrder(val draftId: String) : AcceptanceNavigationEvent
}

/**
 * Orthogonal draft-print phase (D-064 / PRINT-020).
 * Independent from [AcceptancePreviewUiState] so print completion cannot overwrite
 * newer preview observation, and refresh cannot wipe PRINTING incorrectly.
 */
sealed interface DraftPrintUiState {
    data object Idle : DraftPrintUiState

    data object Printing : DraftPrintUiState

    data class Success(
        val message: String = "Bozza inviata alla stampante",
    ) : DraftPrintUiState

    data class Error(
        val message: String,
    ) : DraftPrintUiState
}

@HiltViewModel
class AcceptancePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val acceptOrder: AcceptOrder,
    private val printerService: PrinterService,
) : ViewModel() {
    private val draftId: String =
        checkNotNull(savedStateHandle.get<String>(DRAFT_ID_ARGUMENT)) {
            "Missing draft id for acceptance preview"
        }

    private val _uiState =
        MutableStateFlow<AcceptancePreviewUiState>(AcceptancePreviewUiState.Loading)
    val uiState: StateFlow<AcceptancePreviewUiState> = _uiState.asStateFlow()

    private val _draftPrintUiState =
        MutableStateFlow<DraftPrintUiState>(DraftPrintUiState.Idle)
    val draftPrintUiState: StateFlow<DraftPrintUiState> = _draftPrintUiState.asStateFlow()

    private val _navigationEvents = Channel<AcceptanceNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private var observationJob: Job? = null
    private var acceptJob: Job? = null
    private var newOrderJob: Job? = null
    private var draftPrintJob: Job? = null

    init {
        observeOrder()
    }

    fun retry() {
        observeOrder()
    }

    fun accept() {
        val current = _uiState.value
        if (current !is AcceptancePreviewUiState.Ready) return
        if (!current.isAcceptEnabled || acceptJob?.isActive == true) return
        if (draftPrintJob?.isActive == true) return
        if (_draftPrintUiState.value is DraftPrintUiState.Printing) return

        _uiState.value = current.copy(isAccepting = true, acceptError = null)
        acceptJob = viewModelScope.launch {
            try {
                when (val result = acceptOrder(current.draftId)) {
                    is AcceptOrderResult.Accepted -> {
                        // Room is source of truth; observation maps ACCEPTED → Accepted UI.
                        observeOrder(forceReload = true)
                    }
                    AcceptOrderResult.OrderNotFound ->
                        _uiState.value = AcceptancePreviewUiState.NotFound
                    AcceptOrderResult.OrderNotDraft ->
                        _uiState.value = AcceptancePreviewUiState.NotDraft
                    AcceptOrderResult.EmptyDraft ->
                        _uiState.value = AcceptancePreviewUiState.EmptyDraft
                    AcceptOrderResult.AmountOverflow ->
                        restoreReady(current, "Totale non calcolabile.")
                    AcceptOrderResult.InvalidStoredMode ->
                        restoreReady(current, "Modalità numerazione non valida.")
                    AcceptOrderResult.InvalidNumberingState ->
                        restoreReady(current, "Stato numerazione non valido.")
                    AcceptOrderResult.CounterOverflow ->
                        restoreReady(current, "Contatore sequenziale esaurito.")
                    AcceptOrderResult.CycleOverflow ->
                        restoreReady(current, "Ciclo casuale esaurito.")
                    AcceptOrderResult.PersistenceFailure ->
                        restoreReady(current, "Impossibile accettare l'ordine.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                restoreReady(current, "Impossibile accettare l'ordine.")
            }
        }
    }

    /**
     * Explicit STAMPA BOZZA action (D-064).
     * One tap → at most one [PrinterService.printDraft] while a job is active.
     */
    fun runDraftPrint() {
        if (draftPrintJob?.isActive == true) return
        if (_draftPrintUiState.value is DraftPrintUiState.Printing) return
        if (acceptJob?.isActive == true) return
        val ready = _uiState.value as? AcceptancePreviewUiState.Ready ?: return
        if (ready.isAccepting) return

        val orderId = ready.draftId
        draftPrintJob =
            viewModelScope.launch {
                _draftPrintUiState.value = DraftPrintUiState.Printing
                try {
                    when (val result = printerService.printDraft(orderId)) {
                        PrintResult.Success ->
                            _draftPrintUiState.value = DraftPrintUiState.Success()
                        is PrintResult.Failure ->
                            _draftPrintUiState.value =
                                DraftPrintUiState.Error(mapDraftPrintError(result.error))
                    }
                } catch (e: CancellationException) {
                    _draftPrintUiState.value = DraftPrintUiState.Idle
                    throw e
                } catch (_: Exception) {
                    _draftPrintUiState.value =
                        DraftPrintUiState.Error("Impossibile stampare la bozza")
                }
            }
    }

    /** Clears transient success/error so feedback is not replayed after recomposition. */
    fun consumeDraftPrintFeedback() {
        when (_draftPrintUiState.value) {
            is DraftPrintUiState.Success,
            is DraftPrintUiState.Error,
            -> _draftPrintUiState.value = DraftPrintUiState.Idle
            DraftPrintUiState.Idle,
            DraftPrintUiState.Printing,
            -> Unit
        }
    }

    fun goHome() {
        if (_uiState.value !is AcceptancePreviewUiState.Accepted) return
        _navigationEvents.trySend(AcceptanceNavigationEvent.GoHome)
    }

    fun startNewOrder() {
        val current = _uiState.value
        if (current !is AcceptancePreviewUiState.Accepted) return
        if (current.isCreatingNewOrder || newOrderJob?.isActive == true) return

        _uiState.value = current.copy(isCreatingNewOrder = true, actionError = null)
        newOrderJob = viewModelScope.launch {
            try {
                when (val result = orderRepository.createDraft()) {
                    is CreateDraftResult.Created -> {
                        _uiState.value = current.copy(isCreatingNewOrder = false, actionError = null)
                        _navigationEvents.send(
                            AcceptanceNavigationEvent.OpenNewOrder(result.draft.id),
                        )
                    }
                    CreateDraftResult.AlreadyExists -> {
                        val existing = orderRepository.getActiveDraft()
                        if (existing != null) {
                            _uiState.value =
                                current.copy(isCreatingNewOrder = false, actionError = null)
                            _navigationEvents.send(
                                AcceptanceNavigationEvent.OpenNewOrder(existing.id),
                            )
                        } else {
                            _uiState.value = current.copy(
                                isCreatingNewOrder = false,
                                actionError = "Impossibile creare un nuovo ordine.",
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = current.copy(
                    isCreatingNewOrder = false,
                    actionError = "Impossibile creare un nuovo ordine.",
                )
            }
        }
    }

    private fun restoreReady(
        previous: AcceptancePreviewUiState.Ready,
        message: String,
    ) {
        _uiState.value = previous.copy(isAccepting = false, acceptError = message)
    }

    private fun observeOrder(forceReload: Boolean = false) {
        observationJob?.cancel()
        if (!forceReload && _uiState.value is AcceptancePreviewUiState.Accepted) return
        val keepAcceptingUi =
            (_uiState.value as? AcceptancePreviewUiState.Ready)?.isAccepting == true
        if (!keepAcceptingUi && _uiState.value !is AcceptancePreviewUiState.Accepted) {
            _uiState.value = AcceptancePreviewUiState.Loading
        }
        observationJob = viewModelScope.launch {
            orderRepository.observeById(draftId)
                .catch {
                    _uiState.value = AcceptancePreviewUiState.Failure(
                        message = "Impossibile caricare l'anteprima.",
                    )
                }
                .collect { order ->
                    val accepting =
                        (_uiState.value as? AcceptancePreviewUiState.Ready)?.isAccepting == true
                    if (accepting && order?.status == OrderStatus.DRAFT) return@collect

                    _uiState.value = when {
                        order == null -> AcceptancePreviewUiState.NotFound
                        order.status == OrderStatus.ACCEPTED -> order.toAcceptedUiOrFailure()
                        order.status != OrderStatus.DRAFT -> AcceptancePreviewUiState.NotDraft
                        order.items.isEmpty() -> AcceptancePreviewUiState.EmptyDraft
                        else -> order.toReadyUi()
                    }
                }
        }
    }

    private fun Order.toReadyUi(): AcceptancePreviewUiState.Ready {
        val sections = AcceptancePreviewOrdering.groupByCategory(items)
        val totals = CalculateOrderTotal.fromPersistedItems(sections.flatMap { it.items })
        val lineTotalById = totals.lineTotals.associate { it.itemId to it.lineTotal }
        return AcceptancePreviewUiState.Ready(
            draftId = id,
            sections = sections.toUiSections(lineTotalById),
            generalNote = generalNote,
            orderTotal = totals.orderTotal,
            acceptError = (_uiState.value as? AcceptancePreviewUiState.Ready)?.acceptError,
        )
    }

    private fun Order.toAcceptedUiOrFailure(): AcceptancePreviewUiState {
        val number = displayNumber
        val at = acceptedAt
        val date = businessDate
        if (number == null || at == null || date == null) {
            return AcceptancePreviewUiState.Failure(
                message = "Ordine accettato incompleto.",
            )
        }
        val previous = _uiState.value as? AcceptancePreviewUiState.Accepted
        val sections = AcceptancePreviewOrdering.groupByCategory(items)
        val lineTotals = CalculateOrderTotal.fromPersistedItems(sections.flatMap { it.items })
            .lineTotals
            .associate { it.itemId to it.lineTotal }
        return AcceptancePreviewUiState.Accepted(
            orderId = id,
            displayNumber = number,
            total = total,
            acceptedAt = at,
            businessDate = date,
            sections = sections.toUiSections(lineTotals),
            generalNote = generalNote,
            isCreatingNewOrder = previous?.isCreatingNewOrder == true,
            actionError = previous?.actionError,
        )
    }

    private fun List<AcceptancePreviewCategorySection>.toUiSections(
        lineTotalById: Map<String, Money?>,
    ): List<AcceptancePreviewSectionUi> = map { section ->
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
    }

    companion object {
        const val DRAFT_ID_ARGUMENT = "draftId"
    }

    private fun mapDraftPrintError(error: PrinterError): String =
        when (error) {
            PrinterError.PermissionDenied ->
                "Autorizzazione Bluetooth necessaria"
            PrinterError.BluetoothDisabled ->
                "Bluetooth disattivato"
            PrinterError.PrinterNotConfigured ->
                "Nessuna stampante selezionata"
            PrinterError.ConnectionFailed ->
                "Impossibile connettersi alla stampante"
            PrinterError.ConnectionLost ->
                "Connessione interrotta durante la stampa"
            PrinterError.Timeout ->
                "Timeout di connessione alla stampante"
            PrinterError.PrintFailed,
            PrinterError.UnsupportedEncoding,
            PrinterError.UnencodableCharacter,
            PrinterError.InvalidPrinterProfile,
            PrinterError.OrderNotFound,
            PrinterError.InvalidOrderState,
            PrinterError.Unknown,
            -> "Impossibile stampare la bozza"
        }
}
