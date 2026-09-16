package it.krpng.cassa.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import it.krpng.cassa.domain.model.PricePrintMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * File-backed Preference DataStore on Windows JVM often fails the second atomic rename in a
 * single test (antivirus/file-lock). Single-write + file reopen(read) stay on disk; multi-write
 * contract cases use an in-memory [DataStore] that still exercises the real repository.
 */
class DataStorePrinterSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing value get and observe default to DETAILED`() =
        withFileRepository { repository, _ ->
            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    @Test
    fun `update TOTAL_ONLY then get and observe TOTAL_ONLY`() =
        withFileRepository { repository, _ ->
            repository.updatePricePrintMode(PricePrintMode.TOTAL_ONLY)

            assertEquals(PricePrintMode.TOTAL_ONLY, repository.getPricePrintMode())
            assertEquals(PricePrintMode.TOTAL_ONLY, repository.observePricePrintMode().first())
        }

    @Test
    fun `update DETAILED persists DETAILED`() =
        withFileRepository { repository, _ ->
            repository.updatePricePrintMode(PricePrintMode.DETAILED)

            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    @Test
    fun `corrupt stored string falls back to DETAILED without crashing`() =
        withFileRepository { repository, dataStore ->
            dataStore.edit { prefs ->
                prefs[PrinterPreferences.PRICE_PRINT_MODE] = "NOT_A_REAL_MODE"
            }

            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals(PricePrintMode.DETAILED, repository.observePricePrintMode().first())
        }

    @Test
    fun `default selected printer is null`() =
        withFileRepository { repository, _ ->
            assertNull(repository.getSelectedPrinterId())
            assertNull(repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `set selected printer id returns exact same string`() =
        withFileRepository { repository, _ ->
            val id = "AA:BB:CC:DD:EE:FF"
            repository.setSelectedPrinterId(id)

            assertEquals(id, repository.getSelectedPrinterId())
            assertEquals(id, repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `set A then B returns B`() =
        withInMemoryRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:AA:AA:AA:AA:AA")
            assertEquals("AA:AA:AA:AA:AA:AA", repository.getSelectedPrinterId())

            repository.setSelectedPrinterId("BB:BB:BB:BB:BB:BB")

            assertEquals("BB:BB:BB:BB:BB:BB", repository.getSelectedPrinterId())
        }

    @Test
    fun `clear selected printer returns null`() =
        withInMemoryRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())

            repository.clearSelectedPrinterId()

            assertNull(repository.getSelectedPrinterId())
            assertNull(repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `clear when already empty remains null`() =
        withFileRepository { repository, _ ->
            repository.clearSelectedPrinterId()

            assertNull(repository.getSelectedPrinterId())
            assertNull(repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `set same id twice is semantically stable`() =
        withInMemoryRepository { repository, _ ->
            val id = "11:22:33:44:55:66"
            repository.setSelectedPrinterId(id)
            assertEquals(id, repository.getSelectedPrinterId())

            repository.setSelectedPrinterId(id)

            assertEquals(id, repository.getSelectedPrinterId())
            assertEquals(id, repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `blank id is rejected and leaves selection unconfigured`() =
        withFileRepository { repository, _ ->
            try {
                repository.setSelectedPrinterId("")
                throw AssertionError("expected IllegalArgumentException")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("nonblank"))
            }
            assertNull(repository.getSelectedPrinterId())
        }

    @Test
    fun `whitespace-only id is rejected and leaves selection unconfigured`() =
        withFileRepository { repository, _ ->
            try {
                repository.setSelectedPrinterId("   ")
                throw AssertionError("expected IllegalArgumentException")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("nonblank"))
            }
            assertNull(repository.getSelectedPrinterId())
        }

    @Test
    fun `blank id rejection does not clear existing selection`() =
        withInMemoryRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())

            try {
                repository.setSelectedPrinterId("")
                throw AssertionError("expected IllegalArgumentException")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("nonblank"))
            }
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())
        }

    @Test
    fun `whitespace-only id rejection does not clear existing selection`() =
        withInMemoryRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())

            try {
                repository.setSelectedPrinterId("   ")
                throw AssertionError("expected IllegalArgumentException")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("nonblank"))
            }
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())
        }

    @Test
    fun `seeded blank selected printer is exposed as null`() =
        withFileRepository { repository, dataStore ->
            dataStore.edit { prefs ->
                prefs[PrinterPreferences.SELECTED_PRINTER_ID] = "   "
            }

            assertNull(repository.getSelectedPrinterId())
            assertNull(repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `nonblank id is stored exactly without trim or case normalization`() =
        withFileRepository { repository, _ ->
            val id = " aa:bb:CC:dd:ee:ff "
            repository.setSelectedPrinterId(id)

            assertEquals(id, repository.getSelectedPrinterId())
            assertEquals(id, repository.observeSelectedPrinterId().first())
        }

    @Test
    fun `setting selected printer leaves PricePrintMode DETAILED by default`() =
        withFileRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")

            assertEquals(PricePrintMode.DETAILED, repository.getPricePrintMode())
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())
        }

    @Test
    fun `changing PricePrintMode leaves selected printer unchanged`() =
        withInMemoryRepository { repository, _ ->
            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())

            repository.updatePricePrintMode(PricePrintMode.TOTAL_ONLY)

            assertEquals(PricePrintMode.TOTAL_ONLY, repository.getPricePrintMode())
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())
        }

    @Test
    fun `changing selected printer leaves existing PricePrintMode unchanged`() =
        withInMemoryRepository { repository, _ ->
            repository.updatePricePrintMode(PricePrintMode.TOTAL_ONLY)
            assertEquals(PricePrintMode.TOTAL_ONLY, repository.getPricePrintMode())

            repository.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")

            assertEquals(PricePrintMode.TOTAL_ONLY, repository.getPricePrintMode())
            assertEquals("AA:BB:CC:DD:EE:FF", repository.getSelectedPrinterId())
        }

    @Test
    fun `selected printer survives new repository instance on same DataStore file`() {
        val preferencesFile =
            File(
                temporaryFolder.root,
                "printer_prefs_recreate_${UUID.randomUUID()}.preferences_pb",
            )
        runBlocking {
            val writeScope = CoroutineScope(Dispatchers.IO + Job())
            try {
                val writeStore =
                    PreferenceDataStoreFactory.create(
                        scope = writeScope,
                        produceFile = { preferencesFile },
                    )
                val writer = DataStorePrinterSettingsRepository(writeStore)
                writer.setSelectedPrinterId("AA:BB:CC:DD:EE:FF")
                assertEquals("AA:BB:CC:DD:EE:FF", writer.getSelectedPrinterId())
            } finally {
                writeScope.cancel()
            }

            val readScope = CoroutineScope(Dispatchers.IO + Job())
            try {
                val readStore =
                    PreferenceDataStoreFactory.create(
                        scope = readScope,
                        produceFile = { preferencesFile },
                    )
                val reader = DataStorePrinterSettingsRepository(readStore)
                assertEquals("AA:BB:CC:DD:EE:FF", reader.getSelectedPrinterId())
                assertEquals(PricePrintMode.DETAILED, reader.getPricePrintMode())
            } finally {
                readScope.cancel()
            }
        }
    }

    private fun withFileRepository(
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

    private fun withInMemoryRepository(
        block: suspend (
            DataStorePrinterSettingsRepository,
            DataStore<Preferences>,
        ) -> Unit,
    ) = runBlocking {
        val dataStore = InMemoryPreferencesDataStore()
        block(DataStorePrinterSettingsRepository(dataStore), dataStore)
    }

    /**
     * Minimal Preferences [DataStore] for multi-write JVM cases where file-backed rename fails
     * on Windows. Production path remains Preference DataStore via [withFileRepository].
     */
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock {
                val updated = transform(state.value)
                state.value = updated
                updated
            }
    }
}
