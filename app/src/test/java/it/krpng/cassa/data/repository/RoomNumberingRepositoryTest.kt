package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.NumberingStateRow
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.numbering.RandomCodeFormatter
import it.krpng.cassa.domain.numbering.StableRandomPermutation
import it.krpng.cassa.domain.repository.AllocateRandomResult
import it.krpng.cassa.domain.repository.AllocateSequentialResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NUM-T005 (concurrent AcceptOrder → one number) deferred to ACCEPT-003/004.
 * NUM-T015 covered by NumberingModeSwitchPreservationTest (NUM-005).
 * NUM-T019 (preview non-consume) deferred to ACCEPT preview.
 */
class RoomNumberingRepositoryTest {
    @Test
    fun `NUM-T001 first sequential number is 001`() = runTest {
        val repository = repository()

        val result = repository.allocateNextSequential(
            businessDate = BUSINESS_DATE_A,
            updatedAt = 1_000L,
        )

        assertTrue(result is AllocateSequentialResult.Allocated)
        val allocated = result as AllocateSequentialResult.Allocated
        assertEquals("001", allocated.displayNumber)
        assertEquals(1L, allocated.allocatedNumber)
    }

    @Test
    fun `NUM-T002 sequential progression is 001 002 003`() = runTest {
        val repository = repository()

        val first = repository.allocateNextSequential(BUSINESS_DATE_A, 1L)
        val second = repository.allocateNextSequential(BUSINESS_DATE_A, 2L)
        val third = repository.allocateNextSequential(BUSINESS_DATE_A, 3L)

        assertEquals(
            listOf("001", "002", "003"),
            listOf(first, second, third).map {
                (it as AllocateSequentialResult.Allocated).displayNumber
            },
        )
    }

    @Test
    fun `NUM-T003 new businessDate resets sequential to 001`() = runTest {
        val dao = FakeNumberingStateDao()
        val repository = repository(dao)

        repeat(3) { index ->
            repository.allocateNextSequential(BUSINESS_DATE_A, updatedAt = index.toLong())
        }

        val onNewBusinessDate = repository.allocateNextSequential(
            businessDate = BUSINESS_DATE_B,
            updatedAt = 10L,
        )
        val continueA = repository.allocateNextSequential(
            businessDate = BUSINESS_DATE_A,
            updatedAt = 11L,
        )

        assertEquals(
            "001",
            (onNewBusinessDate as AllocateSequentialResult.Allocated).displayNumber,
        )
        assertEquals(
            "004",
            (continueA as AllocateSequentialResult.Allocated).displayNumber,
        )
        assertEquals(2L, dao.get(BUSINESS_DATE_B)!!.nextSequentialNumber)
        assertEquals(5L, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
    }

    @Test
    fun `NUM-T004 continues beyond 999 without wrapping`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 998L,
                randomCycle = 1,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = false,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )
        val repository = repository(dao)

        val values = listOf(
            repository.allocateNextSequential(BUSINESS_DATE_A, 2L),
            repository.allocateNextSequential(BUSINESS_DATE_A, 3L),
            repository.allocateNextSequential(BUSINESS_DATE_A, 4L),
            repository.allocateNextSequential(BUSINESS_DATE_A, 5L),
        ).map { (it as AllocateSequentialResult.Allocated).displayNumber }

        assertEquals(listOf("998", "999", "1000", "1001"), values)
        assertEquals(1_002L, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
    }

    @Test
    fun `NUM-T005 primitive coverage — allocate has no autonomous transaction runner`() {
        val parameterTypes = RoomNumberingRepository::class.java.constructors
            .single()
            .parameterTypes
            .map { it.name }

        assertTrue(
            "allocate must stay callable inside AcceptOrder's outer Room transaction",
            parameterTypes.none { it.contains("DatabaseTransactionRunner") },
        )
        assertTrue(parameterTypes.any { it.contains("NumberingStateDao") })
        assertTrue(parameterTypes.any { it.contains("NumberingSeedProvider") })
        assertEquals(2, parameterTypes.size)
    }

