package it.krpng.cassa.domain.printer

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.AcceptedOrderSummary
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.DuplicateAcceptedOrderResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.PurgeAcceptedBeforeResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftWithAcceptedOrderDuplicateResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPrinterServiceTest {
    @Test
    fun `PRINT-T009 successful draft print is one driver print attempt with expected bytes`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val orders = MutableOrderRepository(draftOrder())
            val profile = testProfile()
            val service = service(orders, StaticPrinterProfileProvider(profile), driver)

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Success, result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(1, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
            assertFalse(driver.fake.isConnected)

            val expected =
                DefaultEscPosEncoder().encode(
                    DefaultReceiptComposer().compose(
                        orders.getById(DRAFT_ID)!!,
                        PrintKind.DRAFT,
                        profile.pricePrintMode,
                        profile.charsPerLine,
                    ),
                    profile,
                ) as EncodeResult.Success
            assertArrayEquals(expected.bytes, driver.fake.lastCapturedPayload)
        }

    @Test
    fun `successful accepted print captures exactly one FINAL payload`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val orders = MutableOrderRepository(acceptedOrder())
            val profile = testProfile()
            val service = service(orders, StaticPrinterProfileProvider(profile), driver)

            assertEquals(PrintResult.Success, service.printAccepted(ACCEPTED_ID))
            assertEquals(1, driver.printAttempts)
            assertEquals(1, driver.fake.capturedCount)

            val expected =
                DefaultEscPosEncoder().encode(
                    DefaultReceiptComposer().compose(
                        acceptedOrder(),
                        PrintKind.FINAL,
                        profile.pricePrintMode,
                        profile.charsPerLine,
                    ),
                    profile,
                ) as EncodeResult.Success
            assertArrayEquals(expected.bytes, driver.fake.lastCapturedPayload)
        }

    @Test
    fun `PRINT-T020 missing profile is PrinterNotConfigured with zero driver attempts`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val orders = MutableOrderRepository(draftOrder())
            val service =
                service(orders, StaticPrinterProfileProvider(profile = null), driver)

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.PrinterNotConfigured), result)
            assertEquals(0, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(0, driver.disconnectAttempts)
            assertEquals(0, driver.fake.capturedCount)
        }

    @Test
    fun `encode failure maps InvalidProfile and skips driver lifecycle`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val orders = MutableOrderRepository(draftOrder())
            val badProfile = testProfile().copy(feedLines = -1)
            val service = service(orders, StaticPrinterProfileProvider(badProfile), driver)

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.InvalidPrinterProfile), result)
            assertEquals(0, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(0, driver.disconnectAttempts)
        }

    @Test
    fun `encode UnsupportedEncoding maps one-to-one without driver attempts`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val orders = MutableOrderRepository(draftOrder())
            val badCharset = testProfile().copy(codePage = "NOT-A-REAL-CHARSET-XYZ")
            val service = service(orders, StaticPrinterProfileProvider(badCharset), driver)

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.UnsupportedEncoding), result)
            assertEquals(0, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
        }

    @Test
    fun `connect failure propagates and disconnects Fake`() =
        runTest {
            val fake = FakePrinterDriver()
            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.ConnectionFailed))
            val driver = RecordingDriver(fake)
            val orders = MutableOrderRepository(draftOrder())
            val service = service(orders, StaticPrinterProfileProvider(testProfile()), driver)

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.ConnectionFailed), result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
            assertFalse(fake.isConnected)
            assertEquals(0, fake.capturedCount)
        }

    @Test
    fun `print Timeout propagates after connect`() =
        runTest {
            val fake = FakePrinterDriver()
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.Timeout))
            val driver = RecordingDriver(fake)
            val orders = MutableOrderRepository(draftOrder())
            val service = service(orders, StaticPrinterProfileProvider(testProfile()), driver)

            assertEquals(
                PrintResult.Failure(PrinterError.Timeout),
                service.printDraft(DRAFT_ID),
            )
            assertEquals(1, driver.printAttempts)
            assertFalse(fake.isConnected)
            assertEquals(1, fake.capturedCount)
        }

    @Test
    fun `PRINT-T022 ConnectionLost leaves accepted order unchanged`() =
        runTest {
            val accepted = acceptedOrder()
            val orders = MutableOrderRepository(accepted)
            val fake = FakePrinterDriver()
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.ConnectionLost))
            val driver = RecordingDriver(fake)
            val service = service(orders, StaticPrinterProfileProvider(testProfile()), driver)

            val result = service.printAccepted(ACCEPTED_ID)

            assertEquals(PrintResult.Failure(PrinterError.ConnectionLost), result)
            val after = orders.getById(ACCEPTED_ID)!!
            assertEquals(OrderStatus.ACCEPTED, after.status)
            assertEquals("A37", after.displayNumber)
            assertEquals(accepted.total, after.total)
            assertEquals(0, orders.writeCount.get())
            assertFalse(fake.isConnected)
        }

    @Test
    fun `PRINT-T023 service invariant accepted retry keeps same displayNumber and content`() =
        runTest {
            val orders = MutableOrderRepository(acceptedOrder())
            val fake = FakePrinterDriver()
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrintFailed))
            val driver = RecordingDriver(fake)
            val profile = testProfile()
            val service = service(orders, StaticPrinterProfileProvider(profile), driver)

            assertEquals(
                PrintResult.Failure(PrinterError.PrintFailed),
                service.printAccepted(ACCEPTED_ID),
            )
            assertEquals("A37", orders.getById(ACCEPTED_ID)!!.displayNumber)

            val retryDriver = RecordingDriver(FakePrinterDriver())
            val retryService =
                service(orders, StaticPrinterProfileProvider(profile), retryDriver)
            assertEquals(PrintResult.Success, retryService.printAccepted(ACCEPTED_ID))
            assertEquals("A37", orders.getById(ACCEPTED_ID)!!.displayNumber)
            assertEquals(0, orders.writeCount.get())

            val expected =
                DefaultEscPosEncoder().encode(
                    DefaultReceiptComposer().compose(
                        acceptedOrder(),
                        PrintKind.FINAL,
                        profile.pricePrintMode,
                        profile.charsPerLine,
                    ),
                    profile,
                ) as EncodeResult.Success
            assertArrayEquals(expected.bytes, retryDriver.fake.lastCapturedPayload)
        }

    @Test
    fun `PRINT-T024 Mutex serializes concurrent jobs without interleaved driver lifecycle`() =
        runTest {
            val gate = GatedPrinterDriver()
            val orders =
                MutableOrderRepository(
                    draftOrder(id = "d1"),
                    draftOrder(id = "d2"),
                )
            val service = service(orders, StaticPrinterProfileProvider(testProfile()), gate)

            val jobA = async { service.printDraft("d1") }
            gate.firstEntered.await()

            val jobB = async { service.printDraft("d2") }
            repeat(20) { yield() }

            assertEquals(1, gate.connectAttempts.get())
            assertFalse(gate.secondEntered.isCompleted)

            gate.releaseFirst.complete(Unit)
            assertEquals(PrintResult.Success, jobA.await())
            assertEquals(PrintResult.Success, jobB.await())

            assertTrue(gate.secondEntered.isCompleted)
            assertEquals(2, gate.connectAttempts.get())
            assertEquals(2, gate.printAttempts.get())
        }

    @Test
    fun `PRINT-T025 service invariant draft print and retry never allocate displayNumber`() =
        runTest {
            val orders = MutableOrderRepository(draftOrder())
            val fake = FakePrinterDriver()
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrintFailed))
            val service =
                service(orders, StaticPrinterProfileProvider(testProfile()), RecordingDriver(fake))

            assertEquals(
                PrintResult.Failure(PrinterError.PrintFailed),
                service.printDraft(DRAFT_ID),
            )
            assertNull(orders.getById(DRAFT_ID)!!.displayNumber)

            assertEquals(
                PrintResult.Success,
                service(
                    orders,
                    StaticPrinterProfileProvider(testProfile()),
                    RecordingDriver(FakePrinterDriver()),
                ).printDraft(DRAFT_ID),
            )
            assertNull(orders.getById(DRAFT_ID)!!.displayNumber)
            assertEquals(OrderStatus.DRAFT, orders.getById(DRAFT_ID)!!.status)
            assertEquals(0, orders.writeCount.get())
        }

    @Test
    fun `PRINT-T027 testPrint creates and modifies no Order`() =
        runTest {
            val orders = MutableOrderRepository()
            val driver = RecordingDriver(FakePrinterDriver())
            val profile = testProfile()
            val service = service(orders, StaticPrinterProfileProvider(profile), driver)

            assertEquals(PrintResult.Success, service.testPrint())

            assertEquals(0, orders.getByIdCount.get())
            assertEquals(0, orders.writeCount.get())
            assertEquals(1, driver.printAttempts)

            val expected =
                DefaultEscPosEncoder().encode(
                    DefaultPrinterService.testPrintDocument(),
                    profile,
                ) as EncodeResult.Success
            assertArrayEquals(expected.bytes, driver.fake.lastCapturedPayload)
            assertFalse(driver.fake.isConnected)
        }

    @Test
    fun `missing order is OrderNotFound without driver attempts`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val service =
                service(
                    MutableOrderRepository(),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            assertEquals(
                PrintResult.Failure(PrinterError.OrderNotFound),
                service.printDraft("missing"),
            )
            assertEquals(0, driver.connectAttempts)
        }

    @Test
    fun `wrong status is InvalidOrderState without driver attempts`() =
        runTest {
            val driver = RecordingDriver(FakePrinterDriver())
            val service =
                service(
                    MutableOrderRepository(acceptedOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            assertEquals(
                PrintResult.Failure(PrinterError.InvalidOrderState),
                service.printDraft(ACCEPTED_ID),
            )
            assertEquals(0, driver.connectAttempts)
        }

    @Test
    fun `testPrint does not use ReceiptComposer`() =
        runTest {
            val composer = CountingReceiptComposer()
            val driver = RecordingDriver(FakePrinterDriver())
            val service =
                DefaultPrinterService(
                    orderRepository = MutableOrderRepository(),
                    profileProvider = StaticPrinterProfileProvider(testProfile()),
                    receiptComposer = composer,
                    escPosEncoder = DefaultEscPosEncoder(),
                    printerDriver = driver,
                )

            assertEquals(PrintResult.Success, service.testPrint())
            assertEquals(0, composer.composeCount)
        }

    @Test
    fun `Q6b print typed Failure preserved when disconnect throws`() =
        runTest {
            val driver =
                ScriptedThrowingDisconnectDriver(
                    connectResult = PrinterResult.Success,
                    printResult = PrinterResult.Failure(PrinterError.Timeout),
                )
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.Timeout), result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(1, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
        }

    @Test
    fun `Q6b connect typed Failure preserved when disconnect throws`() =
        runTest {
            val driver =
                ScriptedThrowingDisconnectDriver(
                    connectResult = PrinterResult.Failure(PrinterError.ConnectionFailed),
                )
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.ConnectionFailed), result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
        }

    @Test
    fun `Q6b successful print becomes Unknown when disconnect throws`() =
        runTest {
            val driver =
                ScriptedThrowingDisconnectDriver(
                    connectResult = PrinterResult.Success,
                    printResult = PrinterResult.Success,
                )
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.Unknown), result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(1, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
        }

    @Test
    fun `Q6b unexpected print exception and disconnect throw both map to Unknown`() =
        runTest {
            val driver =
                ScriptedThrowingDisconnectDriver(
                    connectResult = PrinterResult.Success,
                    printThrows = true,
                )
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.Unknown), result)
            assertEquals(1, driver.connectAttempts)
            assertEquals(1, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
        }

    @Test
    fun `Q6b missing profile never calls driver even if disconnect would throw`() =
        runTest {
            val driver = ScriptedThrowingDisconnectDriver()
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(profile = null),
                    driver,
                )

            val result = service.printDraft(DRAFT_ID)

            assertEquals(PrintResult.Failure(PrinterError.PrinterNotConfigured), result)
            assertEquals(0, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(0, driver.disconnectAttempts)
        }

    @Test
    fun `CancellationException from connect is rethrown not mapped to Unknown`() =
        kotlinx.coroutines.runBlocking {
            val driver = CancellingConnectDriver()
            val service =
                service(
                    MutableOrderRepository(draftOrder()),
                    StaticPrinterProfileProvider(testProfile()),
                    driver,
                )

            try {
                service.printDraft(DRAFT_ID)
                org.junit.Assert.fail("expected CancellationException")
            } catch (_: kotlinx.coroutines.CancellationException) {
                // D-059: structured concurrency preserved
            }
            assertEquals(1, driver.connectAttempts)
            assertEquals(0, driver.printAttempts)
            assertEquals(1, driver.disconnectAttempts)
        }

    private fun service(
        orders: OrderRepository,
        profiles: PrinterProfileProvider,
        driver: PrinterDriver,
    ): DefaultPrinterService =
        DefaultPrinterService(
            orderRepository = orders,
            profileProvider = profiles,
            receiptComposer = DefaultReceiptComposer(),
            escPosEncoder = DefaultEscPosEncoder(),
            printerDriver = driver,
        )

    private class StaticPrinterProfileProvider(
        private val profile: PrinterProfile?,
    ) : PrinterProfileProvider {
        override suspend fun getActiveProfile(): PrinterProfile? = profile
    }

    private class RecordingDriver(
        val fake: FakePrinterDriver,
    ) : PrinterDriver {
        var connectAttempts = 0
        var printAttempts = 0
        var disconnectAttempts = 0

        override suspend fun connect(profile: PrinterProfile): PrinterResult {
            connectAttempts++
            return fake.connect(profile)
        }

        override suspend fun print(data: ByteArray): PrinterResult {
            printAttempts++
            return fake.print(data)
        }

        override suspend fun disconnect() {
            disconnectAttempts++
            fake.disconnect()
        }
    }

    /** Test-only driver that always throws from [disconnect] (D-054 Q6b). */
    private class ScriptedThrowingDisconnectDriver(
        private val connectResult: PrinterResult = PrinterResult.Success,
        private val printResult: PrinterResult = PrinterResult.Success,
        private val printThrows: Boolean = false,
    ) : PrinterDriver {
        var connectAttempts = 0
        var printAttempts = 0
        var disconnectAttempts = 0

        override suspend fun connect(profile: PrinterProfile): PrinterResult {
            connectAttempts++
            return connectResult
        }

        override suspend fun print(data: ByteArray): PrinterResult {
            printAttempts++
            if (printThrows) {
                throw IllegalStateException("unexpected print failure")
            }
            return printResult
        }

        override suspend fun disconnect() {
            disconnectAttempts++
            throw IllegalStateException("disconnect cleanup boom")
        }
    }

    /** Connect throws [kotlinx.coroutines.CancellationException] (D-059 structured concurrency). */
    private class CancellingConnectDriver : PrinterDriver {
        var connectAttempts = 0
        var printAttempts = 0
        var disconnectAttempts = 0

        override suspend fun connect(profile: PrinterProfile): PrinterResult {
            connectAttempts++
            throw kotlinx.coroutines.CancellationException("connect cancelled")
        }

        override suspend fun print(data: ByteArray): PrinterResult {
            printAttempts++
            return PrinterResult.Success
        }

        override suspend fun disconnect() {
            disconnectAttempts++
        }
    }

    private class GatedPrinterDriver : PrinterDriver {
        val connectAttempts = AtomicInteger(0)
        val printAttempts = AtomicInteger(0)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        override suspend fun connect(profile: PrinterProfile): PrinterResult {
            val n = connectAttempts.incrementAndGet()
            if (n == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            } else {
                secondEntered.complete(Unit)
            }
            return PrinterResult.Success
        }

        override suspend fun print(data: ByteArray): PrinterResult {
            printAttempts.incrementAndGet()
            return PrinterResult.Success
        }

        override suspend fun disconnect() = Unit
    }

    private class CountingReceiptComposer : ReceiptComposer {
        var composeCount = 0

        override fun compose(
            order: Order,
            kind: PrintKind,
            pricePrintMode: PricePrintMode,
            charsPerLine: Int,
        ): PrintableDocument {
            composeCount++
            return DefaultReceiptComposer().compose(order, kind, pricePrintMode, charsPerLine)
        }
    }

    private class MutableOrderRepository(
        vararg initial: Order,
    ) : OrderRepository {
        private val orders = initial.associateBy { it.id }.toMutableMap()
        val writeCount = AtomicInteger(0)
        val getByIdCount = AtomicInteger(0)

        override suspend fun getById(orderId: String): Order? {
            getByIdCount.incrementAndGet()
            return orders[orderId]
        }

        override fun observeById(orderId: String): Flow<Order?> = flowOf(orders[orderId])

        override fun observeActiveDraft(): Flow<Order?> = flowOf(null)

        override suspend fun getActiveDraft(): Order? = null

        override suspend fun createDraft(): CreateDraftResult {
            writeCount.incrementAndGet()
            return CreateDraftResult.AlreadyExists
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            writeCount.incrementAndGet()
            return DeleteDraftResult.NotFoundOrNotDraft
        }

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult {
            writeCount.incrementAndGet()
            return ReplaceDraftResult.OriginalNotFoundOrNotDraft
        }

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult {
            writeCount.incrementAndGet()
            return QuickAddStandardResult.OrderNotFound
        }

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult {
            writeCount.incrementAndGet()
            return UpdateOrderItemResult.OrderNotFound
        }

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): SplitStandardPizzaItemResult {
            writeCount.incrementAndGet()
            return SplitStandardPizzaItemResult.OrderNotFound
        }

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): ChangeQuantityResult {
            writeCount.incrementAndGet()
            return ChangeQuantityResult.OrderNotFound
        }

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): RemoveOrderItemResult {
            writeCount.incrementAndGet()
            return RemoveOrderItemResult.OrderNotFound
        }

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult {
            writeCount.incrementAndGet()
            return UpdateGeneralNoteResult.OrderNotFound
        }

        override suspend fun acceptOrder(orderId: String): AcceptOrderResult {
            writeCount.incrementAndGet()
            return AcceptOrderResult.OrderNotFound
        }

        override fun observeAcceptedByBusinessDate(
            businessDate: LocalDate,
        ): Flow<List<AcceptedOrderSummary>> = flowOf(emptyList())

        override suspend fun purgeAcceptedBefore(
            currentBusinessDate: LocalDate,
        ): PurgeAcceptedBeforeResult {
            writeCount.incrementAndGet()
            return PurgeAcceptedBeforeResult.Purged(0)
        }

        override suspend fun duplicateAcceptedOrder(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
        ): DuplicateAcceptedOrderResult {
            writeCount.incrementAndGet()
            return DuplicateAcceptedOrderResult.SourceUnavailable
        }

        override suspend fun replaceDraftWithAcceptedOrderDuplicate(
            sourceOrderId: String,
            currentBusinessDate: LocalDate,
            expectedDraftId: String,
        ): ReplaceDraftWithAcceptedOrderDuplicateResult {
            writeCount.incrementAndGet()
            return ReplaceDraftWithAcceptedOrderDuplicateResult.SourceUnavailable
        }
    }

    private companion object {
        const val DRAFT_ID = "draft-1"
        const val ACCEPTED_ID = "accepted-1"
        private val FIXED = Instant.parse("2026-09-15T12:00:00Z")

        fun testProfile(
            id: String = "test-profile",
        ): PrinterProfile =
            PrinterProfile(
                id = id,
                name = "Test",
                charsPerLine = 32,
                codePage = "ISO-8859-1",
                feedLines = 2,
                pricePrintMode = PricePrintMode.DETAILED,
            )

        fun draftOrder(id: String = DRAFT_ID): Order =
            order(
                id = id,
                status = OrderStatus.DRAFT,
                displayNumber = null,
            )

        fun acceptedOrder(): Order =
            order(
                id = ACCEPTED_ID,
                status = OrderStatus.ACCEPTED,
                displayNumber = "A37",
            )

        fun order(
            id: String,
            status: OrderStatus,
            displayNumber: String?,
        ): Order =
            Order(
                id = id,
                status = status,
                displayNumber = displayNumber,
                numberingMode = if (status == OrderStatus.ACCEPTED) NumberingMode.SEQUENTIAL else null,
                numberingCycle = null,
                businessDate = if (status == OrderStatus.ACCEPTED) LocalDate.of(2026, 9, 15) else null,
                createdAt = FIXED,
                updatedAt = FIXED,
                acceptedAt = if (status == OrderStatus.ACCEPTED) FIXED else null,
                total = Money.ofCents(700),
                generalNote = null,
                sourceOrderId = null,
                items =
                    listOf(
                        OrderItem(
                            id = "item-1",
                            productId = 1L,
                            productNameSnapshot = "Margherita",
                            productPrintedNameSnapshot = "Margherita",
                            categorySnapshot = ProductCategory.PIZZA,
                            quantity = 1,
                            baseUnitPrice = Money.ofCents(700),
                            automaticExtrasTotal = Money.ZERO,
                            manualUnitPrice = null,
                            finalUnitPrice = Money.ofCents(700),
                            automaticExtrasPricingSnapshot = true,
                            note = null,
                            createdSequence = 1,
                            additions = emptyList(),
                            removals = emptyList(),
                        ),
                    ),
            )
    }
}
