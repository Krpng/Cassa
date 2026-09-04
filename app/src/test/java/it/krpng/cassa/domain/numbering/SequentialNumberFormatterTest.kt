package it.krpng.cassa.domain.numbering

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SequentialNumberFormatterTest {
    @Test
    fun `NUM-T001 first sequential number is 001`() {
        assertEquals("001", SequentialNumberFormatter.format(1))
    }

    @Test
    fun `NUM-T002 sequential values use at least three digits`() {
        val formatted = listOf(1L, 2L, 3L, 9L, 10L, 99L, 100L)
            .map(SequentialNumberFormatter::format)

        assertEquals(
            listOf("001", "002", "003", "009", "010", "099", "100"),
            formatted,
        )
    }

    @Test
    fun `NUM-T004 values continue beyond 999 without wrapping`() {
        val formatted = listOf(998L, 999L, 1_000L, 1_001L)
            .map(SequentialNumberFormatter::format)

        assertEquals(listOf("998", "999", "1000", "1001"), formatted)
        assertEquals(Long.MAX_VALUE.toString(), SequentialNumberFormatter.format(Long.MAX_VALUE))
    }

    @Test
    fun `zero and negative values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SequentialNumberFormatter.format(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SequentialNumberFormatter.format(-1)
        }
    }

    @Test
    fun `formatting is deterministic`() {
        val first = SequentialNumberFormatter.format(42)

        repeat(10) {
            assertEquals(first, SequentialNumberFormatter.format(42))
        }
    }

    @Test
    fun `formatting is independent from default locale`() {
        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("007", SequentialNumberFormatter.format(7))
            assertEquals("1000", SequentialNumberFormatter.format(1_000))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
