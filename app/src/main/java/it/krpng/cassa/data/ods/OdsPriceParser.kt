package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money

sealed interface OdsPriceParseResult {
    data class Success(
        val money: Money,
    ) : OdsPriceParseResult

    data class Failure(
        val error: OdsPriceParseError,
    ) : OdsPriceParseResult
}

enum class OdsPriceParseError {
    MISSING_VALUE,
    NON_NUMERIC,
    NEGATIVE,
    TOO_MANY_DECIMALS,
    OVERFLOW,
    UNSUPPORTED_CELL_TYPE,
}

object OdsPriceParser {
    fun parse(cell: RawOdsCell?): OdsPriceParseResult {
        if (cell == null || cell.kind == RawOdsCellKind.EMPTY) {
            return OdsPriceParseResult.Failure(OdsPriceParseError.MISSING_VALUE)
        }

        val rawPrice = when (cell.kind) {
            RawOdsCellKind.TEXT -> cell.text
            RawOdsCellKind.NUMBER,
            RawOdsCellKind.CURRENCY,
            -> cell.rawValue
                ?: return OdsPriceParseResult.Failure(OdsPriceParseError.MISSING_VALUE)

            RawOdsCellKind.EMPTY -> error("La cella vuota è già stata gestita.")
            RawOdsCellKind.OTHER ->
                return OdsPriceParseResult.Failure(OdsPriceParseError.UNSUPPORTED_CELL_TYPE)
        }

        return parseExactAmount(rawPrice)
    }

    private fun parseExactAmount(rawValue: String): OdsPriceParseResult {
        val trimmedValue = rawValue.trimOdsWhitespace()
        if (trimmedValue.isEmpty()) {
            return OdsPriceParseResult.Failure(OdsPriceParseError.MISSING_VALUE)
        }

        val amount = trimmedValue.withOptionalEuroSymbol()
            ?: return OdsPriceParseResult.Failure(OdsPriceParseError.NON_NUMERIC)
        if (amount.startsWith('-')) {
            return OdsPriceParseResult.Failure(OdsPriceParseError.NEGATIVE)
        }
        if (TOO_PRECISE_AMOUNT.matches(amount)) {
            return OdsPriceParseResult.Failure(OdsPriceParseError.TOO_MANY_DECIMALS)
        }
        if (!VALID_AMOUNT.matches(amount)) {
            return OdsPriceParseResult.Failure(OdsPriceParseError.NON_NUMERIC)
        }

        val separatorIndex = amount.indexOfFirst { character ->
            character == ',' || character == '.'
        }
        val wholePart = if (separatorIndex < 0) amount else amount.substring(0, separatorIndex)
        val decimalPart = if (separatorIndex < 0) "" else amount.substring(separatorIndex + 1)
        val wholeEuros = wholePart.toLongOrNull()
            ?: return OdsPriceParseResult.Failure(OdsPriceParseError.OVERFLOW)

        return try {
            val wholeCents = Math.multiplyExact(wholeEuros, CENTS_PER_EURO)
            val decimalCents = when (decimalPart.length) {
                0 -> 0L
                1 -> decimalPart[0].digitToInt().toLong() * 10L
                else -> decimalPart.toLong()
            }
            val cents = Math.addExact(wholeCents, decimalCents)
            OdsPriceParseResult.Success(Money.ofCents(cents))
        } catch (_: ArithmeticException) {
            OdsPriceParseResult.Failure(OdsPriceParseError.OVERFLOW)
        }
    }

    private fun String.withOptionalEuroSymbol(): String? {
        val startsWithEuro = startsWith(EURO_SYMBOL)
        val withoutEuro = if (startsWithEuro) drop(1).trimOdsWhitespace() else this

        return withoutEuro.takeUnless { EURO_SYMBOL in it }
    }

    private fun String.trimOdsWhitespace(): String = ODS_OUTER_WHITESPACE.replace(this, "")

    private const val CENTS_PER_EURO = 100L
    private const val EURO_SYMBOL = '€'
    private val ODS_OUTER_WHITESPACE = Regex("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$")
    private val VALID_AMOUNT = Regex("^[0-9]+(?:[,.][0-9]{1,2})?$")
    private val TOO_PRECISE_AMOUNT = Regex("^[0-9]+[,.][0-9]{3,}$")
}
