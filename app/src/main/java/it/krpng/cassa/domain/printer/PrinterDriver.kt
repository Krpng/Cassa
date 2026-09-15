package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile

/**
 * Transport-level print port (D-048 / architecture §20).
 * Pure Kotlin contract — no Bluetooth, Android, or vendor types.
 * Concrete drivers: Fake (PRINT-006), Bluetooth ESC/POS (M9).
 */
interface PrinterDriver {
    suspend fun connect(profile: PrinterProfile): PrinterResult

    suspend fun print(data: ByteArray): PrinterResult

    suspend fun disconnect()
}