package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.FakeClockProvider
import it.krpng.cassa.data.database.dao.AppSettingsDao
import it.krpng.cassa.data.database.entity.AppSettingsEntity
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSettingsRepositoryTest {
    private val clock = FakeClockProvider(Instant.parse("2026-09-14T15:00:00Z"))

    @Test
    fun `NUM-T025 default numbering mode is SEQUENTIAL`() = runTest {
        val dao = FakeAppSettingsDao()
        val repository = RoomSettingsRepository(dao, clock)

        val result = repository.getNumberingMode()

        assertEquals(NumberingModeLoadResult.Loaded(NumberingMode.SEQUENTIAL), result)
        assertEquals(NumberingMode.SEQUENTIAL, dao.get()!!.numberingMode)
        assertEquals(clock.now().toEpochMilli(), dao.get()!!.updatedAt)
    }

    @Test
    fun `NUM-T026 persisted numbering mode survives repository restart`() = runTest {
        val dao = FakeAppSettingsDao()
        RoomSettingsRepository(dao, clock).updateNumberingMode(NumberingMode.RANDOM)

        val reloaded = RoomSettingsRepository(dao, clock).getNumberingMode()

        assertEquals(NumberingModeLoadResult.Loaded(NumberingMode.RANDOM), reloaded)

        RoomSettingsRepository(dao, clock).updateNumberingMode(NumberingMode.SEQUENTIAL)
        assertEquals(
            NumberingModeLoadResult.Loaded(NumberingMode.SEQUENTIAL),
            RoomSettingsRepository(dao, clock).getNumberingMode(),
        )
    }

    @Test
    fun `NUM-T027 mode persistence S to R and R to S`() = runTest {
        val dao = FakeAppSettingsDao()
        val repository = RoomSettingsRepository(dao, clock)

        assertSame(
            UpdateNumberingModeResult.Updated,
            repository.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )
        assertEquals("SEQUENTIAL", dao.getNumberingModeName())

        assertSame(
            UpdateNumberingModeResult.Updated,
            repository.updateNumberingMode(NumberingMode.RANDOM),
        )
        assertEquals("RANDOM", dao.getNumberingModeName())

        assertSame(
            UpdateNumberingModeResult.Updated,
            repository.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )
        assertEquals("SEQUENTIAL", dao.getNumberingModeName())
    }

    @Test
    fun `observe creates default when singleton missing`() = runTest {
        val dao = FakeAppSettingsDao()
        val repository = RoomSettingsRepository(dao, clock)

        val observed = repository.observeNumberingMode().first()

        assertEquals(NumberingModeLoadResult.Loaded(NumberingMode.SEQUENTIAL), observed)
        assertEquals(NumberingMode.SEQUENTIAL, dao.get()!!.numberingMode)
    }

    @Test
    fun `invalid stored mode is typed without rewriting row`() = runTest {
        val dao = FakeAppSettingsDao(
            initial = AppSettingsEntity(
                numberingMode = NumberingMode.SEQUENTIAL,
                updatedAt = 1L,
            ),
        )
        dao.forceRawMode("BROKEN")
        val before = dao.rawSnapshot()

        val repository = RoomSettingsRepository(dao, clock)
        assertSame(NumberingModeLoadResult.InvalidStoredMode, repository.getNumberingMode())
        assertSame(
            UpdateNumberingModeResult.InvalidStoredMode,
            repository.updateNumberingMode(NumberingMode.RANDOM),
        )
        assertEquals(before, dao.rawSnapshot())
    }

    @Test
    fun `future AcceptOrder mode-read API is getNumberingMode`() = runTest {
        val repository = RoomSettingsRepository(FakeAppSettingsDao(), clock)
        repository.updateNumberingMode(NumberingMode.RANDOM)

        val mode = repository.getNumberingMode()

        assertTrue(mode is NumberingModeLoadResult.Loaded)
        assertEquals(NumberingMode.RANDOM, (mode as NumberingModeLoadResult.Loaded).mode)
    }

    @Test
    fun `NUM-T030 settings repository cannot touch orders`() {
        val parameterTypes = RoomSettingsRepository::class.java.constructors
            .single()
            .parameterTypes
            .map { it.simpleName }

        assertEquals(listOf("AppSettingsDao", "ClockProvider"), parameterTypes)
    }

    private class FakeAppSettingsDao(
        initial: AppSettingsEntity? = null,
    ) : AppSettingsDao {
        private val row = MutableStateFlow(initial)
        private var rawOverride: String? = null

        override suspend fun get(id: Int): AppSettingsEntity? =
            row.value?.takeIf { it.id == id }

        override fun observe(id: Int): Flow<AppSettingsEntity?> =
            row.map { entity -> entity?.takeIf { it.id == id } }

        override suspend fun getNumberingModeName(id: Int): String? {
            val entity = get(id) ?: return null
            return rawOverride ?: entity.numberingMode.name
        }

        override fun observeNumberingModeName(id: Int): Flow<String?> =
            row.map { entity ->
                entity
                    ?.takeIf { it.id == id }
                    ?.let { rawOverride ?: it.numberingMode.name }
            }

        override suspend fun insert(settings: AppSettingsEntity): Long {
            if (row.value != null) return -1L
            row.value = settings
            return 1L
        }

        override suspend fun update(settings: AppSettingsEntity): Int {
            if (row.value == null) return 0
            row.value = settings
            rawOverride = null
            return 1
        }

        fun forceRawMode(raw: String) {
            rawOverride = raw
        }

        fun rawSnapshot(): Pair<String?, Long?> {
            val entity = row.value
            return (rawOverride ?: entity?.numberingMode?.name) to entity?.updatedAt
        }
    }
}
