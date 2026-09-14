package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.domain.repository.AllocateSequentialResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NUM-T005 (concurrent AcceptOrder → one number) is deferred to ACCEPT-003/004.
 * NUM-T018 / NUM-T024 (first RANDOM init + restart seed) deferred to NUM-003.
 * Covered here: sequential allocate has no autonomous transaction; D-042 flag semantics for SEQUENTIAL.
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
        assertEquals(1, parameterTypes.size)
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
        val repository = repository(dao)

        repository.allocateNextSequential(BUSINESS_DATE_A, 1L)
        repository.allocateNextSequential(BUSINESS_DATE_A, 2L)
        repository.allocateNextSequential(BUSINESS_DATE_A, 3L)

        val state = dao.get(BUSINESS_DATE_A)!!
        assertFalse(state.randomSeedInitialized)
        assertEquals(0L, state.randomSeed)
        assertEquals(4L, state.nextSequentialNumber)
    }

    @Test
    fun `NUM-T022 initialized false leaves physical randomSeed non-authoritative for SEQUENTIAL path`() =
        runTest {
            val dao = FakeNumberingStateDao(
                initial = NumberingStateEntity(
                    businessDate = BUSINESS_DATE_A,
                    nextSequentialNumber = 1L,
                    randomCycle = 1,
                    randomSeed = 7_777L,
                    randomSeedInitialized = false,
                    randomPosition = 0,
                    updatedAt = 1L,
                ),
            )

            repository(dao).allocateNextSequential(BUSINESS_DATE_A, 2L)
            val state = dao.get(BUSINESS_DATE_A)!!

            // SEQUENTIAL path does not interpret or rewrite filler/non-initialized seed.
            assertFalse(state.randomSeedInitialized)
            assertEquals(7_777L, state.randomSeed)
            assertEquals(2L, state.nextSequentialNumber)
        }

    @Test
    fun `NUM-T023 seed 0 with initialized true remains distinguishable and preserved`() = runTest {
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
    ): RoomNumberingRepository = RoomNumberingRepository(
        numberingStateDao = dao,
    )

    private class FakeNumberingStateDao(
        initial: NumberingStateEntity? = null,
    ) : NumberingStateDao {
        private val states = mutableMapOf<String, NumberingStateEntity>()

        init {
            if (initial != null) {
                states[initial.businessDate] = initial
            }
        }

        override suspend fun get(businessDate: String): NumberingStateEntity? =
            states[businessDate]

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
