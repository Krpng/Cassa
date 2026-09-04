package it.krpng.cassa.core.datetime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BusinessDateCalculatorTest {
    @Test
    fun `DATE-001 immediately before 05 belongs to previous date`() {
        assertBusinessDate(
            localDateTime = "2026-09-04T04:59:59",
            expected = "2026-09-03",
        )
    }

    @Test
    fun `DATE-002 exactly 05 belongs to current date`() {
        assertBusinessDate(
            localDateTime = "2026-09-04T05:00:00",
            expected = "2026-09-04",
        )
    }

    @Test
    fun `time after cutoff belongs to current date`() {
        assertBusinessDate(
            localDateTime = "2026-09-04T18:30:00",
            expected = "2026-09-04",
        )
    }

    @Test
    fun `midnight belongs to previous date`() {
        assertBusinessDate(
            localDateTime = "2026-09-04T00:00:00",
            expected = "2026-09-03",
        )
    }

    @Test
    fun `DATE-003 night time before cutoff belongs to previous date`() {
        assertBusinessDate(
            localDateTime = "2026-09-03T01:30:00",
            expected = "2026-09-02",
        )
    }

    @Test
    fun `DATE-004 late night belongs to its calendar date`() {
        assertBusinessDate(
            localDateTime = "2026-09-04T23:59:59",
            expected = "2026-09-04",
        )
    }

    @Test
    fun `calendar date change applies the same cutoff rule`() {
        assertBusinessDate(
            localDateTime = "2026-09-05T04:59:59",
            expected = "2026-09-04",
        )
        assertBusinessDate(
            localDateTime = "2026-09-05T05:00:00",
            expected = "2026-09-05",
        )
    }

    @Test
    fun `DATE-005 uses the explicit Europe Rome timezone and fake clock`() {
        val sameInstant = Instant.parse("2026-09-04T03:30:00Z")
        val clock: ClockProvider = FakeClockProvider(sameInstant)

        assertEquals(
            LocalDate.parse("2026-09-04"),
            BusinessDateCalculator.calculate(clock.now(), ROME, DEFAULT_CUTOFF_MINUTES),
        )
        assertEquals(
            LocalDate.parse("2026-09-03"),
            BusinessDateCalculator.calculate(clock.now(), ZoneId.of("UTC"), DEFAULT_CUTOFF_MINUTES),
        )
    }

    @Test
    fun `cutoff is configurable in minutes`() {
        val cutoffAtSixThirty = 6 * 60 + 30

        assertBusinessDate(
            localDateTime = "2026-09-04T06:29:59",
            expected = "2026-09-03",
            cutoffMinutes = cutoffAtSixThirty,
        )
        assertBusinessDate(
            localDateTime = "2026-09-04T06:30:00",
            expected = "2026-09-04",
            cutoffMinutes = cutoffAtSixThirty,
        )
    }

    @Test
    fun `Europe Rome daylight saving gap is handled from Instant`() {
        val beforeGap = Instant.parse("2026-03-29T00:59:59Z")
        val afterGap = Instant.parse("2026-03-29T01:00:00Z")

        assertEquals(
            LocalDate.parse("2026-03-28"),
            BusinessDateCalculator.calculate(beforeGap, ROME, DEFAULT_CUTOFF_MINUTES),
        )
        assertEquals(
            LocalDate.parse("2026-03-28"),
            BusinessDateCalculator.calculate(afterGap, ROME, DEFAULT_CUTOFF_MINUTES),
        )
    }

    @Test
    fun `rejects cutoff outside a calendar day`() {
        val instant = Instant.parse("2026-09-04T12:00:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            BusinessDateCalculator.calculate(instant, ROME, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessDateCalculator.calculate(instant, ROME, 24 * 60)
        }
    }

    private fun assertBusinessDate(
        localDateTime: String,
        expected: String,
        cutoffMinutes: Int = DEFAULT_CUTOFF_MINUTES,
    ) {
        val instant = LocalDateTime.parse(localDateTime).atZone(ROME).toInstant()

        assertEquals(
            LocalDate.parse(expected),
            BusinessDateCalculator.calculate(instant, ROME, cutoffMinutes),
        )
    }

    companion object {
        private val ROME = ZoneId.of("Europe/Rome")
        private const val DEFAULT_CUTOFF_MINUTES = 300
    }
}
