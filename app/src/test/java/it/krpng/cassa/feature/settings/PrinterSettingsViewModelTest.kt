package it.krpng.cassa.feature.settings

import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.repository.PrinterSettingsRepository
import it.krpng.cassa.platform.bluetooth.BluetoothAdapterAvailability
import it.krpng.cassa.platform.bluetooth.BluetoothAdapterStateProvider
import it.krpng.cassa.platform.bluetooth.BluetoothPermissionManager
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevice
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevicesProvider
import it.krpng.cassa.platform.bluetooth.BondedDevicesError
import it.krpng.cassa.platform.bluetooth.BondedDevicesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterSettingsViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `A permission missing shows PermissionRequired`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                permissionGranted = false,
                requestNeeded = true,
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrinterSettingsUiState.PermissionRequired)
        assertTrue((state as PrinterSettingsUiState.PermissionRequired).requestable)
    }

    @Test
    fun `B permission granted lists bonded devices`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                devices =
                    listOf(
                        BondedBluetoothDevice("AA:BB:CC:DD:EE:01", "Cucina"),
                        BondedBluetoothDevice("AA:BB:CC:DD:EE:02", "Bar"),
                    ),
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(2, state.devices.size)
        assertEquals("Cucina", state.devices[0].displayName)
        assertEquals("AA:BB:CC:DD:EE:01", state.devices[0].id)
    }

    @Test
    fun `C Bluetooth disabled is distinct from empty bonded`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                adapterAvailability = BluetoothAdapterAvailability.DISABLED,
                devices = emptyList(),
            )
        advanceUntilIdle()

        assertEquals(
            PrinterSettingsUiState.BluetoothDisabled,
            viewModel.uiState.value,
        )
    }

    @Test
    fun `D no bonded devices shows empty Ready`() = runTest(mainDispatcher) {
        val viewModel = createViewModel(devices = emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertTrue(state.devices.isEmpty())
        assertNull(state.selectedPrinterId)
        assertFalse(state.selectedIsStale)
    }

    @Test
    fun `E selected bonded device is marked selected not stale`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "Net")),
                selectedId = "AA:01",
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals("AA:01", state.selectedPrinterId)
        assertFalse(state.selectedIsStale)
    }

    @Test
    fun `F stale persisted selection is flagged without auto-clear`() = runTest(mainDispatcher) {
        val repository =
            FakePrinterSettingsRepository(
                selectedId = "STALE:ID",
                priceMode = PricePrintMode.DETAILED,
            )
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "Alive")),
                repository = repository,
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals("STALE:ID", state.selectedPrinterId)
        assertTrue(state.selectedIsStale)
        assertEquals("STALE:ID", repository.selectedId)
    }

    @Test
    fun `G selection persistence success updates selected id`() = runTest(mainDispatcher) {
        val repository = FakePrinterSettingsRepository()
        val viewModel =
            createViewModel(
                devices =
                    listOf(
                        BondedBluetoothDevice("AA:01", "One"),
                        BondedBluetoothDevice("AA:02", "Two"),
                    ),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.selectPrinter("AA:02")
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals("AA:02", state.selectedPrinterId)
        assertFalse(state.selectedIsStale)
        assertNull(state.persistenceMessage)
        assertEquals("AA:02", repository.selectedId)
    }

    @Test
    fun `H selection persistence failure keeps previous selection`() = runTest(mainDispatcher) {
        val repository =
            FakePrinterSettingsRepository(
                selectedId = "AA:01",
                failSetSelected = true,
            )
        val viewModel =
            createViewModel(
                devices =
                    listOf(
                        BondedBluetoothDevice("AA:01", "One"),
                        BondedBluetoothDevice("AA:02", "Two"),
                    ),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.selectPrinter("AA:02")
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals("AA:01", state.selectedPrinterId)
        assertEquals("Impossibile salvare la stampante selezionata.", state.persistenceMessage)
        assertEquals("AA:01", repository.selectedId)
    }

    @Test
    fun `I clear selection succeeds`() = runTest(mainDispatcher) {
        val repository = FakePrinterSettingsRepository(selectedId = "AA:01")
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.clearPrinter()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertNull(state.selectedPrinterId)
        assertFalse(state.selectedIsStale)
        assertNull(repository.selectedId)
    }

    @Test
    fun `J duplicate device names stay distinct by id`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                devices =
                    listOf(
                        BondedBluetoothDevice("AA:01", "Same"),
                        BondedBluetoothDevice("AA:02", "Same"),
                    ),
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(2, state.devices.size)
        assertEquals("Same", state.devices[0].displayName)
        assertEquals("Same", state.devices[1].displayName)
        assertEquals("AA:01", state.devices[0].id)
        assertEquals("AA:02", state.devices[1].id)
    }

    @Test
    fun `K unnamed device uses Dispositivo Bluetooth fallback`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                devices =
                    listOf(
                        BondedBluetoothDevice("AA:99", null),
                        BondedBluetoothDevice("AA:98", "  "),
                    ),
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(PrinterDeviceUi.UNNAMED_FALLBACK, state.devices[0].displayName)
        assertEquals(PrinterDeviceUi.UNNAMED_FALLBACK, state.devices[1].displayName)
    }

    @Test
    fun `L PricePrintMode DETAILED load and select`() = runTest(mainDispatcher) {
        val repository = FakePrinterSettingsRepository(priceMode = PricePrintMode.DETAILED)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
            )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(PricePrintMode.DETAILED, loaded.pricePrintMode)

        viewModel.selectPricePrintMode(PricePrintMode.DETAILED)
        advanceUntilIdle()
        assertEquals(
            PricePrintMode.DETAILED,
            (viewModel.uiState.value as PrinterSettingsUiState.Ready).pricePrintMode,
        )
    }

    @Test
    fun `M PricePrintMode TOTAL_ONLY load and select`() = runTest(mainDispatcher) {
        val repository = FakePrinterSettingsRepository(priceMode = PricePrintMode.DETAILED)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.selectPricePrintMode(PricePrintMode.TOTAL_ONLY)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(PricePrintMode.TOTAL_ONLY, state.pricePrintMode)
        assertEquals(PricePrintMode.TOTAL_ONLY, repository.priceMode)
    }

    @Test
    fun `N price mode persistence failure keeps previous mode`() = runTest(mainDispatcher) {
        val repository =
            FakePrinterSettingsRepository(
                priceMode = PricePrintMode.DETAILED,
                failUpdatePriceMode = true,
            )
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.selectPricePrintMode(PricePrintMode.TOTAL_ONLY)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(PricePrintMode.DETAILED, state.pricePrintMode)
        assertEquals("Impossibile salvare la modalità prezzi.", state.persistenceMessage)
        assertEquals(PricePrintMode.DETAILED, repository.priceMode)
    }

    @Test
    fun `Bluetooth unavailable when adapter missing`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(adapterAvailability = BluetoothAdapterAvailability.UNAVAILABLE)
        advanceUntilIdle()
        assertEquals(PrinterSettingsUiState.BluetoothUnavailable, viewModel.uiState.value)
    }

    @Test
    fun `onPermissionResult denied exposes PermissionDenied`() = runTest(mainDispatcher) {
        val permission =
            FakePermissionManager(granted = false, requestNeeded = true)
        val viewModel =
            createViewModel(
                permissionManager = permission,
            )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PrinterSettingsUiState.PermissionRequired)

        viewModel.onPermissionResult(shouldShowRationale = { false })
        advanceUntilIdle()
        val denied = viewModel.uiState.value as PrinterSettingsUiState.PermissionDenied
        assertFalse(denied.requestable)
    }

    @Test
    fun `initial missing permission is requestable not permanent`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                permissionGranted = false,
                requestNeeded = true,
                shouldShowRationale = { false },
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.PermissionRequired
        assertTrue(state.requestable)
        assertTrue(state.missingPermissions.isNotEmpty())
    }

    @Test
    fun `requestable denial keeps requestable true when rationale true`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                permissionGranted = false,
                requestNeeded = true,
            )
        advanceUntilIdle()

        viewModel.onPermissionResult(shouldShowRationale = { true })
        advanceUntilIdle()

        val denied = viewModel.uiState.value as PrinterSettingsUiState.PermissionDenied
        assertTrue(denied.requestable)
    }

    @Test
    fun `non-requestable denial exposes app-settings path`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                permissionGranted = false,
                requestNeeded = true,
            )
        advanceUntilIdle()

        viewModel.onPermissionResult(shouldShowRationale = { false })
        advanceUntilIdle()

        val denied = viewModel.uiState.value as PrinterSettingsUiState.PermissionDenied
        assertFalse(denied.requestable)
    }

    @Test
    fun `refresh after non-requestable denial stays non-requestable`() = runTest(mainDispatcher) {
        val viewModel =
            createViewModel(
                permissionGranted = false,
                requestNeeded = true,
            )
        advanceUntilIdle()
        viewModel.onPermissionResult(shouldShowRationale = { false })
        advanceUntilIdle()

        viewModel.refresh(shouldShowRationale = { false })
        advanceUntilIdle()

        val denied = viewModel.uiState.value as PrinterSettingsUiState.PermissionDenied
        assertFalse(denied.requestable)
    }

    @Test
    fun `return from app settings after grant reaches Ready`() = runTest(mainDispatcher) {
        val permission = FakePermissionManager(granted = false, requestNeeded = true)
        val viewModel =
            createViewModel(
                permissionManager = permission,
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
            )
        advanceUntilIdle()
        viewModel.onPermissionResult(shouldShowRationale = { false })
        advanceUntilIdle()
        assertFalse(
            (viewModel.uiState.value as PrinterSettingsUiState.PermissionDenied).requestable,
        )

        permission.granted = true
        viewModel.refresh(shouldShowRationale = { false })
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(1, state.devices.size)
    }

    @Test
    fun `clear selection persistence failure keeps previous selection`() = runTest(mainDispatcher) {
        val repository =
            FakePrinterSettingsRepository(
                selectedId = "AA:01",
                failClearSelected = true,
            )
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
            )
        advanceUntilIdle()

        viewModel.clearPrinter()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals("AA:01", state.selectedPrinterId)
        assertFalse(state.selectedIsStale)
        assertEquals("Impossibile rimuovere la stampante selezionata.", state.persistenceMessage)
        assertEquals("AA:01", repository.selectedId)
    }

    @Test
    fun `refresh reloads after Bluetooth DISABLED to ENABLED`() = runTest(mainDispatcher) {
        val adapter =
            MutableAdapterStateProvider(BluetoothAdapterAvailability.DISABLED)
        val viewModel =
            createViewModel(
                adapterStateProvider = adapter,
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
            )
        advanceUntilIdle()
        assertEquals(PrinterSettingsUiState.BluetoothDisabled, viewModel.uiState.value)

        adapter.availability = BluetoothAdapterAvailability.ENABLED
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(1, state.devices.size)
        assertEquals("AA:01", state.devices[0].id)
    }

    @Test
    fun `refresh reloads bonded list from empty to devices`() = runTest(mainDispatcher) {
        val bonded = MutableBondedProvider(devices = emptyList())
        val viewModel = createViewModel(bondedProvider = bonded)
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as PrinterSettingsUiState.Ready).devices.isEmpty())

        bonded.devices = listOf(BondedBluetoothDevice("AA:02", "Nuova"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(1, state.devices.size)
        assertEquals("AA:02", state.devices[0].id)
        assertEquals("Nuova", state.devices[0].displayName)
    }

    @Test
    fun `refresh reloads permission after grant`() = runTest(mainDispatcher) {
        val permission = FakePermissionManager(granted = false, requestNeeded = true)
        val viewModel =
            createViewModel(
                permissionManager = permission,
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
            )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PrinterSettingsUiState.PermissionRequired)

        permission.granted = true
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PrinterSettingsUiState.Ready
        assertEquals(1, state.devices.size)
    }

    @Test
    fun `persist success does not overwrite non-Ready state after refresh`() =
        runTest(mainDispatcher) {
            val repository = FakePrinterSettingsRepository(selectedId = "AA:01")
            val adapter =
                MutableAdapterStateProvider(BluetoothAdapterAvailability.ENABLED)
            val viewModel =
                createViewModel(
                    adapterStateProvider = adapter,
                    devices =
                        listOf(
                            BondedBluetoothDevice("AA:01", "One"),
                            BondedBluetoothDevice("AA:02", "Two"),
                        ),
                    repository = repository,
                )
            advanceUntilIdle()

            viewModel.selectPrinter("AA:02")
            // Concurrent resume-style refresh before persist applies.
            adapter.availability = BluetoothAdapterAvailability.DISABLED
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(PrinterSettingsUiState.BluetoothDisabled, viewModel.uiState.value)
            // Persist may still have written DataStore; UI must not resurrect Ready from old snapshot.
            assertEquals("AA:02", repository.selectedId)
        }

    // --- BT-007 test print ---

    @Test
    fun `BT007 A no selected printer does not call testPrint`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService()
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = null,
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(0, printerService.testPrintCalls)
        assertEquals(TestPrintUiState.Idle, viewModel.testPrintUiState.value)
    }

    @Test
    fun `BT007 B stale selected printer does not call testPrint`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService()
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "Alive")),
                selectedId = "STALE:ID",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(0, printerService.testPrintCalls)
        assertEquals(TestPrintUiState.Idle, viewModel.testPrintUiState.value)
    }

    @Test
    fun `BT007 C valid selected printer calls testPrint once`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService(result = PrintResult.Success)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(1, printerService.testPrintCalls)
        assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Success)
        assertEquals(
            "Test stampa inviato",
            (viewModel.testPrintUiState.value as TestPrintUiState.Success).message,
        )
    }

    @Test
    fun `BT007 D double tap while printing calls testPrint once`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printerService =
            FakePrinterService(
                result = PrintResult.Success,
                blockUntil = gate,
            )
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()
        assertEquals(TestPrintUiState.Printing, viewModel.testPrintUiState.value)

        viewModel.runTestPrint()
        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(1, printerService.testPrintCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, printerService.testPrintCalls)
        assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Success)
    }

    @Test
    fun `BT007 E success clears printing and allows new explicit tap`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService(result = PrintResult.Success)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()
        assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Success)

        viewModel.consumeTestPrintFeedback()
        assertEquals(TestPrintUiState.Idle, viewModel.testPrintUiState.value)

        viewModel.runTestPrint()
        advanceUntilIdle()
        assertEquals(2, printerService.testPrintCalls)
    }

    @Test
    fun `BT007 F PermissionDenied maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.PermissionDenied,
            "Autorizzazione Bluetooth necessaria",
        )
    }

    @Test
    fun `BT007 G BluetoothDisabled maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.BluetoothDisabled,
            "Bluetooth disattivato",
        )
    }

    @Test
    fun `BT007 H PrinterNotConfigured maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.PrinterNotConfigured,
            "Nessuna stampante selezionata",
        )
    }

    @Test
    fun `BT007 I ConnectionFailed maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.ConnectionFailed,
            "Impossibile connettersi alla stampante",
        )
    }

    @Test
    fun `BT007 J ConnectionLost maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.ConnectionLost,
            "Connessione interrotta durante la stampa di prova",
        )
    }

    @Test
    fun `BT007 K Timeout maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.Timeout,
            "Timeout di connessione alla stampante",
        )
    }

    @Test
    fun `BT007 L PrintFailed and Unknown map generic failure`() = runTest(mainDispatcher) {
        assertMappedError(
            PrinterError.PrintFailed,
            "Impossibile completare la stampa di prova",
        )
        assertMappedError(
            PrinterError.Unknown,
            "Impossibile completare la stampa di prova",
        )
    }

    @Test
    fun `BT007 M unexpected service exception is recoverable`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService(throwOnTestPrint = true)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()

        val state = viewModel.testPrintUiState.value as TestPrintUiState.Error
        assertEquals("Impossibile completare la stampa di prova", state.message)

        viewModel.consumeTestPrintFeedback()
        viewModel.runTestPrint()
        advanceUntilIdle()
        assertEquals(2, printerService.testPrintCalls)
    }

    @Test
    fun `BT007 N manual retry after failure starts new job`() = runTest(mainDispatcher) {
        val printerService =
            FakePrinterService(result = PrintResult.Failure(PrinterError.ConnectionFailed))
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()
        assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Error)

        printerService.result = PrintResult.Success
        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(2, printerService.testPrintCalls)
        assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Success)
    }

    @Test
    fun `BT007 O testPrint does not touch printer settings repository`() = runTest(mainDispatcher) {
        val repository = FakePrinterSettingsRepository(selectedId = "AA:01")
        val printerService = FakePrinterService(result = PrintResult.Success)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                repository = repository,
                printerService = printerService,
            )
        advanceUntilIdle()

        val beforeSelected = repository.selectedId
        val beforePrice = repository.priceMode
        viewModel.runTestPrint()
        advanceUntilIdle()

        assertEquals(beforeSelected, repository.selectedId)
        assertEquals(beforePrice, repository.priceMode)
        assertEquals(0, repository.mutationCount)
        assertEquals(1, printerService.testPrintCalls)
    }

    @Test
    fun `BT007 P refresh during print does not overwrite config from print completion`() =
        runTest(mainDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val printerService =
                FakePrinterService(
                    result = PrintResult.Success,
                    blockUntil = gate,
                )
            val bonded = MutableBondedProvider(listOf(BondedBluetoothDevice("AA:01", "One")))
            val viewModel =
                createViewModel(
                    bondedProvider = bonded,
                    selectedId = "AA:01",
                    printerService = printerService,
                )
            advanceUntilIdle()

            viewModel.runTestPrint()
            advanceUntilIdle()
            assertEquals(TestPrintUiState.Printing, viewModel.testPrintUiState.value)

            bonded.devices =
                listOf(
                    BondedBluetoothDevice("AA:01", "One"),
                    BondedBluetoothDevice("AA:02", "Two"),
                )
            viewModel.refresh()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as PrinterSettingsUiState.Ready
            assertEquals(2, ready.devices.size)
            assertEquals(TestPrintUiState.Printing, viewModel.testPrintUiState.value)

            gate.complete(Unit)
            advanceUntilIdle()

            val after = viewModel.uiState.value as PrinterSettingsUiState.Ready
            assertEquals(2, after.devices.size)
            assertTrue(viewModel.testPrintUiState.value is TestPrintUiState.Success)
        }

    @Test
    fun `BT007 Q refresh does not auto invoke testPrint`() = runTest(mainDispatcher) {
        val printerService = FakePrinterService(result = PrintResult.Success)
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()
        assertEquals(0, printerService.testPrintCalls)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, printerService.testPrintCalls)
        assertEquals(TestPrintUiState.Idle, viewModel.testPrintUiState.value)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertMappedError(
        error: PrinterError,
        expectedMessage: String,
    ) {
        val printerService =
            FakePrinterService(result = PrintResult.Failure(error))
        val viewModel =
            createViewModel(
                devices = listOf(BondedBluetoothDevice("AA:01", "One")),
                selectedId = "AA:01",
                printerService = printerService,
            )
        advanceUntilIdle()

        viewModel.runTestPrint()
        advanceUntilIdle()

        val state = viewModel.testPrintUiState.value as TestPrintUiState.Error
        assertEquals(expectedMessage, state.message)
        // Configuration Ready remains intact after failure.
        assertTrue(viewModel.uiState.value is PrinterSettingsUiState.Ready)
    }

    private fun createViewModel(
        permissionGranted: Boolean = true,
        requestNeeded: Boolean = false,
        adapterAvailability: BluetoothAdapterAvailability = BluetoothAdapterAvailability.ENABLED,
        adapterStateProvider: BluetoothAdapterStateProvider =
            MutableAdapterStateProvider(adapterAvailability),
        devices: List<BondedBluetoothDevice> = emptyList(),
        bondedProvider: BondedBluetoothDevicesProvider? = null,
        selectedId: String? = null,
        priceMode: PricePrintMode = PricePrintMode.DETAILED,
        repository: FakePrinterSettingsRepository =
            FakePrinterSettingsRepository(selectedId = selectedId, priceMode = priceMode),
        permissionManager: BluetoothPermissionManager =
            FakePermissionManager(
                granted = permissionGranted,
                requestNeeded = requestNeeded,
            ),
        bondedFailure: BondedDevicesError? = null,
        shouldShowRationale: (String) -> Boolean = { false },
        printerService: PrinterService = FakePrinterService(),
    ): PrinterSettingsViewModel {
        val resolvedBonded =
            bondedProvider
                ?: object : BondedBluetoothDevicesProvider {
                    override fun listBondedDevices(): BondedDevicesResult =
                        when {
                            bondedFailure != null -> BondedDevicesResult.Failure(bondedFailure)
                            else -> BondedDevicesResult.Success(devices)
                        }
                }
        return PrinterSettingsViewModel(
            permissionManager = permissionManager,
            adapterStateProvider = adapterStateProvider,
            bondedDevicesProvider = resolvedBonded,
            printerSettingsRepository = repository,
            printerService = printerService,
        ).also { it.refresh(shouldShowRationale) }
    }
}

