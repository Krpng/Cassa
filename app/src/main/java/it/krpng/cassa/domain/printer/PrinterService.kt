package it.krpng.cassa.domain.printer

/**
 * Application print API (D-048 / architecture §20).
 * Signatures only — Mutex serialization and implementations are PRINT-007+.
 */
interface PrinterService {
    suspend fun printDraft(orderId: String): PrintResult

    suspend fun printAccepted(orderId: String): PrintResult

    suspend fun testPrint(): PrintResult
}