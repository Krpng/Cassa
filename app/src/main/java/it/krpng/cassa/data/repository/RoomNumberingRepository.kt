package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.NumberingStateRow
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
 *
 * Reads go through [NumberingStateRow] so corrupted DB values are validated here
 * into typed InvalidState instead of crashing [NumberingStateEntity] construction.
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

        val state = when (val loaded = getOrCreateState(businessDate, updatedAt)) {
            is LoadedNumberingState.Ready -> loaded.state
            LoadedNumberingState.InvalidState ->
                return AllocateSequentialResult.InvalidState
            LoadedNumberingState.PersistenceFailure ->
                return AllocateSequentialResult.PersistenceFailure
        }

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

        val existing = when (val loaded = getOrCreateState(businessDate, updatedAt)) {
            is LoadedNumberingState.Ready -> loaded.state
            LoadedNumberingState.InvalidState ->
                return AllocateRandomResult.InvalidState
            LoadedNumberingState.PersistenceFailure ->
                return AllocateRandomResult.PersistenceFailure
        }

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
    ): LoadedNumberingState {
        numberingStateDao.getRaw(businessDate)?.let { row ->
            return when (val validated = validatePersistedRow(row)) {
                is RowValidation.Valid -> LoadedNumberingState.Ready(validated.entity)
                RowValidation.Invalid -> LoadedNumberingState.InvalidState
            }
        }

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
            LoadedNumberingState.Ready(created)
        } else {
            val raced = numberingStateDao.getRaw(businessDate)
                ?: return LoadedNumberingState.PersistenceFailure
            when (val validated = validatePersistedRow(raced)) {
                is RowValidation.Valid -> LoadedNumberingState.Ready(validated.entity)
                RowValidation.Invalid -> LoadedNumberingState.InvalidState
            }
        }
    }

    /**
     * Mirrors [NumberingStateEntity] invariants without auto-repair.
     * Invalid rows stay as-is in SQLite; callers return typed InvalidState.
     */
    private fun validatePersistedRow(row: NumberingStateRow): RowValidation {
        if (row.nextSequentialNumber <= 0L) {
            return RowValidation.Invalid
        }
        if (row.randomCycle <= 0) {
            return RowValidation.Invalid
        }
        if (row.randomPosition < 0) {
            return RowValidation.Invalid
        }
        return RowValidation.Valid(
            NumberingStateEntity(
                businessDate = row.businessDate,
                nextSequentialNumber = row.nextSequentialNumber,
                randomCycle = row.randomCycle,
                randomSeed = row.randomSeed,
                randomSeedInitialized = row.randomSeedInitialized,
                randomPosition = row.randomPosition,
                updatedAt = row.updatedAt,
            ),
        )
    }

    private sealed interface LoadedNumberingState {
        data class Ready(val state: NumberingStateEntity) : LoadedNumberingState

        data object InvalidState : LoadedNumberingState

        data object PersistenceFailure : LoadedNumberingState
    }

    private sealed interface RowValidation {
        data class Valid(val entity: NumberingStateEntity) : RowValidation

        data object Invalid : RowValidation
    }

    private companion object {
        const val FIRST_RANDOM_CYCLE = 1
        const val FIRST_RANDOM_POSITION = 0
        const val LAST_RANDOM_POSITION = 2599
    }
}