private class FakePrinterService(
    var result: PrintResult = PrintResult.Success,
    var throwOnTestPrint: Boolean = false,
    private val blockUntil: CompletableDeferred<Unit>? = null,
) : PrinterService {
    var testPrintCalls: Int = 0
        private set

    override suspend fun printDraft(orderId: String): PrintResult =
        error("printDraft must not be called by BT-007")

    override suspend fun printAccepted(orderId: String): PrintResult =
        error("printAccepted must not be called by BT-007")

    override suspend fun testPrint(): PrintResult {
        testPrintCalls += 1
        blockUntil?.await()
        if (throwOnTestPrint) error("unexpected testPrint failure")
        return result
    }
}

private class MutableAdapterStateProvider(
    var availability: BluetoothAdapterAvailability,
) : BluetoothAdapterStateProvider {
    override fun currentAvailability(): BluetoothAdapterAvailability = availability
}

private class MutableBondedProvider(
    var devices: List<BondedBluetoothDevice>,
) : BondedBluetoothDevicesProvider {
    override fun listBondedDevices(): BondedDevicesResult = BondedDevicesResult.Success(devices)
}

private class FakePermissionManager(
    var granted: Boolean,
    var requestNeeded: Boolean,
) : BluetoothPermissionManager {
    override fun requiredRuntimePermissions(): List<String> =
        if (granted) emptyList() else listOf("android.permission.BLUETOOTH_CONNECT")

    override fun missingRuntimePermissions(): List<String> = requiredRuntimePermissions()

    override fun areRequiredRuntimePermissionsGranted(): Boolean = granted

    override fun isRuntimePermissionRequestNeeded(): Boolean = !granted && requestNeeded
}

