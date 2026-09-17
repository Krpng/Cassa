package it.krpng.cassa.feature.acceptance

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.usecase.AcceptOrder
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptancePreviewViewModelTest {
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
    fun `ACCEPT-T008 observe only — zero repository writes`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Ready)
        assertEquals(0, repository.writeCount)
        assertEquals(listOf("observeById"), repository.readOps)
    }

    @Test
    fun `ACCEPT-T009 preview does not call numbering APIs`() {
        val parameterTypes = AcceptancePreviewViewModel::class.java.constructors
            .single()
            .parameterTypes
            .map { it.simpleName }

        assertEquals(
            listOf("SavedStateHandle", "OrderRepository", "AcceptOrder", "PrinterService"),
            parameterTypes,
        )
    }

    @Test
    fun `ACCEPT-T012 preview total ignores stale orders totalCents`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1, unitCents = 700, qty = 2),
            ),
            staleTotalCents = 1L,
            generalNote = "senza cipolla",
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(
            OrderTotalResult.Success(Money.ofCents(1_400)),
            ready.orderTotal,
        )
        assertEquals("senza cipolla", ready.generalNote)
        assertTrue(ready.isAcceptEnabled)
    }

    @Test
    fun `ACCEPT-T010 and T011 sections keep category and sequence order`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(id = "b", category = ProductCategory.BIBITA, sequence = 1, name = "Acqua"),
                item(id = "p2", category = ProductCategory.PIZZA, sequence = 2, name = "AAA"),
                item(id = "p1", category = ProductCategory.PIZZA, sequence = 1, name = "ZZZ"),
                item(id = "f", category = ProductCategory.FRITTURA, sequence = 1, name = "Crochette"),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(listOf("PIZZE", "FRITTURA", "BIBITE"), ready.sections.map { it.title })
        assertEquals(listOf("p1", "p2"), ready.sections[0].lines.map { it.itemId })
        assertEquals("ZZZ", ready.sections[0].lines[0].productName)
    }

    @Test
    fun `snapshots additions removals and notes are shown`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(
                    id = "custom",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    name = "Margherita",
                    printed = "MARGHERITA",
                    note = "ben cotta",
                    additions = listOf(
                        OrderItemAddition(
                            id = "a1",
                            additionId = 1L,
                            nameSnapshot = "Funghi",
                            printedNameSnapshot = "FUNGHI",
                            listedPrice = Money.ofCents(100),
                            chargedPrice = Money.ofCents(100),
                            displayOrder = 0,
                        ),
                    ),
                    removals = listOf(
                        OrderItemRemoval(
                            id = "r1",
                            ingredientId = 2L,
                            nameSnapshot = "Basilico",
                            displayOrder = 0,
                        ),
                    ),
                    unitCents = 800,
                ),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val line = (viewModel.uiState.value as AcceptancePreviewUiState.Ready)
            .sections.single().lines.single()
        assertEquals("Margherita", line.productName)
        assertEquals("MARGHERITA", line.productPrintedName)
        assertEquals(listOf("Funghi"), line.additionNames)
        assertEquals(listOf("Basilico"), line.removalNames)
        assertEquals("ben cotta", line.note)
        assertEquals(Money.ofCents(800), line.finalUnitPrice)
        assertEquals(Money.ofCents(800), line.lineTotal)
    }

    @Test
    fun `overflow keeps accept disabled`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(
                    id = "overflow",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    unitCents = Long.MAX_VALUE / 2,
                    qty = 3,
                ),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(OrderTotalResult.AmountOverflow, ready.orderTotal)
        assertTrue(ready.hasTotalOverflow)
        assertFalse(ready.isAcceptEnabled)
    }

    @Test
    fun `empty draft maps to EmptyDraft state`() = runTest(mainDispatcher) {
        val viewModel = viewModel(FakeOrderRepository(draft(items = emptyList())))
        advanceUntilIdle()
        assertEquals(AcceptancePreviewUiState.EmptyDraft, viewModel.uiState.value)
    }

    @Test
    fun `ACCEPT-003 ACCETTA enabled on valid ready preview`() = runTest(mainDispatcher) {
        val viewModel = viewModel(FakeOrderRepository(draft()))
        advanceUntilIdle()
        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertTrue(ready.isAcceptEnabled)
        assertFalse(ready.isAccepting)
    }

    @Test
    fun `ACCEPT-T006 accept success stays on Accepted from Room`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(draft()).apply {
            acceptResult = AcceptOrderResult.Accepted(
                orderId = DRAFT_ID,
                displayNumber = "001",
                total = Money.ofCents(700),
                acceptedAt = NOW,
                businessDate = java.time.LocalDate.parse("2026-09-14"),
                numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
                numberingCycle = null,
            )
            emitAcceptedOnAccept = true
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        val accepted = viewModel.uiState.value as AcceptancePreviewUiState.Accepted
        assertEquals("001", accepted.displayNumber)
        assertEquals(Money.ofCents(700), accepted.total)
        assertEquals(DRAFT_ID, accepted.orderId)
        assertEquals(1, repository.acceptCalls)
        assertTrue(accepted.sections.isNotEmpty())
    }

    @Test
    fun `ACCEPT-T019 home and new order navigation leave accepted unchanged`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                acceptedOrder(displayNumber = "042", totalCents = 1_400),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val before = viewModel.uiState.value as AcceptancePreviewUiState.Accepted
            assertEquals("042", before.displayNumber)

            val events = mutableListOf<AcceptanceNavigationEvent>()
            val collectJob = launch {
                viewModel.navigationEvents.collect { events += it }
            }

            viewModel.goHome()
            advanceUntilIdle()
            assertEquals(listOf(AcceptanceNavigationEvent.GoHome), events)

            viewModel.startNewOrder()
            advanceUntilIdle()
            assertEquals(1, repository.createDraftCalls)
            assertTrue(events.last() is AcceptanceNavigationEvent.OpenNewOrder)
            val open = events.last() as AcceptanceNavigationEvent.OpenNewOrder
            assertEquals("new-draft", open.draftId)
            assertTrue(open.draftId != DRAFT_ID)

            val after = viewModel.uiState.value as AcceptancePreviewUiState.Accepted
            assertEquals(before.displayNumber, after.displayNumber)
            assertEquals(before.total, after.total)
            assertEquals(before.orderId, after.orderId)
            assertEquals(0, repository.acceptCalls)
            collectJob.cancel()
        }

    @Test
    fun `ACCEPT-003 accept success maps to Accepted state`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(draft()).apply {
            acceptResult = AcceptOrderResult.Accepted(
                orderId = DRAFT_ID,
                displayNumber = "001",
                total = Money.ofCents(700),
                acceptedAt = NOW,
                businessDate = java.time.LocalDate.parse("2026-09-14"),
                numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
                numberingCycle = null,
            )
            emitAcceptedOnAccept = true
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        val accepted = viewModel.uiState.value as AcceptancePreviewUiState.Accepted
        assertEquals("001", accepted.displayNumber)
        assertEquals(1, repository.acceptCalls)
    }

    @Test
    fun `ACCEPT-003 duplicate accept while in progress is ignored`() = runTest(mainDispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = FakeOrderRepository(draft()).apply {
            acceptBlock = { gate.await() }
            acceptResult = AcceptOrderResult.Accepted(
                orderId = DRAFT_ID,
                displayNumber = "002",
                total = Money.ofCents(700),
                acceptedAt = NOW,
                businessDate = java.time.LocalDate.parse("2026-09-14"),
                numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
                numberingCycle = null,
            )
            emitAcceptedOnAccept = true
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as AcceptancePreviewUiState.Ready).isAccepting)
        viewModel.accept()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.acceptCalls)
        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Accepted)
    }

    // --- PRINT-020 draft print ---

    @Test
    fun `PRINT020 A non-Ready does not call printDraft`() = runTest(mainDispatcher) {
        val printer = FakePrinterService()
        val viewModel =
            viewModel(
                FakeOrderRepository(null),
                printerService = printer,
            )
        advanceUntilIdle()
        assertEquals(AcceptancePreviewUiState.NotFound, viewModel.uiState.value)

        viewModel.runDraftPrint()
        advanceUntilIdle()

        assertEquals(0, printer.printDraftCalls)
        assertEquals(DraftPrintUiState.Idle, viewModel.draftPrintUiState.value)
    }

    @Test
    fun `PRINT020 B EmptyDraft does not call printDraft`() = runTest(mainDispatcher) {
        val printer = FakePrinterService()
        val viewModel =
            viewModel(
                FakeOrderRepository(draft(items = emptyList())),
                printerService = printer,
            )
        advanceUntilIdle()
        assertEquals(AcceptancePreviewUiState.EmptyDraft, viewModel.uiState.value)

        viewModel.runDraftPrint()
        advanceUntilIdle()

        assertEquals(0, printer.printDraftCalls)
        assertEquals(DraftPrintUiState.Idle, viewModel.draftPrintUiState.value)
    }

    @Test
    fun `PRINT020 C valid Ready calls printDraft once with draftId`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printDraftCalls)
        assertEquals(listOf(DRAFT_ID), printer.printDraftIds)
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)
        assertEquals(
            "Bozza inviata alla stampante",
            (viewModel.draftPrintUiState.value as DraftPrintUiState.Success).message,
        )
    }

    @Test
    fun `PRINT020 D double tap while printing calls printDraft once`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Success,
                blockUntil = gate,
            )
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(DraftPrintUiState.Printing, viewModel.draftPrintUiState.value)

        viewModel.runDraftPrint()
        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(1, printer.printDraftCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, printer.printDraftCalls)
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)
    }

    @Test
    fun `PRINT020 E success clears printing and allows new tap`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)

        viewModel.consumeDraftPrintFeedback()
        assertEquals(DraftPrintUiState.Idle, viewModel.draftPrintUiState.value)

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(2, printer.printDraftCalls)
    }

    @Test
    fun `PRINT020 F PermissionDenied maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.PermissionDenied,
            "Autorizzazione Bluetooth necessaria",
        )
    }

    @Test
    fun `PRINT020 G BluetoothDisabled maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.BluetoothDisabled,
            "Bluetooth disattivato",
        )
    }

    @Test
    fun `PRINT020 H PrinterNotConfigured maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.PrinterNotConfigured,
            "Nessuna stampante selezionata",
        )
    }

    @Test
    fun `PRINT020 I ConnectionFailed maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.ConnectionFailed,
            "Impossibile connettersi alla stampante",
        )
    }

    @Test
    fun `PRINT020 J ConnectionLost maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.ConnectionLost,
            "Connessione interrotta durante la stampa",
        )
    }

    @Test
    fun `PRINT020 K Timeout maps expected feedback`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.Timeout,
            "Timeout di connessione alla stampante",
        )
    }

    @Test
    fun `PRINT020 L PrintFailed and Unknown map generic failure`() = runTest(mainDispatcher) {
        assertMappedDraftPrintError(
            PrinterError.PrintFailed,
            "Impossibile stampare la bozza",
        )
        assertMappedDraftPrintError(
            PrinterError.Unknown,
            "Impossibile stampare la bozza",
        )
    }

    @Test
    fun `PRINT020 M unexpected exception is recoverable`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(throwOnPrintDraft = true)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()

        val error = viewModel.draftPrintUiState.value as DraftPrintUiState.Error
        assertEquals("Impossibile stampare la bozza", error.message)

        viewModel.consumeDraftPrintFeedback()
        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(2, printer.printDraftCalls)
    }

    @Test
    fun `PRINT020 N manual retry after failure starts new job`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.ConnectionFailed))
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Error)

        printer.result = PrintResult.Success
        viewModel.runDraftPrint()
        advanceUntilIdle()

        assertEquals(2, printer.printDraftCalls)
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)
    }

    @Test
    fun `PRINT020 O while printing accept is ignored`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Success,
                blockUntil = gate,
            )
        val repository = FakeOrderRepository(draft()).apply {
            acceptResult = AcceptOrderResult.Accepted(
                orderId = DRAFT_ID,
                displayNumber = "001",
                total = Money.ofCents(700),
                acceptedAt = NOW,
                businessDate = LocalDate.parse("2026-09-14"),
                numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
                numberingCycle = null,
            )
            emitAcceptedOnAccept = true
        }
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(DraftPrintUiState.Printing, viewModel.draftPrintUiState.value)

        viewModel.accept()
        advanceUntilIdle()
        assertEquals(0, repository.acceptCalls)
        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Ready)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, repository.acceptCalls)
    }

    @Test
    fun `PRINT020 P while accepting print is ignored`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer = FakePrinterService(result = PrintResult.Success)
        val repository = FakeOrderRepository(draft()).apply {
            acceptBlock = { gate.await() }
            acceptResult = AcceptOrderResult.Accepted(
                orderId = DRAFT_ID,
                displayNumber = "002",
                total = Money.ofCents(700),
                acceptedAt = NOW,
                businessDate = LocalDate.parse("2026-09-14"),
                numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
                numberingCycle = null,
            )
            emitAcceptedOnAccept = true
        }
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as AcceptancePreviewUiState.Ready).isAccepting)

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(0, printer.printDraftCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, printer.printDraftCalls)
        assertEquals(1, repository.acceptCalls)
    }

    @Test
    fun `PRINT020 Q R S T print does not mutate draft business state`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()

        val before = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        val writeBefore = repository.writeCount
        val acceptBefore = repository.acceptCalls

        viewModel.runDraftPrint()
        advanceUntilIdle()

        val after = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(before.draftId, after.draftId)
        assertEquals(before.sections, after.sections)
        assertEquals(before.generalNote, after.generalNote)
        assertEquals(before.orderTotal, after.orderTotal)
        assertEquals(writeBefore, repository.writeCount)
        assertEquals(acceptBefore, repository.acceptCalls)
        assertNull(repository.orderSnapshot()?.displayNumber)
        assertEquals(OrderStatus.DRAFT, repository.orderSnapshot()?.status)
        assertEquals(1, printer.printDraftCalls)
    }

    @Test
    fun `PRINT020 U print completion does not overwrite newer Ready`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Success,
                blockUntil = gate,
            )
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(DraftPrintUiState.Printing, viewModel.draftPrintUiState.value)

        repository.emitOrder(
            draft(
                items = listOf(
                    item(id = "1", category = ProductCategory.PIZZA, sequence = 1),
                    item(id = "2", category = ProductCategory.BIBITA, sequence = 1, name = "Acqua"),
                ),
                generalNote = "updated while printing",
            ),
        )
        advanceUntilIdle()

        val mid = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals("updated while printing", mid.generalNote)
        assertEquals(2, mid.sections.sumOf { it.lines.size })

        gate.complete(Unit)
        advanceUntilIdle()

        val after = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals("updated while printing", after.generalNote)
        assertEquals(2, after.sections.sumOf { it.lines.size })
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)
    }

    @Test
    fun `PRINT020 V retry observe does not auto print`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()
        assertEquals(0, printer.printDraftCalls)

        viewModel.retry()
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(0, printer.printDraftCalls)
        assertEquals(DraftPrintUiState.Idle, viewModel.draftPrintUiState.value)
    }

    @Test
    fun `PRINT020 W consume idle does not clear Printing`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Success,
                blockUntil = gate,
            )
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(DraftPrintUiState.Printing, viewModel.draftPrintUiState.value)

        viewModel.consumeDraftPrintFeedback()
        assertEquals(DraftPrintUiState.Printing, viewModel.draftPrintUiState.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.draftPrintUiState.value is DraftPrintUiState.Success)

        // Simulate newer failure after consume of prior success.
        viewModel.consumeDraftPrintFeedback()
        printer.result = PrintResult.Failure(PrinterError.Timeout)
        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(
            "Timeout di connessione alla stampante",
            (viewModel.draftPrintUiState.value as DraftPrintUiState.Error).message,
        )
        // Old success consume must not wipe newer error (consume only clears Success/Error once).
        viewModel.consumeDraftPrintFeedback()
        assertEquals(DraftPrintUiState.Idle, viewModel.draftPrintUiState.value)
    }

    // --- PRINT-021 / D-068 accepted print ---

    @Test
    fun `PRINT021 J DRAFT still calls printDraft not printAccepted`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printDraftCalls)
        assertEquals(0, printer.printAcceptedCalls)
        assertEquals(listOf(DRAFT_ID), printer.printDraftIds)
    }

    @Test
    fun `PRINT021 K ACCEPTED print calls printAccepted with exact id`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val repository = FakeOrderRepository(acceptedOrder(displayNumber = "042", totalCents = 1_400))
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Accepted)

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(0, printer.printDraftCalls)
        assertEquals(listOf(DRAFT_ID), printer.printAcceptedIds)
        val success = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Success
        assertEquals("Ordine inviato alla stampante", success.message)
    }

    @Test
    fun `PRINT021 L M accepted double tap while printing calls once`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Success,
                acceptedBlockUntil = gate,
            )
        val viewModel =
            viewModel(
                FakeOrderRepository(acceptedOrder(displayNumber = "001", totalCents = 700)),
                printerService = printer,
            )
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(AcceptedPrintUiState.Printing, viewModel.acceptedPrintUiState.value)

        viewModel.runAcceptedPrint()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)
        assertTrue(viewModel.acceptedPrintUiState.value is AcceptedPrintUiState.Success)
    }

    @Test
    fun `PRINT021 O maps PrinterNotConfigured for accepted`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.PrinterNotConfigured))
        val viewModel =
            viewModel(
                FakeOrderRepository(acceptedOrder(displayNumber = "001", totalCents = 700)),
                printerService = printer,
            )
        advanceUntilIdle()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        val error = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Error
        assertEquals("Nessuna stampante selezionata", error.message)
    }

    @Test
    fun `PRINT021 P accepted CancellationException restores Idle`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(throwAcceptedCancellation = true)
        val viewModel =
            viewModel(
                FakeOrderRepository(acceptedOrder(displayNumber = "001", totalCents = 700)),
                printerService = printer,
            )
        advanceUntilIdle()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)
    }

    @Test
    fun `PRINT021 Q R accepted print does not accept or allocate number`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val repository =
            FakeOrderRepository(acceptedOrder(displayNumber = "042", totalCents = 1_400))
        val viewModel = viewModel(repository, printerService = printer)
        advanceUntilIdle()
        val before = viewModel.uiState.value as AcceptancePreviewUiState.Accepted

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(0, repository.acceptCalls)
        assertEquals(0, repository.writeCount)
        val after = viewModel.uiState.value as AcceptancePreviewUiState.Accepted
        assertEquals(before.displayNumber, after.displayNumber)
        assertEquals(before.orderId, after.orderId)
        assertEquals(before.total, after.total)
    }

    @Test
    fun `PRINT021 S Ready runAcceptedPrint is no-op`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Ready)

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(0, printer.printAcceptedCalls)
        assertEquals(0, printer.printDraftCalls)
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)
    }

    @Test
    fun `PRINT021 Accepted runDraftPrint is no-op`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel =
            viewModel(
                FakeOrderRepository(acceptedOrder(displayNumber = "001", totalCents = 700)),
                printerService = printer,
            )
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()
        assertEquals(0, printer.printDraftCalls)
        assertEquals(0, printer.printAcceptedCalls)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertMappedDraftPrintError(
        error: PrinterError,
        expectedMessage: String,
    ) {
        val printer = FakePrinterService(result = PrintResult.Failure(error))
        val viewModel = viewModel(FakeOrderRepository(draft()), printerService = printer)
        advanceUntilIdle()

        viewModel.runDraftPrint()
        advanceUntilIdle()

        val state = viewModel.draftPrintUiState.value as DraftPrintUiState.Error
        assertEquals(expectedMessage, state.message)
        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Ready)
    }

    private fun viewModel(
        repository: FakeOrderRepository,
        printerService: PrinterService = FakePrinterService(),
    ): AcceptancePreviewViewModel =
        AcceptancePreviewViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(AcceptancePreviewViewModel.DRAFT_ID_ARGUMENT to DRAFT_ID),
            ),
            orderRepository = repository,
            acceptOrder = AcceptOrder(repository),
            printerService = printerService,
        )

    private class FakePrinterService(
        var result: PrintResult = PrintResult.Success,
        var throwOnPrintDraft: Boolean = false,
        var throwOnPrintAccepted: Boolean = false,
        var throwAcceptedCancellation: Boolean = false,
        private val blockUntil: CompletableDeferred<Unit>? = null,
        private val acceptedBlockUntil: CompletableDeferred<Unit>? = null,
    ) : PrinterService {
        var printDraftCalls: Int = 0
            private set
        var printAcceptedCalls: Int = 0
            private set
        val printDraftIds = mutableListOf<String>()
        val printAcceptedIds = mutableListOf<String>()

        override suspend fun printDraft(orderId: String): PrintResult {
            printDraftCalls += 1
            printDraftIds += orderId
            blockUntil?.await()
            if (throwOnPrintDraft) error("unexpected printDraft failure")
            return result
        }

        override suspend fun printAccepted(orderId: String): PrintResult {
            printAcceptedCalls += 1
            printAcceptedIds += orderId
            acceptedBlockUntil?.await()
            if (throwAcceptedCancellation) throw CancellationException("test cancel")
            if (throwOnPrintAccepted) error("unexpected printAccepted failure")
            return result
        }

        override suspend fun testPrint(): PrintResult =
            error("testPrint must not be called by PRINT-020/021")
    }

    private class FakeOrderRepository(
        initial: Order?,
    ) : OrderRepository {
        private val order = MutableStateFlow(initial)
        private var activeDraftId: String? =
            initial?.takeIf { it.status == OrderStatus.DRAFT }?.id
        var writeCount: Int = 0
            private set
        val readOps = mutableListOf<String>()
        var acceptCalls: Int = 0
            private set
        var createDraftCalls: Int = 0
            private set
        var acceptResult: AcceptOrderResult = AcceptOrderResult.PersistenceFailure
        var acceptBlock: (suspend () -> Unit)? = null
        var emitAcceptedOnAccept: Boolean = false

        fun orderSnapshot(): Order? = order.value

        fun emitOrder(value: Order?) {
            order.value = value
            activeDraftId = value?.takeIf { it.status == OrderStatus.DRAFT }?.id
        }

        private fun write(): Nothing {
            writeCount += 1
            error("ACCEPT-001 preview must not write")
        }

        override suspend fun getById(orderId: String): Order? {
            readOps += "getById"
            return order.value?.takeIf { it.id == orderId }
        }

        override fun observeById(orderId: String): Flow<Order?> {
            readOps += "observeById"
            return order
        }

        override fun observeActiveDraft(): Flow<Order?> = write()

        override suspend fun getActiveDraft(): Order? {
            val id = activeDraftId ?: return null
            return order.value?.takeIf { it.id == id && it.status == OrderStatus.DRAFT }
        }

        override suspend fun createDraft(): CreateDraftResult {
            createDraftCalls += 1
            if (activeDraftId != null) return CreateDraftResult.AlreadyExists
            val created = draft(id = "new-draft", items = emptyList())
            activeDraftId = created.id
            return CreateDraftResult.Created(created)
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult = write()

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult = write()

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult = write()

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult = write()

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): SplitStandardPizzaItemResult = write()

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): ChangeQuantityResult = write()

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): RemoveOrderItemResult = write()

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult = write()

        override suspend fun acceptOrder(orderId: String): AcceptOrderResult {
            acceptCalls += 1
            acceptBlock?.invoke()
            val result = acceptResult
            if (emitAcceptedOnAccept && result is AcceptOrderResult.Accepted) {
                val current = order.value ?: return result
                activeDraftId = null
                order.value = current.copy(
                    status = OrderStatus.ACCEPTED,
                    displayNumber = result.displayNumber,
                    numberingMode = result.numberingMode,
                    numberingCycle = result.numberingCycle,
                    businessDate = result.businessDate,
                    acceptedAt = result.acceptedAt,
                    total = result.total,
                )
            }
            return result
        }

        override fun observeAcceptedByBusinessDate(
            businessDate: java.time.LocalDate,
        ): Flow<List<it.krpng.cassa.domain.model.AcceptedOrderSummary>> = write()

        override suspend fun purgeAcceptedBefore(
            currentBusinessDate: java.time.LocalDate,
        ): it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult = write()

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: java.time.LocalDate,
        ): it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult = write()

        override suspend fun replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId: String,
            currentBusinessDate: java.time.LocalDate,
            expectedDraftId: String,
        ): it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult = write()
    }

    private companion object {
        const val DRAFT_ID = "draft-1"
        val NOW: Instant = Instant.parse("2026-09-14T18:00:00Z")

        fun draft(
            id: String = DRAFT_ID,
            items: List<OrderItem> = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1),
            ),
            staleTotalCents: Long = 999_999L,
            generalNote: String? = null,
        ): Order = Order(
            id = id,
            status = OrderStatus.DRAFT,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = null,
            total = Money.ofCents(staleTotalCents),
            generalNote = generalNote,
            sourceOrderId = null,
            items = items,
        )

        fun acceptedOrder(
            displayNumber: String,
            totalCents: Long,
        ): Order = Order(
            id = DRAFT_ID,
            status = OrderStatus.ACCEPTED,
            displayNumber = displayNumber,
            numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
            numberingCycle = null,
            businessDate = LocalDate.parse("2026-09-14"),
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = NOW,
            total = Money.ofCents(totalCents),
            generalNote = null,
            sourceOrderId = null,
            items = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1, unitCents = 700, qty = 2),
            ),
        )

        fun item(
            id: String,
            category: ProductCategory,
            sequence: Int,
            name: String = "Prodotto $id",
            printed: String = name.uppercase(),
            note: String? = null,
            additions: List<OrderItemAddition> = emptyList(),
            removals: List<OrderItemRemoval> = emptyList(),
            unitCents: Long = 700,
            qty: Int = 1,
        ): OrderItem = OrderItem(
            id = id,
            productId = null,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printed,
            categorySnapshot = category,
            quantity = qty,
            baseUnitPrice = Money.ofCents(unitCents.coerceAtLeast(0)),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = null,
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = true,
            note = note,
            createdSequence = sequence,
            additions = additions,
            removals = removals,
        )
    }
}
