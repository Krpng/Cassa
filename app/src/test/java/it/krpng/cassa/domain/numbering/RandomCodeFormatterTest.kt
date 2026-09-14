package it.krpng.cassa.domain.numbering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomCodeFormatterTest {
    @Test
    fun `NUM-T017 maps canonical indices to display codes`() {
        assertEquals("A00", RandomCodeFormatter.format(0))
        assertEquals("A01", RandomCodeFormatter.format(1))
        assertEquals("A09", RandomCodeFormatter.format(9))
        assertEquals("A10", RandomCodeFormatter.format(10))
        assertEquals("A99", RandomCodeFormatter.format(99))
        assertEquals("B00", RandomCodeFormatter.format(100))
        assertEquals("B01", RandomCodeFormatter.format(101))
        assertEquals("Z99", RandomCodeFormatter.format(2599))
    }

    @Test
    fun `NUM-T010 formatted codes match A-Z then two digits`() {
        val pattern = Regex("^[A-Z][0-9]{2}$")
        val samples = listOf(0, 1, 9, 10, 99, 100, 101, 1372, 2500, 2599)

        samples.forEach { index ->
            val code = RandomCodeFormatter.format(index)
            assertTrue("expected $code to match $pattern for index=$index", pattern.matches(code))
            assertEquals(3, code.length)
        }
    }

    @Test
    fun `out of range indices are rejected without wrapping`() {
        assertThrows(IllegalArgumentException::class.java) {
            RandomCodeFormatter.format(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RandomCodeFormatter.format(2600)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RandomCodeFormatter.format(Int.MIN_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RandomCodeFormatter.format(Int.MAX_VALUE)
        }
    }

    @Test
    fun `formatting is deterministic`() {
        val first = RandomCodeFormatter.format(1372)

        assertEquals("N72", first)
        repeat(10) {
            assertEquals(first, RandomCodeFormatter.format(1372))
        }
    }
}
