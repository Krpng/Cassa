package it.krpng.cassa.data.database.entity

/**
 * Raw persisted projection of [numbering_state] without domain/entity invariants.
 *
 * Used only at the DB → repository validation boundary so corrupted rows can be
 * read and mapped to typed [it.krpng.cassa.domain.repository.AllocateSequentialResult.InvalidState]
 * / [it.krpng.cassa.domain.repository.AllocateRandomResult.InvalidState] instead of
 * crashing while materializing [NumberingStateEntity].
 */
data class NumberingStateRow(
    val businessDate: String,
    val nextSequentialNumber: Long,
    val randomCycle: Int,
    val randomSeed: Long,
    val randomSeedInitialized: Boolean,
    val randomPosition: Int,
    val updatedAt: Long,
)
