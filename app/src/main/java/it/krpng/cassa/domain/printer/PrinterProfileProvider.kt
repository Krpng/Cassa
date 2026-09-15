package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile

/**
 * Runtime source of the active [PrinterProfile] (D-054 / PRINT-007).
 *
 * Returns one complete profile (including [PrinterProfile.pricePrintMode]) or `null`
 * when not configured → [PrinterError.PrinterNotConfigured].
 *
 * [DefaultPrinterService] must not assemble profiles or read DataStore keys.
 * Concrete hardware/config-backed provider = M9.
 */
fun interface PrinterProfileProvider {
    suspend fun getActiveProfile(): PrinterProfile?
}
