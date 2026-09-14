package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.domain.numbering.SequentialAllocationResult
import it.krpng.cassa.domain.numbering.SequentialNumberAllocator
import it.krpng.cassa.domain.repository.AllocateSequentialResult
import it.krpng.cassa.domain.repository.NumberingRepository
import javax.inject.Inject

/**
 * Persists sequential numbering via [numbering_state].
 *
 * No [it.krpng.cassa.data.database.DatabaseTransactionRunner]: callers must wrap
 * AcceptOrder mutations in a single Room transaction that includes this allocate.
 *
 * RANDOM seed generation is out of scope (NUM-002/003). New SEQUENTIAL rows use
 * `randomSeedInitialized = false` with `randomSeed = 0L` as non-semantic NOT NULL filler only.
 */
class RoomNumberingRepository @Inject constructor(
    private val numberingStateDao: NumberingStateDao,
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

    private suspend fun getOrCreateState(
        businessDate: String,
        updatedAt: Long,
    ): NumberingStateEntity? {
        numberingStateDao.get(businessDate)?.let { return it }

        val created = NumberingStateEntity(
            businessDate = businessDate,
            nextSequentialNumber = 1L,
            randomCycle = 1,
            randomSeed = 0L, // non-semantic filler while randomSeedInitialized=false
            randomSeedInitialized = false,
            randomPosition = 0,
            updatedAt = updatedAt,
        )
        val insertedRowId = numberingStateDao.insert(created)
        return if (insertedRowId != -1L) {
            created
        } else {
            numberingStateDao.get(businessDate)
        }
    }
}
