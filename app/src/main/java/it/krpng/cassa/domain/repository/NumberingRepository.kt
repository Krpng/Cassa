package it.krpng.cassa.domain.repository

/**
 * Numbering persistence for AcceptOrder.
 *
 * [allocateNextSequential] / [allocateNextRandom] advance and persist numbering state
 * for [businessDate]. They must be invoked inside the caller's single Room transaction
 * (future AcceptOrder). They do **not** open or commit an autonomous transaction.
 *
 * There is intentionally no UI/use-case entry point that consumes numbers alone.
 */
interface NumberingRepository {
    suspend fun allocateNextSequential(
        businessDate: String,
        updatedAt: Long,
    ): AllocateSequentialResult

    suspend fun allocateNextRandom(
        businessDate: String,
        updatedAt: Long,
    ): AllocateRandomResult
}

sealed interface AllocateSequentialResult {
    data class Allocated(
        val displayNumber: String,
        val allocatedNumber: Long,
    ) : AllocateSequentialResult

    data object InvalidState : AllocateSequentialResult

    data object CounterOverflow : AllocateSequentialResult

    data object PersistenceFailure : AllocateSequentialResult
}

sealed interface AllocateRandomResult {
    data class Allocated(
        val displayNumber: String,
        val allocatedIndex: Int,
        val randomCycle: Int,
        val consumedPosition: Int,
    ) : AllocateRandomResult

    data object InvalidState : AllocateRandomResult

    data object CycleOverflow : AllocateRandomResult

    data object PersistenceFailure : AllocateRandomResult
}
