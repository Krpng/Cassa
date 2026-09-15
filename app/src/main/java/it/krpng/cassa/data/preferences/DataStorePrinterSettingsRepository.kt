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
 * DataStore-backed [PrinterSettingsRepository] (D-050 / PRINT-003).
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
}