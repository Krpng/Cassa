package it.krpng.cassa.domain.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextLayoutTest {
    @Test
    fun `charsPerLine zero and negative clamp to one`() {
        assertEquals(1, ReceiptTextLayout.effectiveWidth(0))
        assertEquals(1, ReceiptTextLayout.effectiveWidth(-5))
        assertEquals(32, ReceiptTextLayout.effectiveWidth(32))
    }

    @Test
    fun `word wrap prefers spaces and hard-splits oversized tokens`() {
        assertEquals(
            listOf("hello", "world"),
            ReceiptTextLayout.wrapPreservingLeadingIndent("hello world", 5),
        )
        assertEquals(
            listOf("ABCDE", "FG"),
            ReceiptTextLayout.wrapPreservingLeadingIndent("ABCDEFG", 5),
        )
    }

    @Test
    fun `explicit newlines split before wrapping`() {
        assertEquals(
            listOf("aa", "bb", "cc"),
            ReceiptTextLayout.wrapPreservingLeadingIndent("aa\nbb\ncc", 10),
        )
    }

    @Test
    fun `leading indent preserved on continuation lines`() {
        assertEquals(
            listOf("   + Funghi", "   Extra"),
            ReceiptTextLayout.wrapPreservingLeadingIndent("   + Funghi Extra", 12),
        )
    }

    @Test
    fun `price fits on same line when width allows`() {
        val left = "1x Pizza"
        val price = "7,00"
        val width = 32
        val gap = width - left.length - price.length
        assertEquals(
            listOf(left + " ".repeat(gap) + price),
            ReceiptTextLayout.linesWithOptionalPrice(left, price, width),
        )
    }

    @Test
    fun `price uses dedicated right-aligned line when it does not fit`() {
        val left = "1x Very Long Product Name"
        val price = "7,00"
        val width = 28
        assertEquals(
            listOf(left, price.padStart(width)),
            ReceiptTextLayout.linesWithOptionalPrice(left, price, width),
        )
    }

    @Test
    fun `price longer than charsPerLine is preserved fully on dedicated line`() {
        val left = "1x A"
        val price = "1000,00"
        val width = 4
        require(price.length > width)
        require(left.length + 1 + price.length > width)

        val first = ReceiptTextLayout.linesWithOptionalPrice(left, price, width)
        val second = ReceiptTextLayout.linesWithOptionalPrice(left, price, width)

        assertEquals(first, second)
        assertEquals(
            listOf("1x A", "1000,00"),
            first,
        )
        assertEquals(price, first.last())
        assertTrue(first.last().length > width)
        assertFalse(first.any { line -> price.startsWith(line) && line.length < price.length })
    }

    @Test
    fun `section title adapts dashes to width`() {
        val line = ReceiptTextLayout.sectionTitleLine("PIZZE", 28)
        assertEquals(28, line.length)
        assertEquals('|', line.first())
        assertEquals(true, line.contains(" PIZZE "))
    }

    // --- D-067 scale-aware layout width ---

    @Test
    fun `D067 layoutWidth multipliers for base 42`() {
        assertEquals(1, ReceiptTextLayout.horizontalScaleMultiplier(PrintTextScale.NORMAL))
        assertEquals(1, ReceiptTextLayout.horizontalScaleMultiplier(PrintTextScale.DOUBLE_HEIGHT))
        assertEquals(2, ReceiptTextLayout.horizontalScaleMultiplier(PrintTextScale.DOUBLE_WIDTH))
        assertEquals(2, ReceiptTextLayout.horizontalScaleMultiplier(PrintTextScale.DOUBLE_BOTH))

        assertEquals(42, ReceiptTextLayout.layoutWidth(42, PrintTextScale.NORMAL))
        assertEquals(42, ReceiptTextLayout.layoutWidth(42, PrintTextScale.DOUBLE_HEIGHT))
        assertEquals(21, ReceiptTextLayout.layoutWidth(42, PrintTextScale.DOUBLE_WIDTH))
        assertEquals(21, ReceiptTextLayout.layoutWidth(42, PrintTextScale.DOUBLE_BOTH))
    }

    @Test
    fun `D067 layoutWidth never returns zero`() {
        assertEquals(1, ReceiptTextLayout.layoutWidth(0, PrintTextScale.DOUBLE_BOTH))
        assertEquals(1, ReceiptTextLayout.layoutWidth(-3, PrintTextScale.DOUBLE_BOTH))
        assertEquals(1, ReceiptTextLayout.layoutWidth(1, PrintTextScale.DOUBLE_BOTH))
        assertEquals(1, ReceiptTextLayout.layoutWidth(1, PrintTextScale.NORMAL))
    }
}
