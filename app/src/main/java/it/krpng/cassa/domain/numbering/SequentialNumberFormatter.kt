package it.krpng.cassa.domain.numbering

object SequentialNumberFormatter {
    fun format(number: Long): String {
        require(number > 0) { "Sequential order number must be positive" }

        return number.toString().padStart(length = 3, padChar = '0')
    }
}
