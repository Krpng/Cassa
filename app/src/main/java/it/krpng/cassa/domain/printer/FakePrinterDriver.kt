package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile

/**
 * In-process [PrinterDriver] fake for tests/dev without hardware (PRINT-006 / D-053).
 * Not thread-safe; Mutex serialization belongs to PRINT-007.
 */
class FakePrinterDriver : PrinterDriver {
    private var connected: Boolean = false
    private val connectResults = ArrayDeque<PrinterResult>()
    private val printResults = ArrayDeque<PrinterResult>()
    private val payloads = mutableListOf<ByteArray>()

    /** Read-only connection observation for tests (no public PrinterState domain type). */
    val isConnected: Boolean
        get() = connected

    /** Ordered history of connected print attempts (defensive copies on read). */
    val capturedPayloads: List<ByteArray>
        get() = payloads.map { it.copyOf() }

    val capturedCount: Int
        get() = payloads.size

    val lastCapturedPayload: ByteArray?
        get() = payloads.lastOrNull()?.copyOf()

    fun enqueueConnectResult(result: PrinterResult) {
        connectResults.addLast(result)
    }

    fun enqueuePrintResult(result: PrinterResult) {
        printResults.addLast(result)
    }

    override suspend fun connect(profile: PrinterProfile): PrinterResult {
        if (connected) {
            // Idempotent: do not consume queued connect results.
            return PrinterResult.Success
        }
        val result = dequeueOrSuccess(connectResults)
        connected = result is PrinterResult.Success
        return result
    }

    override suspend fun print(data: ByteArray): PrinterResult {
        if (!connected) {
            // Do not capture; do not consume queued print results.
            return PrinterResult.Failure(PrinterError.ConnectionLost)
        }
        payloads += data.copyOf()
        val result = dequeueOrSuccess(printResults)
        if (result is PrinterResult.Failure && result.error is PrinterError.ConnectionLost) {
            connected = false
        }
        return result
    }

    override suspend fun disconnect() {
        connected = false
        // Does not clear history or injection queues.
    }

    private fun dequeueOrSuccess(queue: ArrayDeque<PrinterResult>): PrinterResult =
        if (queue.isEmpty()) {
            PrinterResult.Success
        } else {
            queue.removeFirst()
        }
}
