package it.krpng.cassa.domain.printer

/**
 * Application print API (D-048 / D-054 / architecture §20).
 * Implementation: [DefaultPrinterService] (PRINT-007) — whole-job Mutex orchestration.
 */
interface PrinterService {
    suspend fun printDraft(orderId: String): PrintResult

    suspend fun printAccepted(orderId: String): PrintResult

    suspend fun testPrint(): PrintResult
}
