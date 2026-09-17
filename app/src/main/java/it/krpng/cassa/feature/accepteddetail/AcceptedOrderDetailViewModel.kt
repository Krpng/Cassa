package it.krpng.cassa.feature.accepteddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.acceptance.AcceptancePreviewOrdering
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.pricing.CalculateOrderTotal
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.usecase.DuplicateAcceptedOrder
import it.krpng.cassa.domain.usecase.DuplicateAcceptedOrderOutcome
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrder
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrderResult
import it.krpng.cassa.domain.usecase.ReplaceDraftWithAcceptedOrderDuplicate
import it.krpng.cassa.domain.usecase.ReplaceDraftWithAcceptedOrderDuplicateOutcome
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

data class ActiveDraftConflictUi(
    val expectedDraftId: String,
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
        val isDuplicating: Boolean = false,
        val isReplacing: Boolean = false,
        val activeDraftConflict: ActiveDraftConflictUi? = null,
        val duplicateError: String? = null,
    ) : AcceptedOrderDetailUiState

    data object Unavailable : AcceptedOrderDetailUiState

    data class Error(
        val message: String,
    ) : AcceptedOrderDetailUiState
}

sealed interface AcceptedOrderDetailNavigationEvent {
    data class OpenNewOrder(
        val draftId: String,
    ) : AcceptedOrderDetailNavigationEvent
}

/**
 * Orthogonal accepted-print phase (D-068 / PRINT-021).
 * Independent from [AcceptedOrderDetailUiState] so observation refresh cannot wipe PRINTING.
 */
sealed interface AcceptedPrintUiState {
    data object Idle : AcceptedPrintUiState

    data object Printing : AcceptedPrintUiState

    data class Success(
        val message: String = "Ordine inviato alla stampante",
    ) : AcceptedPrintUiState

    data class Error(
        val message: String,
    ) : AcceptedPrintUiState
}

