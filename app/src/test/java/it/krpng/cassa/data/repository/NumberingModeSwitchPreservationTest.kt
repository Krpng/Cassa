package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.FakeClockProvider
import it.krpng.cassa.data.database.dao.AppSettingsDao
import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.AppSettingsEntity
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.NumberingStateRow
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.repository.AllocateRandomResult
import it.krpng.cassa.domain.repository.AllocateSequentialResult
import it.krpng.cassa.domain.repository.NumberingModeLoadResult
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NUM-T006 / NUM-T015 / NUM-T028 / NUM-T029 / NUM-T031 — mode switch preserves
 * independent numbering states and consumes no numbers.
 *
 * NUM-T032 / NUM-T033 — covered by AcceptOrderRepositoryTest (ACCEPT-003).
 */
class NumberingModeSwitchPreservationTest {
    private val clock = FakeClockProvider(Instant.parse("2026-09-14T16:00:00Z"))

    @Test
    fun `NUM-T006 mode switch preserves sequential resume and random resume`() = runTest {
        val numberingDao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(FIXED_SEED)
        val numbering = RoomNumberingRepository(numberingDao, seedProvider)
        val settings = RoomSettingsRepository(FakeAppSettingsDao(), clock)

        assertEquals(
            NumberingModeLoadResult.Loaded(NumberingMode.SEQUENTIAL),
            settings.getNumberingMode(),
        )

        assertEquals(
            "001",
            (numbering.allocateNextSequential(BUSINESS_DATE, 1L) as AllocateSequentialResult.Allocated)
                .displayNumber,
        )
        assertEquals(
            "002",
            (numbering.allocateNextSequential(BUSINESS_DATE, 2L) as AllocateSequentialResult.Allocated)
                .displayNumber,
        )

        assertSame(
            UpdateNumberingModeResult.Updated,
            settings.updateNumberingMode(NumberingMode.RANDOM),
        )
        val firstRandom = numbering.allocateNextRandom(BUSINESS_DATE, 3L)
        assertTrue(firstRandom is AllocateRandomResult.Allocated)
        val afterFirstRandom = snapshot(numberingDao)

        assertSame(
            UpdateNumberingModeResult.Updated,
            settings.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )
        assertEquals(
            "003",
            (numbering.allocateNextSequential(BUSINESS_DATE, 4L) as AllocateSequentialResult.Allocated)
                .displayNumber,
        )
        val afterSequentialResume = snapshot(numberingDao)
        assertEquals(afterFirstRandom.randomSeed, afterSequentialResume.randomSeed)
        assertEquals(afterFirstRandom.randomSeedInitialized, afterSequentialResume.randomSeedInitialized)
        assertEquals(afterFirstRandom.randomCycle, afterSequentialResume.randomCycle)
        assertEquals(afterFirstRandom.randomPosition, afterSequentialResume.randomPosition)

        assertSame(
            UpdateNumberingModeResult.Updated,
            settings.updateNumberingMode(NumberingMode.RANDOM),
        )
        val beforeSecondRandom = snapshot(numberingDao)
        assertEquals(afterFirstRandom.randomSeed, beforeSecondRandom.randomSeed)
        assertEquals(afterFirstRandom.randomSeedInitialized, beforeSecondRandom.randomSeedInitialized)
        assertEquals(afterFirstRandom.randomCycle, beforeSecondRandom.randomCycle)
        assertEquals(afterFirstRandom.randomPosition, beforeSecondRandom.randomPosition)

        val secondRandom = numbering.allocateNextRandom(BUSINESS_DATE, 5L)
        assertTrue(secondRandom is AllocateRandomResult.Allocated)
        assertEquals(1, seedProvider.callCount)
    }

    @Test
    fun `NUM-T015 and NUM-T029 full RANDOM state preserved across R-S-R`() = runTest {
        val numberingDao = FakeNumberingStateDao()
        val numbering = RoomNumberingRepository(numberingDao, CountingSeedProvider(FIXED_SEED))
        val settings = RoomSettingsRepository(FakeAppSettingsDao(), clock)

        settings.updateNumberingMode(NumberingMode.RANDOM)
        numbering.allocateNextRandom(BUSINESS_DATE, 1L)
        numbering.allocateNextRandom(BUSINESS_DATE, 2L)
        val before = snapshot(numberingDao)

        settings.updateNumberingMode(NumberingMode.SEQUENTIAL)
        settings.updateNumberingMode(NumberingMode.RANDOM)
        val after = snapshot(numberingDao)

        assertEquals(before.randomSeed, after.randomSeed)
        assertEquals(before.randomSeedInitialized, after.randomSeedInitialized)
        assertEquals(before.randomCycle, after.randomCycle)
        assertEquals(before.randomPosition, after.randomPosition)
        assertTrue(after.randomSeedInitialized)
    }