    @Test
    fun `NUM-T006 sequential resumes after random fields change without reset`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 1L,
                randomCycle = 1,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = true,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )
        val repository = repository(dao)

        repository.allocateNextSequential(BUSINESS_DATE_A, 1L)
        repository.allocateNextSequential(BUSINESS_DATE_A, 2L)

        val afterSequential = dao.get(BUSINESS_DATE_A)!!
        assertEquals(3L, afterSequential.nextSequentialNumber)
        assertEquals(FIXED_SEED, afterSequential.randomSeed)
        assertTrue(afterSequential.randomSeedInitialized)

        dao.update(
            afterSequential.copy(
                randomCycle = 2,
                randomPosition = 100,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = true,
                updatedAt = 50L,
            ),
        )

        val afterModeSwitch = repository.allocateNextSequential(BUSINESS_DATE_A, 51L)
        val state = dao.get(BUSINESS_DATE_A)!!

        assertEquals(
            "003",
            (afterModeSwitch as AllocateSequentialResult.Allocated).displayNumber,
        )
        assertEquals(4L, state.nextSequentialNumber)
        assertEquals(2, state.randomCycle)
        assertEquals(100, state.randomPosition)
        assertEquals(FIXED_SEED, state.randomSeed)
        assertTrue(state.randomSeedInitialized)
        assertNotEquals(0, state.randomPosition)
    }

    @Test
    fun `NUM-T011 first 2600 random allocations are distinct in cycle 1`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(FIXED_SEED)
        val repository = repository(dao, seedProvider)

        val codes = mutableListOf<String>()
        repeat(StableRandomPermutation.SIZE) { index ->
            val result = repository.allocateNextRandom(BUSINESS_DATE_A, updatedAt = index.toLong())
            val allocated = result as AllocateRandomResult.Allocated
            assertEquals(1, allocated.randomCycle)
            codes += allocated.displayNumber
        }

        assertEquals(StableRandomPermutation.SIZE, codes.size)
        assertEquals(StableRandomPermutation.SIZE, codes.toSet().size)
        codes.forEach { code ->
            assertTrue(Regex("^[A-Z][0-9]{2}$").matches(code))
        }

        val state = dao.get(BUSINESS_DATE_A)!!
        assertEquals(2, state.randomCycle)
        assertEquals(0, state.randomPosition)
        assertEquals(1, seedProvider.callCount)
    }

    @Test
    fun `NUM-T012 allocation 2601 uses cycle 2 and advances position`() = runTest {
        val dao = FakeNumberingStateDao()
        val repository = repository(dao, CountingSeedProvider(FIXED_SEED))

        repeat(StableRandomPermutation.SIZE) { index ->
            repository.allocateNextRandom(BUSINESS_DATE_A, updatedAt = index.toLong())
        }

        val after2600 = dao.get(BUSINESS_DATE_A)!!
        assertEquals(2, after2600.randomCycle)
        assertEquals(0, after2600.randomPosition)

        val expectedIndex = StableRandomPermutation.generate(FIXED_SEED, 2)[0]
        val expectedCode = RandomCodeFormatter.format(expectedIndex)

        val result = repository.allocateNextRandom(BUSINESS_DATE_A, updatedAt = 9_000L)
        val allocated = result as AllocateRandomResult.Allocated

        assertEquals(2, allocated.randomCycle)
        assertEquals(0, allocated.consumedPosition)
        assertEquals(expectedCode, allocated.displayNumber)

        val after2601 = dao.get(BUSINESS_DATE_A)!!
        assertEquals(2, after2601.randomCycle)
        assertEquals(1, after2601.randomPosition)
        assertEquals(FIXED_SEED, after2601.randomSeed)
    }

    @Test
    fun `NUM-T013 new businessDate has independent RANDOM state`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(sequence = longArrayOf(111L, 222L))
        val repository = repository(dao, seedProvider)

        repository.allocateNextRandom(BUSINESS_DATE_A, 1L)
        repository.allocateNextRandom(BUSINESS_DATE_A, 2L)

        val firstOnB = repository.allocateNextRandom(BUSINESS_DATE_B, 3L)
        val continueA = repository.allocateNextRandom(BUSINESS_DATE_A, 4L)

        val stateA = dao.get(BUSINESS_DATE_A)!!
        val stateB = dao.get(BUSINESS_DATE_B)!!

        assertEquals(111L, stateA.randomSeed)
        assertEquals(222L, stateB.randomSeed)
        assertEquals(3, stateA.randomPosition)
        assertEquals(1, stateB.randomPosition)
        assertEquals(1, stateA.randomCycle)
        assertEquals(1, stateB.randomCycle)
        assertEquals(2, seedProvider.callCount)

        assertEquals(
            RandomCodeFormatter.format(StableRandomPermutation.generate(111L, 1)[2]),
            (continueA as AllocateRandomResult.Allocated).displayNumber,
        )
        assertEquals(
            RandomCodeFormatter.format(StableRandomPermutation.generate(222L, 1)[0]),
            (firstOnB as AllocateRandomResult.Allocated).displayNumber,
        )
    }

    @Test
    fun `NUM-T014 restart with new repository instance continues same sequence`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(FIXED_SEED)

        val firstRepository = repository(dao, seedProvider)
        firstRepository.allocateNextRandom(BUSINESS_DATE_A, 1L)
        firstRepository.allocateNextRandom(BUSINESS_DATE_A, 2L)

        val expectedNext = RandomCodeFormatter.format(
            StableRandomPermutation.generate(FIXED_SEED, 1)[2],
        )

        val reloadedProvider = CountingSeedProvider(999_999L)
        val reloaded = repository(dao, reloadedProvider)
        val next = reloaded.allocateNextRandom(BUSINESS_DATE_A, 3L) as AllocateRandomResult.Allocated

        assertEquals(expectedNext, next.displayNumber)
        assertEquals(FIXED_SEED, dao.get(BUSINESS_DATE_A)!!.randomSeed)
        assertEquals(1, seedProvider.callCount)
        assertEquals(0, reloadedProvider.callCount)
        assertEquals(3, dao.get(BUSINESS_DATE_A)!!.randomPosition)
    }

    @Test
    fun `NUM-T018 first RANDOM need creates seed once and keeps it across cycle advance`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(FIXED_SEED)
        val repository = repository(dao, seedProvider)

        repository.allocateNextRandom(BUSINESS_DATE_A, 1L)
        assertEquals(1, seedProvider.callCount)
        assertTrue(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
        assertEquals(FIXED_SEED, dao.get(BUSINESS_DATE_A)!!.randomSeed)

        // Finish remaining 2599 positions of cycle 1 → cycle advances to 2.
        repeat(StableRandomPermutation.SIZE - 1) { index ->
            repository.allocateNextRandom(BUSINESS_DATE_A, updatedAt = (index + 2).toLong())
        }

        val state = dao.get(BUSINESS_DATE_A)!!
        assertEquals(1, seedProvider.callCount)
        assertEquals(FIXED_SEED, state.randomSeed)
        assertTrue(state.randomSeedInitialized)
        assertEquals(2, state.randomCycle)
        assertEquals(0, state.randomPosition)
    }

    @Test
    fun `NUM-T018 seed provider returning 0L is persisted as valid initialized seed`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(0L)
        val repository = repository(dao, seedProvider)

        val first = repository.allocateNextRandom(BUSINESS_DATE_A, 1L) as AllocateRandomResult.Allocated
        val expected = RandomCodeFormatter.format(StableRandomPermutation.generate(0L, 1)[0])

        assertEquals(expected, first.displayNumber)
        assertEquals(0L, dao.get(BUSINESS_DATE_A)!!.randomSeed)
        assertTrue(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
        assertEquals(1, seedProvider.callCount)

        repository.allocateNextRandom(BUSINESS_DATE_A, 2L)
        assertEquals(1, seedProvider.callCount)
        assertEquals(0L, dao.get(BUSINESS_DATE_A)!!.randomSeed)
    }

    @Test
    fun `NUM-T020 sequential-created state has randomSeedInitialized false`() = runTest {
        val dao = FakeNumberingStateDao()
        repository(dao).allocateNextSequential(BUSINESS_DATE_A, 1L)

        val state = dao.get(BUSINESS_DATE_A)!!
        assertFalse(state.randomSeedInitialized)
        assertEquals(0L, state.randomSeed)
        assertEquals(1, state.randomCycle)
        assertEquals(0, state.randomPosition)
        assertEquals(2L, state.nextSequentialNumber)
    }

    @Test
    fun `NUM-T021 sequential allocations never initialize RANDOM seed`() = runTest {
        val dao = FakeNumberingStateDao()
        val seedProvider = CountingSeedProvider(FIXED_SEED)
        val repository = repository(dao, seedProvider)

        repository.allocateNextSequential(BUSINESS_DATE_A, 1L)
        repository.allocateNextSequential(BUSINESS_DATE_A, 2L)
        repository.allocateNextSequential(BUSINESS_DATE_A, 3L)

        val state = dao.get(BUSINESS_DATE_A)!!
        assertFalse(state.randomSeedInitialized)
        assertEquals(0L, state.randomSeed)
        assertEquals(4L, state.nextSequentialNumber)
        assertEquals(0, seedProvider.callCount)
    }

    @Test
    fun `NUM-T022 initialized false ignores physical randomSeed on first RANDOM need`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 5L,
                randomCycle = 1,
                randomSeed = 7_777L,
                randomSeedInitialized = false,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )
        val seedProvider = CountingSeedProvider(FIXED_SEED)
        val repository = repository(dao, seedProvider)

        val allocated = repository.allocateNextRandom(BUSINESS_DATE_A, 2L)
            as AllocateRandomResult.Allocated
        val expected = RandomCodeFormatter.format(StableRandomPermutation.generate(FIXED_SEED, 1)[0])

        assertEquals(expected, allocated.displayNumber)
        assertEquals(FIXED_SEED, dao.get(BUSINESS_DATE_A)!!.randomSeed)
        assertNotEquals(7_777L, dao.get(BUSINESS_DATE_A)!!.randomSeed)
        assertTrue(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
        assertEquals(5L, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
        assertEquals(1, seedProvider.callCount)
    }

    @Test
    fun `NUM-T023 seed 0 with initialized true remains authoritative for RANDOM`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 10L,
                randomCycle = 1,
                randomSeed = 0L,
                randomSeedInitialized = true,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )
        val seedProvider = CountingSeedProvider(42L)
        val repository = repository(dao, seedProvider)

        val allocated = repository.allocateNextRandom(BUSINESS_DATE_A, 2L)
            as AllocateRandomResult.Allocated
        val expected = RandomCodeFormatter.format(StableRandomPermutation.generate(0L, 1)[0])

        assertEquals(expected, allocated.displayNumber)
        assertEquals(0L, dao.get(BUSINESS_DATE_A)!!.randomSeed)
        assertTrue(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
        assertEquals(0, seedProvider.callCount)
        assertEquals(10L, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
    }

    @Test
    fun `NUM-T024 restart after RANDOM initialization keeps seed and does not call provider`() =
        runTest {
            val dao = FakeNumberingStateDao()
            val firstProvider = CountingSeedProvider(FIXED_SEED)
            repository(dao, firstProvider).allocateNextRandom(BUSINESS_DATE_A, 1L)

            val secondProvider = CountingSeedProvider(777L)
            val reloaded = repository(dao, secondProvider)
            reloaded.allocateNextRandom(BUSINESS_DATE_A, 2L)
            reloaded.allocateNextRandom(BUSINESS_DATE_A, 3L)

            val state = dao.get(BUSINESS_DATE_A)!!
            assertTrue(state.randomSeedInitialized)
            assertEquals(FIXED_SEED, state.randomSeed)
            assertEquals(1, firstProvider.callCount)
            assertEquals(0, secondProvider.callCount)
            assertEquals(3, state.randomPosition)
        }

    @Test
    fun `NUM-T023 seed 0 with initialized true remains distinguishable and preserved by sequential`() =
        runTest {
            val dao = FakeNumberingStateDao(
                initial = NumberingStateEntity(
                    businessDate = BUSINESS_DATE_A,
                    nextSequentialNumber = 10L,
                    randomCycle = 2,
                    randomSeed = 0L,
                    randomSeedInitialized = true,
                    randomPosition = 55,
                    updatedAt = 1L,
                ),
            )

            repository(dao).allocateNextSequential(BUSINESS_DATE_A, 2L)
            val state = dao.get(BUSINESS_DATE_A)!!

            assertTrue(state.randomSeedInitialized)
            assertEquals(0L, state.randomSeed)
            assertEquals(2, state.randomCycle)
            assertEquals(55, state.randomPosition)
            assertEquals(11L, state.nextSequentialNumber)
        }

    @Test
    fun `sequential preserves fully initialized RANDOM state fields`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 5L,
                randomCycle = 3,
                randomSeed = 99_001L,
                randomSeedInitialized = true,
                randomPosition = 42,
                updatedAt = 1L,
            ),
        )

        repository(dao).allocateNextSequential(BUSINESS_DATE_A, 2L)
        val state = dao.get(BUSINESS_DATE_A)!!

        assertEquals(6L, state.nextSequentialNumber)
        assertEquals(3, state.randomCycle)
        assertEquals(99_001L, state.randomSeed)
        assertTrue(state.randomSeedInitialized)
        assertEquals(42, state.randomPosition)
    }

    @Test
    fun `RANDOM allocation preserves sequential counter`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 17L,
                randomCycle = 1,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = true,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )

        repository(dao).allocateNextRandom(BUSINESS_DATE_A, 2L)
        assertEquals(17L, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
        assertEquals(1, dao.get(BUSINESS_DATE_A)!!.randomPosition)
    }

    @Test
    fun `invalid randomPosition is rejected without mutation`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 1L,
                randomCycle = 1,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = true,
                randomPosition = 2600,
                updatedAt = 1L,
            ),
        )

        val result = repository(dao).allocateNextRandom(BUSINESS_DATE_A, 2L)
        assertSame(AllocateRandomResult.InvalidState, result)
        assertEquals(2600, dao.get(BUSINESS_DATE_A)!!.randomPosition)
        assertEquals(1L, dao.get(BUSINESS_DATE_A)!!.updatedAt)
    }

    @Test
    fun `randomCycle overflow is typed and does not mutate state`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = 1L,
                randomCycle = Int.MAX_VALUE,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = true,
                randomPosition = 2599,
                updatedAt = 1L,
            ),
        )

        val result = repository(dao).allocateNextRandom(BUSINESS_DATE_A, 2L)
        assertSame(AllocateRandomResult.CycleOverflow, result)
        assertEquals(Int.MAX_VALUE, dao.get(BUSINESS_DATE_A)!!.randomCycle)
        assertEquals(2599, dao.get(BUSINESS_DATE_A)!!.randomPosition)
        assertEquals(1L, dao.get(BUSINESS_DATE_A)!!.updatedAt)
    }

    @Test
    fun `blank businessDate is typed invalid state for RANDOM`() = runTest {
        val result = repository().allocateNextRandom(businessDate = "  ", updatedAt = 1L)
        assertSame(AllocateRandomResult.InvalidState, result)
    }

    @Test
    fun `sequential state survives repository reload from same dao`() = runTest {
        val dao = FakeNumberingStateDao()
        val firstRepository = repository(dao)
        firstRepository.allocateNextSequential(BUSINESS_DATE_A, 1L)
        firstRepository.allocateNextSequential(BUSINESS_DATE_A, 2L)

        val reloaded = repository(dao)
        val next = reloaded.allocateNextSequential(BUSINESS_DATE_A, 3L)

        assertEquals("003", (next as AllocateSequentialResult.Allocated).displayNumber)
        assertFalse(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
    }

    @Test
    fun `blank businessDate is typed invalid state`() = runTest {
        val result = repository().allocateNextSequential(
            businessDate = "  ",
            updatedAt = 1L,
        )
        assertSame(AllocateSequentialResult.InvalidState, result)
    }

    @Test
    fun `counter overflow is typed and does not advance state`() = runTest {
        val dao = FakeNumberingStateDao(
            initial = NumberingStateEntity(
                businessDate = BUSINESS_DATE_A,
                nextSequentialNumber = Long.MAX_VALUE,
                randomCycle = 1,
                randomSeed = FIXED_SEED,
                randomSeedInitialized = false,
                randomPosition = 0,
                updatedAt = 1L,
            ),
        )

        val result = repository(dao).allocateNextSequential(BUSINESS_DATE_A, 2L)

        assertSame(AllocateSequentialResult.CounterOverflow, result)
        assertEquals(Long.MAX_VALUE, dao.get(BUSINESS_DATE_A)!!.nextSequentialNumber)
        assertEquals(1L, dao.get(BUSINESS_DATE_A)!!.updatedAt)
        assertFalse(dao.get(BUSINESS_DATE_A)!!.randomSeedInitialized)
    }

    private fun repository(
        dao: FakeNumberingStateDao = FakeNumberingStateDao(),
        seedProvider: NumberingSeedProvider = CountingSeedProvider(FIXED_SEED),
    ): RoomNumberingRepository = RoomNumberingRepository(
        numberingStateDao = dao,
        numberingSeedProvider = seedProvider,
    )

    private class CountingSeedProvider(
        private val sequence: LongArray,
    ) : NumberingSeedProvider {
        constructor(fixed: Long) : this(longArrayOf(fixed))

        var callCount: Int = 0
            private set

        override fun nextSeed(): Long {
            val index = callCount.coerceAtMost(sequence.lastIndex)
            callCount += 1
            return sequence[index]
        }
    }

    private class FakeNumberingStateDao(
        initial: NumberingStateEntity? = null,
    ) : NumberingStateDao {
        private val states = mutableMapOf<String, NumberingStateEntity>()

        init {
            if (initial != null) {
                states[initial.businessDate] = initial
            }
        }

        /** Test helper for inspecting valid fake state (not a DAO override). */
        fun get(businessDate: String): NumberingStateEntity? = states[businessDate]

        override suspend fun getRaw(businessDate: String): NumberingStateRow? =
            states[businessDate]?.let { entity ->
                NumberingStateRow(
                    businessDate = entity.businessDate,
                    nextSequentialNumber = entity.nextSequentialNumber,
                    randomCycle = entity.randomCycle,
                    randomSeed = entity.randomSeed,
                    randomSeedInitialized = entity.randomSeedInitialized,
                    randomPosition = entity.randomPosition,
                    updatedAt = entity.updatedAt,
                )
            }

        override suspend fun insert(state: NumberingStateEntity): Long {
            if (states.containsKey(state.businessDate)) {
                return -1L
            }
            states[state.businessDate] = state
            return 1L
        }

        override suspend fun update(state: NumberingStateEntity): Int {
            if (!states.containsKey(state.businessDate)) {
                return 0
            }
            states[state.businessDate] = state
            return 1
        }
    }

    private companion object {
        const val BUSINESS_DATE_A = "2026-09-14"
        const val BUSINESS_DATE_B = "2026-09-15"
        const val FIXED_SEED = 42L
    }
}
