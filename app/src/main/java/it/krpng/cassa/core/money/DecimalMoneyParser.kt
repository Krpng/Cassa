package it.krpng.cassa.core.money

object DecimalMoneyParser {
    fun parse(input: String): Money? {
        val value = input.trim()
        if (!PRICE_PATTERN.matches(value)) return null

        val separatorIndex = value.indexOfFirst { character ->
            character == ',' || character == '.'
        }
        val wholePart = if (separatorIndex == -1) value else value.substring(0, separatorIndex)
        val decimalPart = if (separatorIndex == -1) "" else value.substring(separatorIndex + 1)

        return try {
            val wholeCents = Math.multiplyExact(wholePart.toLong(), CENTS_PER_EURO)
            val decimalCents = when (decimalPart.length) {
                0 -> 0L
                1 -> decimalPart.toLong() * 10L
                else -> decimalPart.toLong()
            }
            Money.ofCents(Math.addExact(wholeCents, decimalCents))
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private val PRICE_PATTERN = Regex("^[0-9]+([,.][0-9]{1,2})?$")
    private const val CENTS_PER_EURO = 100L
}
