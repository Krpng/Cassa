package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.PricePrintMode
import kotlinx.coroutines.flow.Flow

/**
 * Printer/device preferences SoT for [PricePrintMode] (D-050 / PRINT-003).
 * Persisted in DataStore — not Room `app_settings`.
 */
interface PrinterSettingsRepository {
    suspend fun getPricePrintMode(): PricePrintMode

    fun observePricePrintMode(): Flow<PricePrintMode>

    suspend fun updatePricePrintMode(mode: PricePrintMode)
}