    @Test
    fun `NUM-T028 full sequential state preserved across S-R-S`() = runTest {
        val numberingDao = FakeNumberingStateDao()
        val numbering = RoomNumberingRepository(numberingDao, CountingSeedProvider(FIXED_SEED))
        val settings = RoomSettingsRepository(FakeAppSettingsDao(), clock)

        numbering.allocateNextSequential(BUSINESS_DATE, 1L)
        numbering.allocateNextSequential(BUSINESS_DATE, 2L)
        val before = snapshot(numberingDao)

        settings.updateNumberingMode(NumberingMode.RANDOM)
        settings.updateNumberingMode(NumberingMode.SEQUENTIAL)
        val after = snapshot(numberingDao)

        assertEquals(before.nextSequentialNumber, after.nextSequentialNumber)
        assertEquals(3L, after.nextSequentialNumber)
        assertEquals(
            "003",
            (numbering.allocateNextSequential(BUSINESS_DATE, 3L) as AllocateSequentialResult.Allocated)
                .displayNumber,
        )
    }

    @Test
    fun `NUM-T031 settings mode change consumes no number and does not call seed provider`() =
        runTest {
            val numberingDao = FakeNumberingStateDao(
                initial = NumberingStateEntity(
                    businessDate = BUSINESS_DATE,
                    nextSequentialNumber = 7L,
                    randomCycle = 2,
                    randomSeed = FIXED_SEED,
                    randomSeedInitialized = true,
                    randomPosition = 11,
                    updatedAt = 1L,
                ),
            )
            val seedProvider = CountingSeedProvider(99L)
            RoomNumberingRepository(numberingDao, seedProvider)
            val settings = RoomSettingsRepository(FakeAppSettingsDao(), clock)
            val before = snapshot(numberingDao)

            settings.updateNumberingMode(NumberingMode.RANDOM)
            settings.updateNumberingMode(NumberingMode.SEQUENTIAL)

            assertEquals(before, snapshot(numberingDao))
            assertEquals(0, seedProvider.callCount)
            assertFalse(numberingDao.mutated)
        }

    private fun snapshot(dao: FakeNumberingStateDao): NumberingStateEntity =
        requireNotNull(dao.getBlocking(BUSINESS_DATE))

    private class CountingSeedProvider(
        private val fixed: Long,
    ) : NumberingSeedProvider {
        var callCount: Int = 0
            private set

        override fun nextSeed(): Long {
            callCount += 1
            return fixed
        }
    }

    private class FakeAppSettingsDao(
        initial: AppSettingsEntity? = null,
    ) : AppSettingsDao {
        private val row = MutableStateFlow(initial)

        override suspend fun get(id: Int): AppSettingsEntity? =
            row.value?.takeIf { it.id == id }

        override fun observe(id: Int): Flow<AppSettingsEntity?> =
            row.map { entity -> entity?.takeIf { it.id == id } }

        override suspend fun getNumberingModeName(id: Int): String? =
            get(id)?.numberingMode?.name

        override fun observeNumberingModeName(id: Int): Flow<String?> =
            row.map { entity -> entity?.takeIf { it.id == id }?.numberingMode?.name }

        override suspend fun insert(settings: AppSettingsEntity): Long {
            if (row.value != null) return -1L
            row.value = settings
            return 1L
        }

        override suspend fun update(settings: AppSettingsEntity): Int {
            if (row.value == null) return 0
            row.value = settings
            return 1
        }
    }

    private class FakeNumberingStateDao(
        initial: NumberingStateEntity? = null,
    ) : NumberingStateDao {
        private val states = mutableMapOf<String, NumberingStateEntity>()
        var mutated: Boolean = false
            private set

        init {
            if (initial != null) {
                states[initial.businessDate] = initial
            }
        }

        fun getBlocking(businessDate: String): NumberingStateEntity? = states[businessDate]

        override suspend fun getRaw(businessDate: String): NumberingStateRow? =
            states[businessDate]?.toRow()

        override suspend fun insert(state: NumberingStateEntity): Long {
            if (states.containsKey(state.businessDate)) return -1L
            states[state.businessDate] = state
            mutated = true
            return 1L
        }

        override suspend fun update(state: NumberingStateEntity): Int {
            if (!states.containsKey(state.businessDate)) return 0
            states[state.businessDate] = state
            mutated = true
            return 1
        }

        private fun NumberingStateEntity.toRow(): NumberingStateRow = NumberingStateRow(
            businessDate = businessDate,
            nextSequentialNumber = nextSequentialNumber,
            randomCycle = randomCycle,
            randomSeed = randomSeed,
            randomSeedInitialized = randomSeedInitialized,
            randomPosition = randomPosition,
            updatedAt = updatedAt,
        )
    }

    private companion object {
        const val BUSINESS_DATE = "2026-09-14"
        const val FIXED_SEED = 42L
    }
}
