package it.krpng.cassa.domain.numbering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialNumberAllocatorTest {
    @Test
    fun `allocates first value as 001 and advances counter`() {
        val result = SequentialNumberAllocator.allocate(1L)

        assertTrue(result is SequentialAllocationResult.Allocated)
        val allocated = result as SequentialAllocationResult.Allocated
        assertEquals(1L, allocated.value)
        assertEquals("001", allocated.displayNumber)
        assertEquals(2L, allocated.nextSequentialNumber)
    }

    @Test
    fun `rejects non-positive counter as invalid state`() {
        assertSame(
            SequentialAllocationResult.InvalidState,
            SequentialNumberAllocator.allocate(0L),
        )
        assertSame(
            SequentialAllocationResult.InvalidState,
            SequentialNumberAllocator.allocate(-1L),
        )
    }

    @Test
    fun `rejects Long MAX_VALUE to avoid silent wraparound`() {
        assertSame(
            SequentialAllocationResult.CounterOverflow,
            SequentialNumberAllocator.allocate(Long.MAX_VALUE),
        )
    }

    @Test
    fun `formats values beyond 999 without three-digit cap`() {
        val result = SequentialNumberAllocator.allocate(1_000L) as SequentialAllocationResult.Allocated

        assertEquals("1000", result.displayNumber)
        assertEquals(1_001L, result.nextSequentialNumber)
    }
}
