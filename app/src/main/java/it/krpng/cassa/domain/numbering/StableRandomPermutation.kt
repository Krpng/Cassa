package it.krpng.cassa.domain.numbering

/**
 * FREEZE-A bit-stable random permutation: XorShift32 + Fisher–Yates.
 *
 * Same `(randomSeed, randomCycle)` always yields the identical permutation of `0..2599`.
 * No DB, Android, or platform RNG — pure domain primitive for NUM-003.
 */
object StableRandomPermutation {
    const val SIZE = 2600

    // Contract constant 0x9E3779B97F4A7C15 as raw 64-bit pattern (Kotlin signed Long literal rejects it).
    private val GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15uL.toLong()
    // Contract fallback 0xA5A5A5A5u — UInt literal exceeds signed Int const range.
    private val XORSHIFT_ZERO_FALLBACK = 0xA5A5A5A5L.toUInt()

    fun generate(randomSeed: Long, randomCycle: Int): IntArray {
        val perm = IntArray(SIZE) { index -> index }
        var state = cycleSeed32(randomSeed, randomCycle)
        for (i in (SIZE - 1) downTo 1) {
            val next = nextUInt32(state)
            state = next.first
            val raw = next.second
            val j = (raw % (i + 1).toUInt()).toInt()
            val tmp = perm[i]
            perm[i] = perm[j]
            perm[j] = tmp
        }
        return perm
    }

    /**
     * Contract:
     * `cycleSeed32 = (randomSeed xor (randomCycle.toLong() * 0x9E3779B97F4A7C15L)).toUInt()`
     * then if zero → `0xA5A5A5A5u`.
     */
    internal fun cycleSeed32(randomSeed: Long, randomCycle: Int): UInt {
        var cycleSeed32 =
            (randomSeed xor (randomCycle.toLong() * GOLDEN_RATIO_64)).toUInt()
        if (cycleSeed32 == 0u) {
            cycleSeed32 = XORSHIFT_ZERO_FALLBACK
        }
        return cycleSeed32
    }

    /**
     * Contract XorShift32 on unsigned 32-bit state; returns `(newState, raw)`.
     */
    internal fun nextUInt32(state: UInt): Pair<UInt, UInt> {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x shr 17)
        x = x xor (x shl 5)
        return x to x
    }
}
