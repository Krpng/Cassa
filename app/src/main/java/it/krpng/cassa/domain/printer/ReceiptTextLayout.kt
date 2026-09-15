package it.krpng.cassa.domain.printer

/**
 * Character-based layout helpers for receipt composition (D-051).
 * No ESC/POS, fonts, dots, or code-page byte lengths.
 */
internal object ReceiptTextLayout {
    fun effectiveWidth(charsPerLine: Int): Int = maxOf(charsPerLine, 1)

    fun bannerLine(width: Int): String = "=".repeat(width)

    fun totalSeparator(width: Int): String = "-".repeat(width)

    fun center(text: String, width: Int): String {
        if (text.length >= width) return text
        val leftPad = (width - text.length) / 2
        return " ".repeat(leftPad) + text
    }

    /**
     * Compact section title: `|{dashes} {TITLE} {dashes}` adapted to [width].
     * Never truncates [title]; if decorations cannot fit, emits [title] alone (may exceed width).
     */
    fun sectionTitleLine(title: String, width: Int): String {
        val inner = " $title "
        val minDecorated = 1 + inner.length // leading '|' + inner; zero dashes
        if (minDecorated > width) {
            return title
        }
        val dashBudget = width - minDecorated
        val leftDashes = dashBudget / 2
        val rightDashes = dashBudget - leftDashes
        return "|" + "-".repeat(leftDashes) + inner + "-".repeat(rightDashes)
    }

    fun rightAlign(text: String, width: Int): String {
        if (text.length >= width) return text
        return text.padStart(width)
    }

    /**
     * Same-line price when it fits; otherwise wrap [left] then dedicated right-aligned price line.
     */
    fun linesWithOptionalPrice(
        left: String,
        price: String?,
        width: Int,
    ): List<String> {
        if (price == null) {
            return wrapPreservingLeadingIndent(left, width)
        }
        if (left.length + 1 + price.length <= width) {
            val gap = width - left.length - price.length
            return listOf(left + " ".repeat(gap) + price)
        }
        return wrapPreservingLeadingIndent(left, width) + listOf(rightAlign(price, width))
    }

    /**
     * Word-wrap preferred; oversized tokens hard-split; explicit `\n` split first.
     * Leading indent on the first physical line of each segment is repeated on continuations.
     */
    fun wrapPreservingLeadingIndent(
        text: String,
        width: Int,
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        return text.split('\n').flatMap { segment -> wrapSegment(segment, width) }
    }

    private fun wrapSegment(
        segment: String,
        width: Int,
    ): List<String> {
        if (segment.isEmpty()) return listOf("")
        val indent = leadingIndent(segment)
        val body = segment.drop(indent.length)
        val contentWidth = maxOf(width - indent.length, 1)
        if (body.isEmpty()) {
            return listOf(indent.take(width))
        }
        val wrappedBody = wrapBody(body, contentWidth)
        return wrappedBody.map { indent + it }
    }

    private fun leadingIndent(text: String): String {
        val count = text.indexOfFirst { it != ' ' }.let { if (it < 0) text.length else it }
        return text.take(count)
    }

    private fun wrapBody(
        body: String,
        width: Int,
    ): List<String> {
        val lines = mutableListOf<String>()
        var remaining = body
        while (remaining.isNotEmpty()) {
            remaining = remaining.trimStart(' ')
            if (remaining.isEmpty()) break
            if (remaining.length <= width) {
                lines += remaining
                break
            }
            val window = remaining.take(width)
            val breakAt = window.lastIndexOf(' ')
            if (breakAt > 0) {
                lines += remaining.take(breakAt)
                remaining = remaining.drop(breakAt + 1)
            } else {
                lines += remaining.take(width)
                remaining = remaining.drop(width)
            }
        }
        return lines.ifEmpty { listOf("") }
    }
}
