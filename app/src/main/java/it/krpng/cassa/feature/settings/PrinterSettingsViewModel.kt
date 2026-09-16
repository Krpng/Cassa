package it.krpng.cassa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.repository.PrinterSettingsRepository
import it.krpng.cassa.platform.bluetooth.BluetoothAdapterAvailability
import it.krpng.cassa.platform.bluetooth.BluetoothAdapterStateProvider
import it.krpng.cassa.platform.bluetooth.BluetoothPermissionManager
import it.krpng.cassa.platform.bluetooth.BluetoothPermissionRequestability
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevicesProvider
import it.krpng.cassa.platform.bluetooth.BondedDevicesError
import it.krpng.cassa.platform.bluetooth.BondedDevicesResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Stampante section ViewModel (D-062 / D-063 / BT-006 / BT-007).
 *
 * Owns bonded list + selection + PricePrintMode UI state and orthogonal test-print phase.
 * Does not open system settings intents or call [it.krpng.cassa.domain.printer.PrinterDriver].
 */
@HiltViewModel
class PrinterSettingsViewModel @Inject constructor(
    private val permissionManager: BluetoothPermissionManager,
    private val adapterStateProvider: BluetoothAdapterStateProvider,
    private val bondedDevicesProvider: BondedBluetoothDevicesProvider,
    private val printerSettingsRepository: PrinterSettingsRepository,
    private val printerService: PrinterService,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<PrinterSettingsUiState>(PrinterSettingsUiState.Loading)
    val uiState: StateFlow<PrinterSettingsUiState> = _uiState.asStateFlow()

    private val _testPrintUiState =
        MutableStateFlow<TestPrintUiState>(TestPrintUiState.Idle)
    val testPrintUiState: StateFlow<TestPrintUiState> = _testPrintUiState.asStateFlow()

    private var loadJob: Job? = null
    private var persistJob: Job? = null
    private var testPrintJob: Job? = null

    /** True after this UI has received at least one runtime permission launcher result. */
    private var permissionRequestAttempted: Boolean = false

    /** Last Activity [shouldShowRequestPermissionRationale] snapshot from Compose. */
    private var shouldShowRationale: (String) -> Boolean = { false }

    /**
     * Reloads permission → adapter → bonded → selection → price mode.
     * Does **not** start or cancel an in-flight test print (D-063).
     */
    fun refresh(shouldShowRationale: (String) -> Boolean = this.shouldShowRationale) {
        this.shouldShowRationale = shouldShowRationale
        loadJob?.cancel()
        _uiState.value = PrinterSettingsUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = loadState()
        }
    }

    /**
     * Called after a Compose permission launcher result.
     * Marks that a request occurred, then re-evaluates grant + requestability.
     */
    fun onPermissionResult(shouldShowRationale: (String) -> Boolean) {
        permissionRequestAttempted = true
        this.shouldShowRationale = shouldShowRationale
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (permissionManager.areRequiredRuntimePermissionsGranted()) {
                _uiState.value = loadState()
            } else {
                _uiState.value = missingPermissionUiState()
            }
        }
    }

    fun retryPermission() {
        val current = _uiState.value
        if (current !is PrinterSettingsUiState.PermissionDenied) return
        if (!current.requestable) return
        _uiState.value =
            PrinterSettingsUiState.PermissionRequired(
                missingPermissions = current.missingPermissions,
                requestable = true,
            )
    }

    fun selectPrinter(id: String) {
        val ready = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        if (id.isBlank()) return
        if (id == ready.selectedPrinterId && ready.persistenceMessage == null) return
        if (persistJob?.isActive == true) return

        persistJob = viewModelScope.launch {
            try {
                printerSettingsRepository.setSelectedPrinterId(id)
                val selected = printerSettingsRepository.getSelectedPrinterId()
                applyReadyMutation { current ->
                    current.copy(
                        selectedPrinterId = selected,
                        selectedIsStale = isStale(selected, current.devices),
                        persistenceMessage = null,
                        pendingRetry = null,
                    )
                }
            } catch (_: Exception) {
                applyReadyMutation { current ->
                    current.copy(
                        persistenceMessage = "Impossibile salvare la stampante selezionata.",
                        pendingRetry = PersistenceRetry.SelectPrinter(id),
                    )
                }
            }
        }
    }

    fun clearPrinter() {
        val ready = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        if (ready.selectedPrinterId == null && ready.persistenceMessage == null) return
        if (persistJob?.isActive == true) return

        persistJob = viewModelScope.launch {
            try {
                printerSettingsRepository.clearSelectedPrinterId()
                val selected = printerSettingsRepository.getSelectedPrinterId()
                applyReadyMutation { current ->
                    current.copy(
                        selectedPrinterId = selected,
                        selectedIsStale = false,
                        persistenceMessage = null,
                        pendingRetry = null,
                    )
                }
            } catch (_: Exception) {
                applyReadyMutation { current ->
                    current.copy(
                        persistenceMessage = "Impossibile rimuovere la stampante selezionata.",
                        pendingRetry = PersistenceRetry.ClearPrinter,
                    )
                }
            }
        }
    }

    fun selectPricePrintMode(mode: PricePrintMode) {
        val ready = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        if (mode == ready.pricePrintMode && ready.persistenceMessage == null) return
        if (persistJob?.isActive == true) return

        persistJob = viewModelScope.launch {
            try {
                printerSettingsRepository.updatePricePrintMode(mode)
                val persisted = printerSettingsRepository.getPricePrintMode()
                applyReadyMutation { current ->
                    current.copy(
                        pricePrintMode = persisted,
                        persistenceMessage = null,
                        pendingRetry = null,
                    )
                }
            } catch (_: Exception) {
                applyReadyMutation { current ->
                    current.copy(
                        persistenceMessage = "Impossibile salvare la modalità prezzi.",
                        pendingRetry = PersistenceRetry.SelectPriceMode(mode),
                    )
                }
            }
        }
    }

    /**
     * Explicit STAMPA DI PROVA action (D-063).
     * One tap → at most one [PrinterService.testPrint] while a job is active.
     */
    fun runTestPrint() {
        if (testPrintJob?.isActive == true) return
        if (_testPrintUiState.value is TestPrintUiState.Printing) return
        val ready = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        if (ready.selectedPrinterId == null || ready.selectedIsStale) return

        testPrintJob =
            viewModelScope.launch {
                _testPrintUiState.value = TestPrintUiState.Printing
                try {
                    when (val result = printerService.testPrint()) {
                        PrintResult.Success ->
                            _testPrintUiState.value = TestPrintUiState.Success()
                        is PrintResult.Failure ->
                            _testPrintUiState.value =
                                TestPrintUiState.Error(mapTestPrintError(result.error))
                    }
                } catch (e: CancellationException) {
                    _testPrintUiState.value = TestPrintUiState.Idle
                    throw e
                } catch (_: Exception) {
                    _testPrintUiState.value =
                        TestPrintUiState.Error("Impossibile completare la stampa di prova")
                }
            }
    }

    /** Clears transient success/error so feedback is not replayed after recomposition. */
    fun consumeTestPrintFeedback() {
        when (_testPrintUiState.value) {
            is TestPrintUiState.Success,
            is TestPrintUiState.Error,
            -> _testPrintUiState.value = TestPrintUiState.Idle
            TestPrintUiState.Idle,
            TestPrintUiState.Printing,
            -> Unit
        }
    }

    /**
     * Applies a Ready mutation only when UI is still Ready — skips if a concurrent
     * [refresh] moved to Loading / another state so an older persistence job cannot
     * overwrite fresher authoritative state.
     */
    private fun applyReadyMutation(
        transform: (PrinterSettingsUiState.Ready) -> PrinterSettingsUiState.Ready,
    ) {
        val current = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        _uiState.value = transform(current)
    }

    fun retryPersistence() {
        val ready = _uiState.value as? PrinterSettingsUiState.Ready ?: return
        when (val retry = ready.pendingRetry) {
            is PersistenceRetry.SelectPrinter -> selectPrinter(retry.id)
            PersistenceRetry.ClearPrinter -> clearPrinter()
            is PersistenceRetry.SelectPriceMode -> selectPricePrintMode(retry.mode)
            null -> refresh()
        }
    }

    private suspend fun loadState(): PrinterSettingsUiState {
        if (!permissionManager.areRequiredRuntimePermissionsGranted()) {
            return missingPermissionUiState()
        }

        when (adapterStateProvider.currentAvailability()) {
            BluetoothAdapterAvailability.UNAVAILABLE ->
                return PrinterSettingsUiState.BluetoothUnavailable
            BluetoothAdapterAvailability.DISABLED ->
                return PrinterSettingsUiState.BluetoothDisabled
            BluetoothAdapterAvailability.ENABLED -> Unit
        }

        return when (val bonded = bondedDevicesProvider.listBondedDevices()) {
            is BondedDevicesResult.Failure ->
                when (bonded.error) {
                    BondedDevicesError.PermissionDenied -> missingPermissionUiState()
                    BondedDevicesError.BluetoothUnavailable ->
                        PrinterSettingsUiState.BluetoothUnavailable
                }
            is BondedDevicesResult.Success -> {
                val devices = bonded.devices.map(PrinterDeviceUi::fromBonded)
                val selected =
                    try {
                        printerSettingsRepository.getSelectedPrinterId()
                    } catch (_: Exception) {
                        return PrinterSettingsUiState.LoadError(
                            "Impossibile caricare le impostazioni stampante.",
                        )
                    }
                val priceMode =
                    try {
                        printerSettingsRepository.getPricePrintMode()
                    } catch (_: Exception) {
                        return PrinterSettingsUiState.LoadError(
                            "Impossibile caricare le impostazioni stampante.",
                        )
                    }
                PrinterSettingsUiState.Ready(
                    devices = devices,
                    selectedPrinterId = selected,
                    selectedIsStale = isStale(selected, devices),
                    pricePrintMode = priceMode,
                )
            }
        }
    }

    private fun missingPermissionUiState(): PrinterSettingsUiState {
        val missing = permissionManager.missingRuntimePermissions()
        val requestable =
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = missing,
                alreadyRequestedByUi = permissionRequestAttempted,
                shouldShowRationale = shouldShowRationale,
            )
        return if (permissionRequestAttempted) {
            PrinterSettingsUiState.PermissionDenied(
                missingPermissions = missing,
                requestable = requestable,
            )
        } else {
            PrinterSettingsUiState.PermissionRequired(
                missingPermissions = missing,
                requestable = requestable,
            )
        }
    }

    private fun isStale(
        selectedPrinterId: String?,
        devices: List<PrinterDeviceUi>,
    ): Boolean =
        selectedPrinterId != null && devices.none { it.id == selectedPrinterId }

    private fun mapTestPrintError(error: PrinterError): String =
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
                "Connessione interrotta durante la stampa di prova"
            PrinterError.Timeout ->
                "Timeout di connessione alla stampante"
            PrinterError.PrintFailed,
            PrinterError.UnsupportedEncoding,
            PrinterError.UnencodableCharacter,
            PrinterError.InvalidPrinterProfile,
            PrinterError.OrderNotFound,
            PrinterError.InvalidOrderState,
            PrinterError.Unknown,
            -> "Impossibile completare la stampa di prova"
        }
}
