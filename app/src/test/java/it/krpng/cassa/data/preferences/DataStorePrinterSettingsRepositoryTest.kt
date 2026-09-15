package it.krpng.cassa.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import it.krpng.cassa.domain.model.PricePrintMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * On Windows JVM unit tests, Preference DataStore often fails atomic rename when the same
 * file receives multiple consecutive writes. Each case below performs at most one write.
 */
class DataStorePrinterSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing value get and observe default to DETAILED`() =
        withRepository { repository, _ ->
            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    @Test
    fun `update TOTAL_ONLY then get and observe TOTAL_ONLY`() =
        withRepository { repository, _ ->
            repository.updatePricePrintMode(PricePrintMode.TOTAL_ONLY)

            assertEquals(PricePrintMode.TOTAL_ONLY, repository.getPricePrintMode())
            assertEquals(PricePrintMode.TOTAL_ONLY, repository.observePricePrintMode().first())
        }

    @Test
    fun `update DETAILED persists DETAILED`() =
        withRepository { repository, _ ->
            repository.updatePricePrintMode(PricePrintMode.DETAILED)

            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    @Test
    fun `corrupt stored string falls back to DETAILED without crashing`() =
        withRepository { repository, dataStore ->
            dataStore.edit { prefs ->
                prefs[PrinterPreferences.PRICE_PRINT_MODE] = "NOT_A_REAL_MODE"
            }

            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    private fun withRepository(
        block: suspend (
            DataStorePrinterSettingsRepository,
            DataStore<Preferences>,
        ) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val preferencesFile =
            File(
                temporaryFolder.root,
                "printer_prefs_${UUID.randomUUID()}.preferences_pb",
            )
        try {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { preferencesFile },
                )
            block(DataStorePrinterSettingsRepository(dataStore), dataStore)
        } finally {
            scope.cancel()
        }
    }
}