private class FakePrinterSettingsRepository(
    var selectedId: String? = null,
    var priceMode: PricePrintMode = PricePrintMode.DETAILED,
    var failSetSelected: Boolean = false,
    var failClearSelected: Boolean = false,
    var failUpdatePriceMode: Boolean = false,
    var failGetSelected: Boolean = false,
    var failGetPriceMode: Boolean = false,
) : PrinterSettingsRepository {
    private val selectedFlow = MutableStateFlow(selectedId)
    private val priceFlow = MutableStateFlow(priceMode)
    var mutationCount: Int = 0
        private set

    override suspend fun getPricePrintMode(): PricePrintMode {
        if (failGetPriceMode) error("get price failed")
        return priceMode
    }

    override fun observePricePrintMode(): Flow<PricePrintMode> = priceFlow

    override suspend fun updatePricePrintMode(mode: PricePrintMode) {
        if (failUpdatePriceMode) error("update price failed")
        mutationCount += 1
        priceMode = mode
        priceFlow.value = mode
    }

    override suspend fun getSelectedPrinterId(): String? {
        if (failGetSelected) error("get selected failed")
        return selectedId
    }

    override fun observeSelectedPrinterId(): Flow<String?> = selectedFlow.map { it }

    override suspend fun setSelectedPrinterId(id: String) {
        if (failSetSelected) error("set selected failed")
        mutationCount += 1
        selectedId = id
        selectedFlow.value = id
    }

    override suspend fun clearSelectedPrinterId() {
        if (failClearSelected) error("clear failed")
        mutationCount += 1
        selectedId = null
        selectedFlow.value = null
    }
}
