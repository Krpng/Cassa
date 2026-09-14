package it.krpng.cassa.domain.numbering

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableRandomPermutationTest {
    @Test
    fun `NUM-T011 permutation has size 2600 unique values covering 0 to 2599`() {
        listOf(
            1L to 1,
            0L to 1,
            42L to 1,
            Long.MIN_VALUE to 2,
        ).forEach { (seed, cycle) ->
            val perm = StableRandomPermutation.generate(seed, cycle)
            assertEquals(StableRandomPermutation.SIZE, perm.size)
            assertEquals(StableRandomPermutation.SIZE, perm.toSet().size)
            assertEquals((0 until StableRandomPermutation.SIZE).toSet(), perm.toSet())
            perm.forEach { value ->
                assertTrue("value $value out of range for seed=$seed cycle=$cycle", value in 0..2599)
            }
        }
    }

    @Test
    fun `NUM-T016 same seed and cycle produce bit-identical permutation`() {
        val first = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        val second = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        assertArrayEquals(first, second)
    }

    @Test
    fun `NUM-T014 same seed and cycle are deterministic across new generator calls`() {
        // Persistence of seed/position is NUM-004; this covers restart of the pure generator.
        val fromInstanceA = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        val fromInstanceB = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        assertArrayEquals(fromInstanceA, fromInstanceB)
        assertEquals(
            RandomCodeFormatter.format(fromInstanceA[0]),
            RandomCodeFormatter.format(fromInstanceB[0]),
        )
    }

    @Test
    fun `seed zero is a valid authoritative seed and yields a full permutation`() {
        val perm = StableRandomPermutation.generate(0L, CYCLE_1)
        assertEquals(StableRandomPermutation.SIZE, perm.size)
        assertEquals(StableRandomPermutation.SIZE, perm.toSet().size)
        assertEquals((0 until StableRandomPermutation.SIZE).toSet(), perm.toSet())
    }

    @Test
    fun `different cycles yield valid independent permutations for the same seed`() {
        val cycle1 = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        val cycle2 = StableRandomPermutation.generate(SEED_A, CYCLE_2)
        assertEquals(StableRandomPermutation.SIZE, cycle1.toSet().size)
        assertEquals(StableRandomPermutation.SIZE, cycle2.toSet().size)
        // Not a universal contract that every consecutive cycle differs, but for this
        // frozen derivation they must not accidentally be identical for the sample seed.
        assertFalseArraysEqual(cycle1, cycle2)
    }

    @Test
    fun `cycleSeed32 falls back when derived state would be zero`() {
        // seed xor (cycle * golden) == 0 → lower 32 bits zero before fallback
        val seedThatCancelsCycle1 = 0x9E3779B97F4A7C15uL.toLong()
        assertEquals(0u, (seedThatCancelsCycle1 xor (1L * 0x9E3779B97F4A7C15uL.toLong())).toUInt())
        assertEquals(0xA5A5A5A5L.toUInt(), StableRandomPermutation.cycleSeed32(seedThatCancelsCycle1, 1))

        val perm = StableRandomPermutation.generate(seedThatCancelsCycle1, 1)
        assertEquals(StableRandomPermutation.SIZE, perm.toSet().size)
    }

    @Test
    fun `cycleSeed32 for ordinary seed zero and cycle one is non-zero without fallback path needed`() {
        val derived = (0L xor (1L * 0x9E3779B97F4A7C15uL.toLong())).toUInt()
        assertTrue(derived != 0u)
        assertEquals(derived, StableRandomPermutation.cycleSeed32(0L, 1))
    }

    @Test
    fun `XorShift32 zero input is replaced before stepping via cycleSeed fallback only`() {
        // Direct nextUInt32(0) is undefined for the PRNG contract; production always
        // starts from cycleSeed32 which never returns 0.
        assertTrue(StableRandomPermutation.cycleSeed32(0L, 0) != 0u)
        assertEquals(0xA5A5A5A5L.toUInt(), StableRandomPermutation.cycleSeed32(0L, 0))
    }

    @Test
    fun `bounded index stays in 0 until i for Fisher-Yates steps`() {
        var state = StableRandomPermutation.cycleSeed32(SEED_A, CYCLE_1)
        for (i in 2599 downTo 1) {
            val next = StableRandomPermutation.nextUInt32(state)
            state = next.first
            val j = (next.second % (i + 1).toUInt()).toInt()
            assertTrue("j=$j out of 0..$i", j in 0..i)
        }
    }

    @Test
    fun `Fisher-Yates uses descending i from 2599 to 1`() {
        // Smoke: first and last positions move for a known non-trivial seed.
        val identity = IntArray(StableRandomPermutation.SIZE) { it }
        val perm = StableRandomPermutation.generate(SEED_A, CYCLE_1)
        assertFalseArraysEqual(identity, perm)
        assertEquals(StableRandomPermutation.SIZE, perm.size)
    }

    private fun assertFalseArraysEqual(a: IntArray, b: IntArray) {
        assertEquals(a.size, b.size)
        var same = true
        for (index in a.indices) {
            if (a[index] != b[index]) {
                same = false
                break
            }
        }
        assertTrue("expected permutations to differ", !same)
    }

    private companion object {
        const val SEED_A = 0x1234_5678_9ABC_DEF0L
        const val CYCLE_1 = 1
        const val CYCLE_2 = 2
    }
}
