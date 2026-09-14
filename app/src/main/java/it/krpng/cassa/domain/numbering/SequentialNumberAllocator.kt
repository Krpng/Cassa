package it.krpng.cassa.domain.numbering

/**
 * Pure sequential allocation for a single [nextSequentialNumber] counter.
 * Does not persist; callers (AcceptOrder transaction) persist the advanced state.
 */
object SequentialNumberAllocator {
    fun allocate(nextSequentialNumber: Long): SequentialAllocationResult {
        if (nextSequentialNumber <= 0L) {
            return SequentialAllocationResult.InvalidState
        }
        if (nextSequentialNumber == Long.MAX_VALUE) {
            return SequentialAllocationResult.CounterOverflow
        }

        return SequentialAllocationResult.Allocated(
            value = nextSequentialNumber,
            displayNumber = SequentialNumberFormatter.format(nextSequentialNumber),
            nextSequentialNumber = nextSequentialNumber + 1L,
        )
    }
}

sealed interface SequentialAllocationResult {
    data class Allocated(
        val value: Long,
        val displayNumber: String,
        val nextSequentialNumber: Long,
    ) : SequentialAllocationResult

    data object InvalidState : SequentialAllocationResult

    data object CounterOverflow : SequentialAllocationResult
}
