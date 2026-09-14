package it.krpng.cassa.domain.repository

/**
 * Numbering persistence for AcceptOrder.
 *
 * [allocateNextSequential] advances and persists sequential state for [businessDate].
 * It must be invoked inside the caller's single Room transaction (future AcceptOrder).
 * It does **not** open or commit an autonomous transaction, so a number is not
 * consumed outside that outer unit of work.
 *
 * There is intentionally no UI/use-case entry point that consumes numbers alone.
 */
interface NumberingRepository {
    suspend fun allocateNextSequential(
        businessDate: String,
        updatedAt: Long,
    ): AllocateSequentialResult
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