@HiltViewModel
class AcceptedOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCurrentDayAcceptedOrder: GetCurrentDayAcceptedOrder,
    private val duplicateAcceptedOrder: DuplicateAcceptedOrder,
    private val replaceDraftWithAcceptedOrderDuplicate: ReplaceDraftWithAcceptedOrderDuplicate,
    private val orderRepository: OrderRepository,
    private val printerService: PrinterService,
) : ViewModel() {
    private val orderId: String =
        savedStateHandle.get<String>(ORDER_ID_ARGUMENT).orEmpty()

    private val _uiState =
        MutableStateFlow<AcceptedOrderDetailUiState>(AcceptedOrderDetailUiState.Loading)
    val uiState: StateFlow<AcceptedOrderDetailUiState> = _uiState.asStateFlow()

    private val _acceptedPrintUiState =
        MutableStateFlow<AcceptedPrintUiState>(AcceptedPrintUiState.Idle)
    val acceptedPrintUiState: StateFlow<AcceptedPrintUiState> = _acceptedPrintUiState.asStateFlow()

    private val _navigationEvents = Channel<AcceptedOrderDetailNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private var observeJob: Job? = null
    private var duplicateJob: Job? = null
    private var replaceJob: Job? = null
    private var resumeJob: Job? = null
    private var acceptedPrintJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        observe()
    }

    /**
     * Explicit STAMPA action (D-068 / PRINT-021).
     * One tap → at most one [PrinterService.printAccepted] while a job is active.
     */
    fun runAcceptedPrint() {
        if (acceptedPrintJob?.isActive == true) return
        if (_acceptedPrintUiState.value is AcceptedPrintUiState.Printing) return
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        if (content.isBusy) return

        val acceptedOrderId = content.orderId
        acceptedPrintJob =
            viewModelScope.launch {
                _acceptedPrintUiState.value = AcceptedPrintUiState.Printing
                try {
                    when (val result = printerService.printAccepted(acceptedOrderId)) {
                        PrintResult.Success ->
                            _acceptedPrintUiState.value = AcceptedPrintUiState.Success()
                        is PrintResult.Failure ->
                            _acceptedPrintUiState.value =
                                AcceptedPrintUiState.Error(mapAcceptedPrintError(result.error))
                    }
                } catch (e: CancellationException) {
                    _acceptedPrintUiState.value = AcceptedPrintUiState.Idle
                    throw e
                } catch (_: Exception) {
                    _acceptedPrintUiState.value =
                        AcceptedPrintUiState.Error("Impossibile stampare l'ordine")
                }
            }
    }

    /** Clears transient success/error so feedback is not replayed after recomposition. */
    fun consumeAcceptedPrintFeedback() {
        when (_acceptedPrintUiState.value) {
            is AcceptedPrintUiState.Success,
            is AcceptedPrintUiState.Error,
            -> _acceptedPrintUiState.value = AcceptedPrintUiState.Idle
            AcceptedPrintUiState.Idle,
            AcceptedPrintUiState.Printing,
            -> Unit
        }
    }

    fun onDuplicateOrder() {
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        if (content.isBusy || duplicateJob?.isActive == true || replaceJob?.isActive == true) return
        if (acceptedPrintJob?.isActive == true) return
        if (_acceptedPrintUiState.value is AcceptedPrintUiState.Printing) return
        duplicateJob = viewModelScope.launch {
            _uiState.value = content.copy(
                isDuplicating = true,
                duplicateError = null,
                activeDraftConflict = null,
            )
            when (val outcome = duplicateAcceptedOrder(content.orderId)) {
                is DuplicateAcceptedOrderOutcome.Success -> {
                    _uiState.value = content.copy(
                        isDuplicating = false,
                        duplicateError = null,
                        activeDraftConflict = null,
                    )
                    _navigationEvents.send(
                        AcceptedOrderDetailNavigationEvent.OpenNewOrder(outcome.draftId),
                    )
                }
                is DuplicateAcceptedOrderOutcome.DraftConflict -> {
                    _uiState.value = content.copy(
                        isDuplicating = false,
                        duplicateError = null,
                        activeDraftConflict = ActiveDraftConflictUi(
                            expectedDraftId = outcome.existingDraftId,
                        ),
                    )
                }
                DuplicateAcceptedOrderOutcome.SourceUnavailable -> {
                    _uiState.value = content.copy(
                        isDuplicating = false,
                        activeDraftConflict = null,
                        duplicateError = "Ordine non disponibile per la duplicazione.",
                    )
                }
                DuplicateAcceptedOrderOutcome.SettingsFailure,
                DuplicateAcceptedOrderOutcome.PersistenceFailure,
                -> {
                    _uiState.value = content.copy(
                        isDuplicating = false,
                        activeDraftConflict = null,
                        duplicateError = "Impossibile creare il nuovo ordine.",
                    )
                }
            }
        }
    }

    fun onConflictCancel() {
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        if (content.isReplacing) return
        _uiState.value = content.copy(activeDraftConflict = null, duplicateError = null)
    }

    fun onConflictResume() {
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        val conflict = content.activeDraftConflict ?: return
        if (content.isBusy || resumeJob?.isActive == true) return
        resumeJob = viewModelScope.launch {
            val active = orderRepository.getActiveDraft()
            when {
                active == null -> {
                    _uiState.value = content.copy(
                        activeDraftConflict = null,
                        duplicateError = "L'ordine in corso non è più disponibile.",
                    )
                }
                active.id != conflict.expectedDraftId -> {
                    _uiState.value = content.copy(
                        activeDraftConflict = null,
                        duplicateError = "L'ordine in corso è cambiato. Riprova.",
                    )
                }
                else -> {
                    _uiState.value = content.copy(
                        activeDraftConflict = null,
                        duplicateError = null,
                    )
                    _navigationEvents.send(
                        AcceptedOrderDetailNavigationEvent.OpenNewOrder(active.id),
                    )
                }
            }
        }
    }

    fun onConflictReplace() {
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        val conflict = content.activeDraftConflict ?: return
        if (content.isBusy || replaceJob?.isActive == true) return
        replaceJob = viewModelScope.launch {
            _uiState.value = content.copy(
                isReplacing = true,
                duplicateError = null,
                activeDraftConflict = conflict,
            )
            when (
                val outcome = replaceDraftWithAcceptedOrderDuplicate(
                    sourceOrderId = content.orderId,
                    expectedDraftId = conflict.expectedDraftId,
                )
            ) {
                is ReplaceDraftWithAcceptedOrderDuplicateOutcome.Success -> {
                    _uiState.value = content.copy(
                        isReplacing = false,
                        activeDraftConflict = null,
                        duplicateError = null,
                    )
                    _navigationEvents.send(
                        AcceptedOrderDetailNavigationEvent.OpenNewOrder(outcome.draftId),
                    )
                }
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftMissing -> {
                    _uiState.value = content.copy(
                        isReplacing = false,
                        activeDraftConflict = null,
                        duplicateError = "L'ordine in corso non è più disponibile.",
                    )
                }
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.DraftChanged -> {
                    _uiState.value = content.copy(
                        isReplacing = false,
                        activeDraftConflict = null,
                        duplicateError = "L'ordine in corso è cambiato. Riprova.",
                    )
                }
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.SourceUnavailable -> {
                    _uiState.value = content.copy(
                        isReplacing = false,
                        activeDraftConflict = null,
                        duplicateError = "Ordine non disponibile per la duplicazione.",
                    )
                }
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.SettingsFailure,
                ReplaceDraftWithAcceptedOrderDuplicateOutcome.PersistenceFailure,
                -> {
                    _uiState.value = content.copy(
                        isReplacing = false,
                        activeDraftConflict = conflict,
                        duplicateError = "Impossibile sostituire l'ordine in corso.",
                    )
                }
            }
        }
    }

    fun clearDuplicateError() {
        val content = _uiState.value as? AcceptedOrderDetailUiState.Content ?: return
        if (content.duplicateError != null) {
            _uiState.value = content.copy(duplicateError = null)
        }
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
                    val previous = _uiState.value as? AcceptedOrderDetailUiState.Content
                    _uiState.value = when (result) {
                        is GetCurrentDayAcceptedOrderResult.Available ->
                            result.order.toContent(result.zoneId).withTransient(previous)
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

    private fun AcceptedOrderDetailUiState.Content.withTransient(
        previous: AcceptedOrderDetailUiState.Content?,
    ): AcceptedOrderDetailUiState.Content {
        if (previous == null) return this
        return copy(
            isDuplicating = previous.isDuplicating,
            isReplacing = previous.isReplacing,
            activeDraftConflict = previous.activeDraftConflict,
            duplicateError = previous.duplicateError,
        )
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

    private fun mapAcceptedPrintError(error: PrinterError): String =
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
            -> "Impossibile stampare l'ordine"
        }
}

private val AcceptedOrderDetailUiState.Content.isBusy: Boolean
    get() = isDuplicating || isReplacing
