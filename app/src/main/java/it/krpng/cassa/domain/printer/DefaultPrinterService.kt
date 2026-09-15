package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.repository.OrderRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production [PrinterService] orchestration (PRINT-007 / D-054).
 *
 * Whole-job [Mutex]: load → profile → compose/build → encode → connect → print → disconnect.
 * No AcceptOrder, Room writes, numbering, Bluetooth, or invented NETUM profile defaults.
 * Concrete [PrinterProfileProvider] wiring = M9.
 */
class DefaultPrinterService(
    private val orderRepository: OrderRepository,
    private val profileProvider: PrinterProfileProvider,
    private val receiptComposer: ReceiptComposer,
    private val escPosEncoder: EscPosEncoder,
    private val printerDriver: PrinterDriver,
) : PrinterService {
    private val mutex = Mutex()

    override suspend fun printDraft(orderId: String): PrintResult =
        mutex.withLock {
            printOrderJob(
                orderId = orderId,
                requiredStatus = OrderStatus.DRAFT,
                kind = PrintKind.DRAFT,
            )
        }

    override suspend fun printAccepted(orderId: String): PrintResult =
        mutex.withLock {
            printOrderJob(
                orderId = orderId,
                requiredStatus = OrderStatus.ACCEPTED,
                kind = PrintKind.FINAL,
            )
        }

    override suspend fun testPrint(): PrintResult =
        mutex.withLock {
            val profile =
                profileProvider.getActiveProfile()
                    ?: return@withLock PrintResult.Failure(PrinterError.PrinterNotConfigured)
            val document = testPrintDocument()
            when (val encoded = escPosEncoder.encode(document, profile)) {
                is EncodeResult.Failure ->
                    PrintResult.Failure(mapEncodeError(encoded.error))
                is EncodeResult.Success ->
                    runDriverLifecycle(profile, encoded.bytes)
            }
        }

    private suspend fun printOrderJob(
        orderId: String,
        requiredStatus: OrderStatus,
        kind: PrintKind,
    ): PrintResult {
        val order =
            orderRepository.getById(orderId)
                ?: return PrintResult.Failure(PrinterError.OrderNotFound)
        if (order.status != requiredStatus) {
            return PrintResult.Failure(PrinterError.InvalidOrderState)
        }
        val profile =
            profileProvider.getActiveProfile()
                ?: return PrintResult.Failure(PrinterError.PrinterNotConfigured)
        val document =
            receiptComposer.compose(
                order = order,
                kind = kind,
                pricePrintMode = profile.pricePrintMode,
                charsPerLine = profile.charsPerLine,
            )
        return when (val encoded = escPosEncoder.encode(document, profile)) {
            is EncodeResult.Failure ->
                PrintResult.Failure(mapEncodeError(encoded.error))
            is EncodeResult.Success ->
                runDriverLifecycle(profile, encoded.bytes)
        }
    }

    /**
     * Driver session after a successful encode.
     * [PrinterDriver.disconnect] runs in `finally` once [PrinterDriver.connect] was attempted.
     *
     * Cleanup: if [disconnect] throws after a typed [PrintResult.Failure], keep that failure
     * (do not replace with [PrinterError.Unknown]). If it throws after Success / unset outcome,
     * map to [PrinterError.Unknown] (D-054 unexpected → Unknown).
     */
    private suspend fun runDriverLifecycle(
        profile: PrinterProfile,
        bytes: ByteArray,
    ): PrintResult {
        var outcome: PrintResult = PrintResult.Failure(PrinterError.Unknown)
        try {
            when (val connectResult = printerDriver.connect(profile)) {
                is PrinterResult.Failure -> {
                    outcome = PrintResult.Failure(connectResult.error)
                }
                is PrinterResult.Success -> {
                    outcome =
                        when (val printResult = printerDriver.print(bytes)) {
                            is PrinterResult.Success -> PrintResult.Success
                            is PrinterResult.Failure -> PrintResult.Failure(printResult.error)
                        }
                }
            }
        } catch (_: Throwable) {
            outcome = PrintResult.Failure(PrinterError.Unknown)
        } finally {
            try {
                printerDriver.disconnect()
            } catch (_: Throwable) {
                if (outcome is PrintResult.Success) {
                    outcome = PrintResult.Failure(PrinterError.Unknown)
                }
            }
        }
        return outcome
    }

    private fun mapEncodeError(error: EncodeError): PrinterError =
        when (error) {
            EncodeError.UnsupportedEncoding -> PrinterError.UnsupportedEncoding
            EncodeError.UnencodableCharacter -> PrinterError.UnencodableCharacter
            EncodeError.InvalidProfile -> PrinterError.InvalidPrinterProfile
        }

    companion object {
        fun testPrintDocument(): PrintableDocument =
            PrintableDocument(
                kind = PrintKind.DRAFT,
                lines =
                    listOf(
                        PrintableLine("TEST STAMPANTE", PrintEmphasis.EMPHASIZED),
                        PrintableLine("Cassa", PrintEmphasis.NORMAL),
                        PrintableLine("à è ì ò ù €", PrintEmphasis.NORMAL),
                    ),
            )
    }
}
