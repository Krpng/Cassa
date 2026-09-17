package it.krpng.cassa.feature.accepteddetail

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.printer.PrintResult
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterService
import it.krpng.cassa.domain.usecase.DuplicateAcceptedOrder
import it.krpng.cassa.domain.usecase.GetCurrentDayAcceptedOrder
import it.krpng.cassa.domain.usecase.ReplaceDraftWithAcceptedOrderDuplicate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptedOrderDetailViewModelTest {
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
    fun `ARCH-T011 current-day accepted is visible`() = runTest(mainDispatcher) {
        val order = acceptedOrder()
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals("042", content.displayNumber)
        assertEquals(Money.ofCents(1_400).formatEur(), content.totalLabel)
        assertEquals("15/09/2026 12:30", content.acceptedAtLabel)
    }

    @Test
    fun `ARCH-T012 old accepted is unavailable`() = runTest(mainDispatcher) {
        val order = acceptedOrder(businessDate = LocalDate.parse("2026-09-14"))
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()
        assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `ARCH-T013 draft is unavailable`() = runTest(mainDispatcher) {
        val draft = acceptedOrder().copy(
            status = OrderStatus.DRAFT,
            displayNumber = null,
            acceptedAt = null,
            businessDate = null,
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(draft))
        advanceUntilIdle()
        assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `ARCH-T014 missing and purged mid-open become unavailable without stale content`() =
        runTest(mainDispatcher) {
            val order = acceptedOrder()
            val flow = MutableStateFlow<Order?>(order)
            val viewModel = viewModel(orderFlow = flow)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Content)

            flow.value = null
            advanceUntilIdle()
            assertEquals(AcceptedOrderDetailUiState.Unavailable, viewModel.uiState.value)
        }

    @Test
    fun `ARCH-T015 snapshot names and prices ignore catalog changes`() = runTest(mainDispatcher) {
        val order = acceptedOrder(
            items = listOf(
                item(
                    id = "p1",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    name = "Margherita Snapshot",
                    printed = "MARGHERITA SNAP",
                    unitCents = 700,
                ),
            ),
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals("Margherita Snapshot", line.productName)
        assertEquals("MARGHERITA SNAP", line.productPrintedName)
        assertEquals(Money.ofCents(700), line.finalUnitPrice)
    }

    @Test
    fun `ARCH-T016 generalNote shown when present`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(acceptedOrder(generalNote = "Consegna alle 21")),
        )
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals("Consegna alle 21", content.generalNote)
    }

    @Test
    fun `ARCH-T017 item note shown when present`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(
                acceptedOrder(
                    items = listOf(
                        item(
                            id = "p1",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            note = "Ben cotta",
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals("Ben cotta", line.note)
    }

    @Test
    fun `ARCH-T018 additions and removals use accepted snapshots by displayOrder`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                orderFlow = MutableStateFlow(
                    acceptedOrder(
                        items = listOf(
                            item(
                                id = "p1",
                                category = ProductCategory.PIZZA,
                                sequence = 1,
                                additions = listOf(
                                    OrderItemAddition(
                                        id = "a2",
                                        additionId = 2,
                                        nameSnapshot = "Seconda",
                                        printedNameSnapshot = "SECONDA",
                                        listedPrice = Money.ofCents(50),
                                        chargedPrice = Money.ofCents(50),
                                        displayOrder = 1,
                                    ),
                                    OrderItemAddition(
                                        id = "a1",
                                        additionId = 1,
                                        nameSnapshot = "Prima",
                                        printedNameSnapshot = "PRIMA",
                                        listedPrice = Money.ofCents(100),
                                        chargedPrice = Money.ofCents(100),
                                        displayOrder = 0,
                                    ),
                                ),
                                removals = listOf(
                                    OrderItemRemoval(
                                        id = "r2",
                                        ingredientId = 2,
                                        nameSnapshot = "Secondo",
                                        displayOrder = 1,
                                    ),
                                    OrderItemRemoval(
                                        id = "r1",
                                        ingredientId = 1,
                                        nameSnapshot = "Primo",
                                        displayOrder = 0,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
                .sections.single().lines.single()
            assertEquals(listOf("Prima", "Seconda"), line.additionNames)
            assertEquals(listOf("Primo", "Secondo"), line.removalNames)
        }

    @Test
    fun `ARCH-T019 quantity finalUnitPrice and line total from snapshot`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(
                acceptedOrder(
                    items = listOf(
                        item(
                            id = "p1",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            qty = 2,
                            unitCents = 700,
                            manualCents = 650,
                        ),
                    ),
                    totalCents = 1_400,
                ),
            ),
        )
        advanceUntilIdle()
        val line = (viewModel.uiState.value as AcceptedOrderDetailUiState.Content)
            .sections.single().lines.single()
        assertEquals(2, line.quantity)
        assertEquals(Money.ofCents(700), line.finalUnitPrice)
        assertEquals(Money.ofCents(1_400), line.lineTotal)
        assertEquals(Money.ofCents(650), line.manualUnitPrice)
    }

    @Test
    fun `ARCH-T020 category and createdSequence ordering`() = runTest(mainDispatcher) {
        val order = acceptedOrder(
            items = listOf(
                item(id = "b2", category = ProductCategory.BIBITA, sequence = 2, name = "Acqua"),
                item(id = "p2", category = ProductCategory.PIZZA, sequence = 2, name = "Diavola"),
                item(id = "f1", category = ProductCategory.FRITTURA, sequence = 1, name = "Crocche"),
                item(id = "p1", category = ProductCategory.PIZZA, sequence = 1, name = "Margherita"),
                item(id = "b1", category = ProductCategory.BIBITA, sequence = 1, name = "Coca"),
            ),
        )
        val viewModel = viewModel(orderFlow = MutableStateFlow(order))
        advanceUntilIdle()

        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals(listOf("PIZZE", "FRITTURA", "BIBITE"), content.sections.map { it.title })
        assertEquals(listOf("Margherita", "Diavola"), content.sections[0].lines.map { it.productName })
        assertEquals(listOf("Crocche"), content.sections[1].lines.map { it.productName })
        assertEquals(listOf("Coca", "Acqua"), content.sections[2].lines.map { it.productName })
    }

    @Test
    fun `settings failure maps to error not unavailable`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            orderFlow = MutableStateFlow(acceptedOrder()),
            settingsFail = true,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Error)
    }

    @Test
    fun `ARCH-T032 duplicate success emits OpenNewOrder navigation`() = runTest(mainDispatcher) {
        val order = acceptedOrder()
        val repository = FakeOrderRepository(MutableStateFlow(order)).apply {
            duplicateResult = DuplicateAcceptedOrderResult.Created("new-draft-id")
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        val events = mutableListOf<AcceptedOrderDetailNavigationEvent>()
        val collectJob = launch {
            viewModel.navigationEvents.collect { events += it }
        }
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        assertEquals(
            listOf(AcceptedOrderDetailNavigationEvent.OpenNewOrder("new-draft-id")),
            events,
        )
        collectJob.cancel()
    }

    @Test
    fun `ARCH7-T001 duplicate conflict opens ActiveDraftConflict`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals("existing-draft", content.activeDraftConflict?.expectedDraftId)
        assertNull(content.duplicateError)
    }

    @Test
    fun `ARCH7-T002 cancel conflict clears dialog with zero writes`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictCancel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertNull(content.activeDraftConflict)
        assertEquals(0, repository.replaceCalls)
        assertEquals(1, repository.duplicateCalls)
    }

    @Test
    fun `ARCH7-T003 resume opens existing draft with zero writes`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft", withItem = true)
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        val events = mutableListOf<AcceptedOrderDetailNavigationEvent>()
        val collectJob = launch { viewModel.navigationEvents.collect { events += it } }
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictResume()
        advanceUntilIdle()
        assertEquals(
            listOf(AcceptedOrderDetailNavigationEvent.OpenNewOrder("existing-draft")),
            events,
        )
        assertEquals(0, repository.replaceCalls)
        collectJob.cancel()
    }

    @Test
    fun `ARCH7-T004 resume works with empty persisted draft`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("empty-draft")
            activeDraft = draftOrder(id = "empty-draft", withItem = false)
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        val events = mutableListOf<AcceptedOrderDetailNavigationEvent>()
        val collectJob = launch { viewModel.navigationEvents.collect { events += it } }
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictResume()
        advanceUntilIdle()
        assertEquals(
            listOf(AcceptedOrderDetailNavigationEvent.OpenNewOrder("empty-draft")),
            events,
        )
        collectJob.cancel()
    }

    @Test
    fun `ARCH7-T014 replace success navigates to new draft`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
            replaceResult = ReplaceDraftWithAcceptedOrderDuplicateResult.Created("replaced-draft")
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        val events = mutableListOf<AcceptedOrderDetailNavigationEvent>()
        val collectJob = launch { viewModel.navigationEvents.collect { events += it } }
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictReplace()
        advanceUntilIdle()
        assertEquals(
            listOf(AcceptedOrderDetailNavigationEvent.OpenNewOrder("replaced-draft")),
            events,
        )
        assertEquals(1, repository.replaceCalls)
        assertEquals("existing-draft", repository.lastExpectedDraftId)
        collectJob.cancel()
    }

    @Test
    fun draftMissingOnReplaceShowsErrorWithoutNavigation() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
            replaceResult = ReplaceDraftWithAcceptedOrderDuplicateResult.DraftMissing
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        val events = mutableListOf<AcceptedOrderDetailNavigationEvent>()
        val collectJob = launch { viewModel.navigationEvents.collect { events += it } }
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictReplace()
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertNull(content.activeDraftConflict)
        assertTrue(content.duplicateError!!.contains("non è più disponibile"))
        assertTrue(events.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun draftChangedOnReplaceShowsErrorWithoutNavigation() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
            replaceResult = ReplaceDraftWithAcceptedOrderDuplicateResult.DraftChanged
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        viewModel.onConflictReplace()
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertNull(content.activeDraftConflict)
        assertTrue(content.duplicateError!!.contains("cambiato"))
    }

    @Test
    fun resumeDraftMissingShowsError() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
            activeDraft = draftOrder(id = "existing-draft")
        }
        val viewModel = detailViewModel(repository)
        advanceUntilIdle()
        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        repository.activeDraft = null
        viewModel.onConflictResume()
        advanceUntilIdle()
        val content = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertNull(content.activeDraftConflict)
        assertTrue(content.duplicateError!!.contains("non è più disponibile"))
    }

    // --- PRINT-021 / D-068 ---

    @Test
    fun `PRINT021 A valid Content calls printAccepted once with exact id`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID), printer.printAcceptedIds)
        assertEquals(0, printer.printDraftCalls)
        val success = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Success
        assertEquals("Ordine inviato alla stampante", success.message)
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Content)
    }

    @Test
    fun `PRINT021 B C double tap while printing calls service once`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer = FakePrinterService(result = PrintResult.Success, blockUntil = gate)
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
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
    fun `PRINT021 D unavailable does not call printAccepted`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val viewModel = viewModel(orderFlow = MutableStateFlow(null), printer = printer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Unavailable)

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(0, printer.printAcceptedCalls)
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)
    }

    @Test
    fun `PRINT021 F maps PrinterNotConfigured`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.PrinterNotConfigured,
            "Nessuna stampante selezionata",
        )
    }

    @Test
    fun `PRINT021 F maps PermissionDenied`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.PermissionDenied,
            "Autorizzazione Bluetooth necessaria",
        )
    }

    @Test
    fun `PRINT021 F maps BluetoothDisabled`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.BluetoothDisabled,
            "Bluetooth disattivato",
        )
    }

    @Test
    fun `PRINT021 F maps ConnectionFailed`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.ConnectionFailed,
            "Impossibile connettersi alla stampante",
        )
    }

    @Test
    fun `PRINT021 F maps ConnectionLost`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.ConnectionLost,
            "Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.",
        )
    }

    @Test
    fun `PRINT021 F maps Timeout`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.Timeout,
            "Timeout di connessione alla stampante",
        )
    }

    @Test
    fun `PRINT021 G unexpected failure maps generic message`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(throwOnPrintAccepted = true)
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        val error = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Error
        assertEquals("Impossibile stampare l'ordine", error.message)
    }

    @Test
    fun `PRINT021 H CancellationException restores Idle`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(throwCancellation = true)
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Content)
    }

    @Test
    fun `PRINT021 I content unchanged after successful print`() = runTest(mainDispatcher) {
        val printer = FakePrinterService(result = PrintResult.Success)
        val order = acceptedOrder()
        val viewModel = viewModel(orderFlow = MutableStateFlow(order), printer = printer)
        advanceUntilIdle()
        val before = viewModel.uiState.value as AcceptedOrderDetailUiState.Content

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        val after = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals(before.orderId, after.orderId)
        assertEquals(before.displayNumber, after.displayNumber)
        assertEquals(before.total, after.total)
        assertEquals(0, printer.printDraftCalls)
    }

    @Test
    fun `PRINT021 while duplicating print is ignored`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer = FakePrinterService(result = PrintResult.Success)
        val repository = FakeOrderRepository(MutableStateFlow(acceptedOrder())).apply {
            duplicateGate = gate
            duplicateResult = DuplicateAcceptedOrderResult.DraftConflict("existing-draft")
        }
        val viewModel = detailViewModel(repository, printer = printer)
        advanceUntilIdle()

        viewModel.onDuplicateOrder()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as AcceptedOrderDetailUiState.Content).isDuplicating)

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(0, printer.printAcceptedCalls)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    // --- PRINT-023 / D-070 targeted retry ---

    @Test
    fun `PRINT023 fail then explicit STAMPA retries same orderId`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.BluetoothDisabled))
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()
        val before = viewModel.uiState.value as AcceptedOrderDetailUiState.Content

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID), printer.printAcceptedIds)
        assertEquals(
            "Bluetooth disattivato",
            (viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Error).message,
        )
        assertEquals(before.orderId, (viewModel.uiState.value as AcceptedOrderDetailUiState.Content).orderId)

        viewModel.consumeAcceptedPrintFeedback()
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(2, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID, ORDER_ID), printer.printAcceptedIds)
        assertEquals(ORDER_ID, printer.printAcceptedIds[0])
        assertEquals(ORDER_ID, printer.printAcceptedIds[1])
        val after = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals(before.orderId, after.orderId)
        assertEquals(before.displayNumber, after.displayNumber)
        assertEquals(before.total, after.total)
        assertTrue(viewModel.acceptedPrintUiState.value is AcceptedPrintUiState.Error)
    }

    @Test
    fun `PRINT023 retry blocked in-flight then available after failure`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val printer =
            FakePrinterService(
                result = PrintResult.Failure(PrinterError.BluetoothDisabled),
                blockUntil = gate,
            )
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(AcceptedPrintUiState.Printing, viewModel.acceptedPrintUiState.value)
        assertEquals(1, printer.printAcceptedCalls)

        viewModel.runAcceptedPrint()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)
        assertTrue(viewModel.acceptedPrintUiState.value is AcceptedPrintUiState.Error)

        viewModel.consumeAcceptedPrintFeedback()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(2, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID, ORDER_ID), printer.printAcceptedIds)
    }

    @Test
    fun `PRINT023 successful retry shows success feedback and no third call`() =
        runTest(mainDispatcher) {
            val printer =
                FakePrinterService(result = PrintResult.Failure(PrinterError.BluetoothDisabled))
            val viewModel =
                viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
            advanceUntilIdle()
            val before = viewModel.uiState.value as AcceptedOrderDetailUiState.Content

            viewModel.runAcceptedPrint()
            advanceUntilIdle()
            assertEquals(1, printer.printAcceptedCalls)

            printer.result = PrintResult.Success
            viewModel.consumeAcceptedPrintFeedback()
            viewModel.runAcceptedPrint()
            advanceUntilIdle()

            assertEquals(2, printer.printAcceptedCalls)
            assertEquals(listOf(ORDER_ID, ORDER_ID), printer.printAcceptedIds)
            val success = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Success
            assertEquals("Ordine inviato alla stampante", success.message)
            val after = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
            assertEquals(before.orderId, after.orderId)
            assertEquals(before.displayNumber, after.displayNumber)
            assertEquals(before.total, after.total)

            advanceUntilIdle()
            testScheduler.advanceTimeBy(60_000)
            advanceUntilIdle()
            assertEquals(2, printer.printAcceptedCalls)
        }

    @Test
    fun `PRINT023 failed retry does not auto-retry`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.BluetoothDisabled))
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        viewModel.consumeAcceptedPrintFeedback()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(2, printer.printAcceptedCalls)
        assertTrue(viewModel.acceptedPrintUiState.value is AcceptedPrintUiState.Error)

        advanceUntilIdle()
        testScheduler.advanceTimeBy(60_000)
        advanceUntilIdle()
        assertEquals(2, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID, ORDER_ID), printer.printAcceptedIds)
    }

    @Test
    fun `PRINT023 observer reload after failure does not auto-retry`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.BluetoothDisabled))
        val orderFlow = MutableStateFlow<Order?>(acceptedOrder())
        val viewModel = viewModel(orderFlow = orderFlow, printer = printer)
        advanceUntilIdle()

        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)

        orderFlow.value = acceptedOrder()
        advanceUntilIdle()
        viewModel.consumeAcceptedPrintFeedback()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertTrue(viewModel.uiState.value is AcceptedOrderDetailUiState.Content)
    }

    // --- PRINT-024 / D-071 uncertain ConnectionLost microcopy ---

    @Test
    fun `PRINT024 ConnectionLost maps uncertain copy without retry`() = runTest(mainDispatcher) {
        val printer =
            FakePrinterService(result = PrintResult.Failure(PrinterError.ConnectionLost))
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()
        val before = viewModel.uiState.value as AcceptedOrderDetailUiState.Content

        viewModel.runAcceptedPrint()
        advanceUntilIdle()

        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(listOf(ORDER_ID), printer.printAcceptedIds)
        assertEquals(
            "Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.",
            (viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Error).message,
        )
        val after = viewModel.uiState.value as AcceptedOrderDetailUiState.Content
        assertEquals(before.orderId, after.orderId)
        assertEquals(before.displayNumber, after.displayNumber)
        assertEquals(before.total, after.total)

        viewModel.consumeAcceptedPrintFeedback()
        advanceUntilIdle()
        testScheduler.advanceTimeBy(60_000)
        advanceUntilIdle()
        assertEquals(1, printer.printAcceptedCalls)
        assertEquals(AcceptedPrintUiState.Idle, viewModel.acceptedPrintUiState.value)
    }

    @Test
    fun `PRINT024 definite errors retain existing wording`() = runTest(mainDispatcher) {
        assertMappedAcceptedPrintError(
            PrinterError.BluetoothDisabled,
            "Bluetooth disattivato",
        )
        assertMappedAcceptedPrintError(
            PrinterError.PrinterNotConfigured,
            "Nessuna stampante selezionata",
        )
        assertMappedAcceptedPrintError(
            PrinterError.PermissionDenied,
            "Autorizzazione Bluetooth necessaria",
        )
        assertMappedAcceptedPrintError(
            PrinterError.ConnectionFailed,
            "Impossibile connettersi alla stampante",
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertMappedAcceptedPrintError(
        error: PrinterError,
        expectedMessage: String,
    ) {
        val printer = FakePrinterService(result = PrintResult.Failure(error))
        val viewModel = viewModel(orderFlow = MutableStateFlow(acceptedOrder()), printer = printer)
        advanceUntilIdle()
        viewModel.runAcceptedPrint()
        advanceUntilIdle()
        val state = viewModel.acceptedPrintUiState.value as AcceptedPrintUiState.Error
        assertEquals(expectedMessage, state.message)
    }

    private fun detailViewModel(
        repository: FakeOrderRepository,
        settingsFail: Boolean = false,
        orderId: String = ORDER_ID,
        printer: PrinterService = FakePrinterService(),
    ): AcceptedOrderDetailViewModel {
        val settings = FakeSettingsRepository(fail = settingsFail)
        val clock = FixedClock(romeLocal("2026-09-15T18:00:00"))
        return AcceptedOrderDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(AcceptedOrderDetailViewModel.ORDER_ID_ARGUMENT to orderId),
            ),
            getCurrentDayAcceptedOrder = GetCurrentDayAcceptedOrder(
                orderRepository = repository,
                settingsRepository = settings,
                clockProvider = clock,
            ),
            duplicateAcceptedOrder = DuplicateAcceptedOrder(
                orderRepository = repository,
                settingsRepository = settings,
                clockProvider = clock,
            ),
            replaceDraftWithAcceptedOrderDuplicate = ReplaceDraftWithAcceptedOrderDuplicate(
                orderRepository = repository,
                settingsRepository = settings,
                clockProvider = clock,
            ),
            orderRepository = repository,
            printerService = printer,
        )
    }

    private fun viewModel(
        orderFlow: MutableStateFlow<Order?>,
        settingsFail: Boolean = false,
        orderId: String = ORDER_ID,
        printer: PrinterService = FakePrinterService(),
    ): AcceptedOrderDetailViewModel =
        detailViewModel(FakeOrderRepository(orderFlow), settingsFail, orderId, printer)

    private class FakePrinterService(
        var result: PrintResult = PrintResult.Success,
        var throwOnPrintAccepted: Boolean = false,
        var throwCancellation: Boolean = false,
        private val blockUntil: CompletableDeferred<Unit>? = null,
    ) : PrinterService {
        var printAcceptedCalls: Int = 0
            private set
        var printDraftCalls: Int = 0
            private set
        val printAcceptedIds = mutableListOf<String>()

        override suspend fun printDraft(orderId: String): PrintResult {
            printDraftCalls += 1
            error("printDraft must not be called by PRINT-021 detail")
        }

        override suspend fun printAccepted(orderId: String): PrintResult {
            printAcceptedCalls += 1
            printAcceptedIds += orderId
            blockUntil?.await()
            if (throwCancellation) throw CancellationException("test cancel")
            if (throwOnPrintAccepted) error("unexpected printAccepted failure")
            return result
        }

        override suspend fun testPrint(): PrintResult =
            error("testPrint must not be called by PRINT-021")
    }

    private class FixedClock(private val now: Instant) : ClockProvider {
        override fun now(): Instant = now
    }

    private class FakeSettingsRepository(
        private val fail: Boolean = false,
    ) : SettingsRepository {
        override fun observeNumberingMode() = flowOf(NumberingModeLoadResult.PersistenceFailure)

        override suspend fun getNumberingMode() = NumberingModeLoadResult.PersistenceFailure

        override suspend fun getBusinessDaySettings(): BusinessDaySettingsLoadResult =
            if (fail) {
                BusinessDaySettingsLoadResult.PersistenceFailure
            } else {
                BusinessDaySettingsLoadResult.Loaded(
                    BusinessDaySettings(
                        timezoneId = "Europe/Rome",
                        businessDayStartMinutes = 300,
                    ),
                )
            }

        override suspend fun updateNumberingMode(
            mode: it.krpng.cassa.domain.model.NumberingMode,
        ): UpdateNumberingModeResult = UpdateNumberingModeResult.PersistenceFailure
    }

    private class FakeOrderRepository(
        private val orderFlow: MutableStateFlow<Order?>,
    ) : OrderRepository {
        var duplicateResult: DuplicateAcceptedOrderResult =
            DuplicateAcceptedOrderResult.SourceUnavailable
        var replaceResult: ReplaceDraftWithAcceptedOrderDuplicateResult =
            ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable
        var activeDraft: Order? = null
        var duplicateCalls: Int = 0
        var replaceCalls: Int = 0
        var lastExpectedDraftId: String? = null
        var duplicateGate: CompletableDeferred<Unit>? = null

        override suspend fun getById(orderId: String): Order? = orderFlow.value

        override fun observeById(orderId: String): Flow<Order?> = orderFlow

        override fun observeActiveDraft(): Flow<Order?> = flowOf(activeDraft)

        override suspend fun getActiveDraft(): Order? = activeDraft

        override suspend fun createDraft(): CreateDraftResult = CreateDraftResult.AlreadyExists

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult =
            DeleteDraftResult.NotFoundOrNotDraft

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult =
            ReplaceDraftResult.OriginalNotFoundOrNotDraft

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult = QuickAddStandardResult.OrderNotFound

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult = UpdateOrderItemResult.OrderNotFound

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): SplitStandardPizzaItemResult = SplitStandardPizzaItemResult.OrderNotFound

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): ChangeQuantityResult = ChangeQuantityResult.OrderNotFound

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): RemoveOrderItemResult = RemoveOrderItemResult.OrderNotFound

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult = UpdateGeneralNoteResult.OrderNotFound

        override suspend fun acceptOrder(orderId: String): AcceptOrderResult =
            AcceptOrderResult.OrderNotFound

        override fun observeAcceptedByBusinessDate(
            businessDate: LocalDate,
        ) = flowOf(emptyList<it.krpng.cassa.domain.model.AcceptedOrderSummary>())

        override suspend fun purgeAcceptedBefore(
            currentBusinessDate: LocalDate,
        ): PurgeAcceptedBeforeResult = PurgeAcceptedBeforeResult.Purged(0)

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
        ): DuplicateAcceptedOrderResult {
            duplicateCalls += 1
            duplicateGate?.await()
            return duplicateResult
        }

        override suspend fun replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
            expectedDraftId: String,
        ): ReplaceDraftWithAcceptedOrderDuplicateResult {
            replaceCalls += 1
            lastExpectedDraftId = expectedDraftId
            return replaceResult
        }
    }

    private companion object {
        const val ORDER_ID = "accepted-1"
        private val ROME: ZoneId = ZoneId.of("Europe/Rome")
        private val NOW: Instant = romeLocal("2026-09-15T12:30:00")

        fun romeLocal(localDateTime: String): Instant =
            LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()

        fun draftOrder(id: String, withItem: Boolean = false): Order = Order(
            id = id,
            status = OrderStatus.DRAFT,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = null,
            total = Money.ZERO,
            generalNote = null,
            sourceOrderId = null,
            items = if (withItem) {
                listOf(item(id = "d1", category = ProductCategory.PIZZA, sequence = 1))
            } else {
                emptyList()
            },
        )

        fun acceptedOrder(
            businessDate: LocalDate = LocalDate.parse("2026-09-15"),
            generalNote: String? = null,
            items: List<OrderItem> = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1),
            ),
            totalCents: Long = 1_400,
        ): Order = Order(
            id = ORDER_ID,
            status = OrderStatus.ACCEPTED,
            displayNumber = "042",
            numberingMode = it.krpng.cassa.domain.model.NumberingMode.SEQUENTIAL,
            numberingCycle = null,
            businessDate = businessDate,
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = NOW,
            total = Money.ofCents(totalCents),
            generalNote = generalNote,
            sourceOrderId = null,
            items = items,
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
            manualCents: Long? = null,
        ): OrderItem = OrderItem(
            id = id,
            productId = null,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printed,
            categorySnapshot = category,
            quantity = qty,
            baseUnitPrice = Money.ofCents(unitCents),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = manualCents?.let(Money::ofCents),
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = true,
            note = note,
            createdSequence = sequence,
            additions = additions,
            removals = removals,
        )
    }
}
