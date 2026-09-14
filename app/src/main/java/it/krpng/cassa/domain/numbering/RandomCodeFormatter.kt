package it.krpng.cassa.domain.numbering

/**
 * Pure canonical mapping of a RANDOM permutation index to display code `[A-Z][0-9][0-9]`.
 *
 * Valid range is `0..2599` only. Out-of-range indices are rejected; cycle wrap is NUM-004.
 */
object RandomCodeFormatter {
    private const val MIN_INDEX = 0
    private const val MAX_INDEX = 2599
    private const val CODES_PER_LETTER = 100

    fun format(index: Int): String {
        require(index in MIN_INDEX..MAX_INDEX) {
            "Random code index must be in $MIN_INDEX..$MAX_INDEX"
        }

        val letterIndex = index / CODES_PER_LETTER
        val numericPart = index % CODES_PER_LETTER
        val letter = ('A' + letterIndex)
        return buildString(capacity = 3) {
            append(letter)
            append(numericPart.toString().padStart(length = 2, padChar = '0'))
        }
    }
}
