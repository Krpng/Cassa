package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.PricePrintMode
import kotlinx.coroutines.flow.Flow

/**
 * Printer/device preferences SoT (D-050 / PRINT-003 + D-057 / BT-003).
 * Persisted in DataStore — not Room `app_settings`.
 *
 * [getSelectedPrinterId] / [observeSelectedPrinterId] return `null` when unconfigured.
 * That is not [it.krpng.cassa.domain.printer.PrinterError.PrinterNotConfigured] at this layer.
 */
interface PrinterSettingsRepository {
    suspend fun getPricePrintMode(): PricePrintMode

    fun observePricePrintMode(): Flow<PricePrintMode>

    suspend fun updatePricePrintMode(mode: PricePrintMode)

    /** Bonded printer address id, or `null` if none selected. */
    suspend fun getSelectedPrinterId(): String?

    fun observeSelectedPrinterId(): Flow<String?>

    /**
     * Persists the exact nonblank [id] (`BondedBluetoothDevice.id`).
     * Rejects blank/whitespace-only ids without writing a configured selection.
     */
    suspend fun setSelectedPrinterId(id: String)

    /** Removes selection; subsequent reads are `null`. Idempotent when already empty. */
    suspend fun clearSelectedPrinterId()
}
