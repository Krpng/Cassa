package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.NumberingMode
import kotlinx.coroutines.flow.Flow

/**
 * Persisted app settings. [NumberingMode] source of truth is `app_settings.numberingMode`.
 * Future AcceptOrder must read mode via [getNumberingMode] at accept time.
 */
interface SettingsRepository {
    fun observeNumberingMode(): Flow<NumberingModeLoadResult>

    suspend fun getNumberingMode(): NumberingModeLoadResult

    suspend fun updateNumberingMode(mode: NumberingMode): UpdateNumberingModeResult
}

sealed interface NumberingModeLoadResult {
    data class Loaded(val mode: NumberingMode) : NumberingModeLoadResult

    /** Stored string is not a known [NumberingMode]; row is not auto-rewritten. */
    data object InvalidStoredMode : NumberingModeLoadResult

    data object PersistenceFailure : NumberingModeLoadResult
}

sealed interface UpdateNumberingModeResult {
    data object Updated : UpdateNumberingModeResult

    data object InvalidStoredMode : UpdateNumberingModeResult

    data object PersistenceFailure : UpdateNumberingModeResult
}
