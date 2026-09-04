package it.krpng.cassa.core.datetime

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClockProviderTest {
    @Test
    fun `system clock returns the current instant`() {
        val before = Instant.now()
        val actual = SystemClockProvider.now()
        val after = Instant.now()

        assertFalse(actual.isBefore(before))
        assertFalse(actual.isAfter(after))
    }

    @Test
    fun `fake clock returns the configured instant deterministically`() {
        val expected = Instant.parse("2026-09-04T10:15:30Z")
        val clock: ClockProvider = FakeClockProvider(expected)

        assertEquals(expected, clock.now())
        assertEquals(expected, clock.now())
    }

    @Test
    fun `fake clock can be moved to an explicitly chosen instant`() {
        val clock = FakeClockProvider(Instant.parse("2026-09-04T10:15:30Z"))
        val nextInstant = Instant.parse("2026-12-31T23:59:59Z")

        clock.currentInstant = nextInstant

        assertEquals(nextInstant, clock.now())
    }
}
