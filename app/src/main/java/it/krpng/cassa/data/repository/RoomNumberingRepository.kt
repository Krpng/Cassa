package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import it.krpng.cassa.domain.numbering.RandomCodeFormatter
import it.krpng.cassa.domain.numbering.SequentialAllocationResult
import it.krpng.cassa.domain.numbering.SequentialNumberAllocator
import it.krpng.cassa.domain.numbering.StableRandomPermutation
import it.krpng.cassa.domain.repository.AllocateRandomResult
import it.krpng.cassa.domain.repository.AllocateSequentialResult
import it.krpng.cassa.domain.repository.NumberingRepository
import javax.inject.Inject

/**
 * Persists sequential and random numbering via [numbering_state].
 *
 * No [it.krpng.cassa.data.database.DatabaseTransactionRunner]: callers must wrap
 * AcceptOrder mutations in a single Room transaction that includes allocate.
 *
 * New rows default to `randomSeedInitialized = false` with `randomSeed = 0L` filler.
 * First RANDOM need generates and persists an authoritative seed once.
 */
class RoomNumberingRepository @Inject constructor(
    private val numberingStateDao: NumberingStateDao,
    private val numberingSeedProvider: NumberingSeedProvider,
) : NumberingRepository {
    override suspend fun allocateNextSequential(
        businessDate: String,
        updatedAt: Long,
    ): AllocateSequentialResult {
        if (businessDate.isBlank()) {
            return AllocateSequentialResult.InvalidState
        }

        val state = getOrCreateState(businessDate, updatedAt)
            ?: return AllocateSequentialResult.PersistenceFailure

        return when (
            val allocation = SequentialNumberAllocator.allocate(state.nextSequentialNumber)
        ) {
            is SequentialAllocationResult.InvalidState ->
                AllocateSequentialResult.InvalidState

            is SequentialAllocationResult.CounterOverflow ->
                AllocateSequentialResult.CounterOverflow

            is SequentialAllocationResult.Allocated -> {
                // Update sequential fields only — preserve all RANDOM state fields as-is.
                val advanced = state.copy(
                    nextSequentialNumber = allocation.nextSequentialNumber,
                    updatedAt = updatedAt,
                )
                val updatedRows = numberingStateDao.update(advanced)
                if (updatedRows != 1) {
                    return AllocateSequentialResult.PersistenceFailure
                }
                AllocateSequentialResult.Allocated(
                    displayNumber = allocation.displayNumber,
                    allocatedNumber = allocation.value,
                )
            }
        }
    }

    override suspend fun allocateNextRandom(
        businessDate: String,
        updatedAt: Long,
    ): AllocateRandomResult {
        if (businessDate.isBlank()) {
            return AllocateRandomResult.InvalidState
        }

        val existing = getOrCreateState(businessDate, updatedAt)
            ?: return AllocateRandomResult.PersistenceFailure

        val state = if (existing.randomSeedInitialized) {
            existing
        } else {
            // First RANDOM need: generate seed once; ignore any physical filler seed.
            existing.copy(
                randomSeed = numberingSeedProvider.nextSeed(),
                randomSeedInitialized = true,
                randomCycle = FIRST_RANDOM_CYCLE,
                randomPosition = FIRST_RANDOM_POSITION,
            )
        }

        if (state.randomCycle <= 0) {
            return AllocateRandomResult.InvalidState
        }
        if (state.randomPosition !in FIRST_RANDOM_POSITION..LAST_RANDOM_POSITION) {
            return AllocateRandomResult.InvalidState
        }

        val permutation = StableRandomPermutation.generate(
            randomSeed = state.randomSeed,
            randomCycle = state.randomCycle,
        )
        val consumedPosition = state.randomPosition
        val allocatedIndex = permutation[consumedPosition]
        val displayNumber = RandomCodeFormatter.format(allocatedIndex)

        val advanced = if (consumedPosition == LAST_RANDOM_POSITION) {
            if (state.randomCycle == Int.MAX_VALUE) {
                return AllocateRandomResult.CycleOverflow
            }
            state.copy(
                randomCycle = state.randomCycle + 1,
                randomPosition = FIRST_RANDOM_POSITION,
                updatedAt = updatedAt,
            )
        } else {
            state.copy(
                randomPosition = consumedPosition + 1,
                updatedAt = updatedAt,
            )
        }

        val updatedRows = numberingStateDao.update(advanced)
        if (updatedRows != 1) {
            return AllocateRandomResult.PersistenceFailure
        }

        return AllocateRandomResult.Allocated(
            displayNumber = displayNumber,
            allocatedIndex = allocatedIndex,
            randomCycle = state.randomCycle,
            consumedPosition = consumedPosition,
        )
    }

    private suspend fun getOrCreateState(
        businessDate: String,
        updatedAt: Long,
    ): NumberingStateEntity? {
        numberingStateDao.get(businessDate)?.let { return it }

        val created = NumberingStateEntity(
            businessDate = businessDate,
            nextSequentialNumber = 1L,
            randomCycle = FIRST_RANDOM_CYCLE,
            randomSeed = 0L, // non-semantic filler while randomSeedInitialized=false
            randomSeedInitialized = false,
            randomPosition = FIRST_RANDOM_POSITION,
            updatedAt = updatedAt,
        )
        val insertedRowId = numberingStateDao.insert(created)
        return if (insertedRowId != -1L) {
            created
        } else {
            numberingStateDao.get(businessDate)
        }
    }

    private companion object {
        const val FIRST_RANDOM_CYCLE = 1
        const val FIRST_RANDOM_POSITION = 0
        const val LAST_RANDOM_POSITION = 2599
    }
}
