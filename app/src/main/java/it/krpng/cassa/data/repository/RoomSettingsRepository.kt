package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.dao.AppSettingsDao
import it.krpng.cassa.data.database.entity.AppSettingsEntity
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.NumberingModeParser
import it.krpng.cassa.domain.repository.BusinessDaySettings
import it.krpng.cassa.domain.repository.BusinessDaySettingsLoadResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.SettingsRepository
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

/**
 * Room-backed settings. Mode updates do not mutate numbering_state or orders.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSettingsRepository @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
    private val clockProvider: ClockProvider,
) : SettingsRepository {
    override fun observeNumberingMode(): Flow<NumberingModeLoadResult> =
        appSettingsDao.observeNumberingModeName().mapLatest { raw ->
            if (raw == null) {
                ensureDefaultLoaded()
            } else {
                parseStored(raw)
            }
        }

    override suspend fun getNumberingMode(): NumberingModeLoadResult {
        val raw = appSettingsDao.getNumberingModeName()
        return if (raw == null) {
            ensureDefaultLoaded()
        } else {
            parseStored(raw)
        }
    }

    override suspend fun getBusinessDaySettings(): BusinessDaySettingsLoadResult {
        if (appSettingsDao.get() == null) {
            when (ensureDefaultLoaded()) {
                is NumberingModeLoadResult.Loaded -> Unit
                else -> return BusinessDaySettingsLoadResult.PersistenceFailure
            }
        }
        val entity = appSettingsDao.get()
            ?: return BusinessDaySettingsLoadResult.PersistenceFailure
        return BusinessDaySettingsLoadResult.Loaded(
            BusinessDaySettings(
                timezoneId = entity.timezoneId,
                businessDayStartMinutes = entity.businessDayStartMinutes,
            ),
        )
    }

    override suspend fun updateNumberingMode(mode: NumberingMode): UpdateNumberingModeResult {
        var existing = appSettingsDao.get()
        if (existing == null) {
            val created = defaultSettings(
                numberingMode = mode,
                updatedAt = clockProvider.now().toEpochMilli(),
            )
            val inserted = appSettingsDao.insert(created)
            if (inserted != -1L) {
                return UpdateNumberingModeResult.Updated
            }
            existing = appSettingsDao.get()
                ?: return UpdateNumberingModeResult.PersistenceFailure
        }

        val storedName = appSettingsDao.getNumberingModeName()
            ?: return UpdateNumberingModeResult.PersistenceFailure
        if (NumberingModeParser.parseOrNull(storedName) == null) {
            return UpdateNumberingModeResult.InvalidStoredMode
        }

        val updatedRows = appSettingsDao.update(
            existing.copy(
                numberingMode = mode,
                updatedAt = clockProvider.now().toEpochMilli(),
            ),
        )
        return if (updatedRows == 1) {
            UpdateNumberingModeResult.Updated
        } else {
            UpdateNumberingModeResult.PersistenceFailure
        }
    }

    private suspend fun ensureDefaultLoaded(): NumberingModeLoadResult {
        val created = defaultSettings(
            numberingMode = NumberingMode.SEQUENTIAL,
            updatedAt = clockProvider.now().toEpochMilli(),
        )
        appSettingsDao.insert(created)
        val raw = appSettingsDao.getNumberingModeName()
            ?: return NumberingModeLoadResult.PersistenceFailure
        return parseStored(raw)
    }

    private fun parseStored(raw: String): NumberingModeLoadResult {
        val mode = NumberingModeParser.parseOrNull(raw)
            ?: return NumberingModeLoadResult.InvalidStoredMode
        return NumberingModeLoadResult.Loaded(mode)
    }

    private fun defaultSettings(
        numberingMode: NumberingMode,
        updatedAt: Long,
    ): AppSettingsEntity = AppSettingsEntity(
        numberingMode = numberingMode,
        updatedAt = updatedAt,
    )
}
