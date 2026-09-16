package it.krpng.cassa.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.PricePrintModeParser
import it.krpng.cassa.domain.repository.PrinterSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [PrinterSettingsRepository] (D-050 / PRINT-003 + D-057 / BT-003).
 *
 * Does not access Bluetooth stack, assemble [it.krpng.cassa.domain.model.PrinterProfile],
 * or map missing selection to PrinterNotConfigured.
 */
@Singleton
class DataStorePrinterSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PrinterSettingsRepository {
    override suspend fun getPricePrintMode(): PricePrintMode =
        PricePrintModeParser.parseStoredOrDefault(
            dataStore.data.first()[PrinterPreferences.PRICE_PRINT_MODE],
        )

    override fun observePricePrintMode(): Flow<PricePrintMode> =
        dataStore.data.map { prefs ->
            PricePrintModeParser.parseStoredOrDefault(prefs[PrinterPreferences.PRICE_PRINT_MODE])
        }

    override suspend fun updatePricePrintMode(mode: PricePrintMode) {
        dataStore.edit { prefs ->
            prefs[PrinterPreferences.PRICE_PRINT_MODE] = mode.name
        }
    }

    override suspend fun getSelectedPrinterId(): String? =
        normalizeSelectedPrinterId(
            dataStore.data.first()[PrinterPreferences.SELECTED_PRINTER_ID],
        )

    override fun observeSelectedPrinterId(): Flow<String?> =
        dataStore.data.map { prefs ->
            normalizeSelectedPrinterId(prefs[PrinterPreferences.SELECTED_PRINTER_ID])
        }

    override suspend fun setSelectedPrinterId(id: String) {
        require(id.isNotBlank()) { "selected printer id must be nonblank" }
        dataStore.edit { prefs ->
            prefs[PrinterPreferences.SELECTED_PRINTER_ID] = id
        }
    }

    override suspend fun clearSelectedPrinterId() {
        dataStore.edit { prefs ->
            prefs.remove(PrinterPreferences.SELECTED_PRINTER_ID)
        }
    }

    private fun normalizeSelectedPrinterId(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
}
