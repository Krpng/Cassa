package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class OdsPriceParserTest {
    @Test
    fun `parses documented text formats exactly`() {
        val cases = mapOf(
            "0" to 0L,
            "0,00" to 0L,
            "0.00" to 0L,
            "7" to 700L,
            "7,5" to 750L,
            "7,50" to 750L,
            "7.5" to 750L,
            "7.50" to 750L,
            "€ 3,00" to 300L,
            "\u00A0€\u00A03,00\u00A0" to 300L,
        )

        cases.forEach { (input, expectedCents) ->
            assertSuccess(expectedCents, OdsPriceParser.parse(textCell(input)))
        }
    }

    @Test
    fun `parses numeric ODS raw value instead of formatted display text`() {
        val cell = RawOdsCell(
            kind = RawOdsCellKind.NUMBER,
            text = "valore visuale non numerico",
            rawValue = "7.50",
            currencyCode = null,
        )

        assertSuccess(750L, OdsPriceParser.parse(cell))
    }

    @Test
    fun `parses currency ODS raw value exactly including zero`() {
        val regular = RawOdsCell(
            kind = RawOdsCellKind.CURRENCY,
            text = "7,50 €",
            rawValue = "7.50",
            currencyCode = "EUR",
        )
        val zero = RawOdsCell(
            kind = RawOdsCellKind.CURRENCY,
            text = "0,00 €",
            rawValue = "0",
            currencyCode = "EUR",
        )

        assertSuccess(750L, OdsPriceParser.parse(regular))
        assertSuccess(0L, OdsPriceParser.parse(zero))
    }

    @Test
    fun `reports missing null empty blank and numeric values without raw value`() {
        assertFailure(OdsPriceParseError.MISSING_VALUE, OdsPriceParser.parse(null))
        assertFailure(OdsPriceParseError.MISSING_VALUE, OdsPriceParser.parse(emptyCell()))
        assertFailure(OdsPriceParseError.MISSING_VALUE, OdsPriceParser.parse(textCell(" \u00A0 ")))
        assertFailure(
            OdsPriceParseError.MISSING_VALUE,
            OdsPriceParser.parse(
                RawOdsCell(
                    kind = RawOdsCellKind.NUMBER,
                    text = "7,50",
                    rawValue = null,
                    currencyCode = null,
                ),
            ),
        )
    }

    @Test
    fun `reports non numeric and ambiguous formats`() {
        listOf(
            "Gorgonzola",
            "1,234.56",
            "1.234,56",
            "1 2",
            "+7,50",
            "7,50 €",
            "€ 7,50 €",
        ).forEach { input ->
            assertFailure(OdsPriceParseError.NON_NUMERIC, OdsPriceParser.parse(textCell(input)))
        }
    }

    @Test
    fun `reports negative values for text numeric and currency cells`() {
        assertFailure(OdsPriceParseError.NEGATIVE, OdsPriceParser.parse(textCell("-1,00")))
        assertFailure(
            OdsPriceParseError.NEGATIVE,
            OdsPriceParser.parse(rawValueCell(RawOdsCellKind.NUMBER, "-1.00")),
        )
        assertFailure(
            OdsPriceParseError.NEGATIVE,
            OdsPriceParser.parse(rawValueCell(RawOdsCellKind.CURRENCY, "-0.01")),
        )
    }

    @Test
    fun `reports more than two decimals without rounding`() {
        listOf("1,234", "1.234", "0,001", "7.5000").forEach { input ->
            assertFailure(
                OdsPriceParseError.TOO_MANY_DECIMALS,
                OdsPriceParser.parse(textCell(input)),
            )
        }
    }

    @Test
    fun `accepts maximum Money value and reports overflow above it`() {
        assertSuccess(Long.MAX_VALUE, OdsPriceParser.parse(textCell("92233720368547758,07")))
        assertFailure(
            OdsPriceParseError.OVERFLOW,
            OdsPriceParser.parse(textCell("92233720368547758,08")),
        )
        assertFailure(
            OdsPriceParseError.OVERFLOW,
            OdsPriceParser.parse(textCell("999999999999999999999999999999")),
        )
    }

    @Test
    fun `rejects unsupported ODS cell types`() {
        val cell = RawOdsCell(
            kind = RawOdsCellKind.OTHER,
            text = "7,50",
            rawValue = "7.50",
            currencyCode = null,
        )

        assertFailure(OdsPriceParseError.UNSUPPORTED_CELL_TYPE, OdsPriceParser.parse(cell))
    }

    @Test
    fun `decimal cents remain exact without floating point rounding`() {
        assertEquals(Money.ofCents(29), successMoney(OdsPriceParser.parse(textCell("0,29"))))
        assertEquals(Money.ofCents(1), successMoney(OdsPriceParser.parse(textCell("0,01"))))
    }

    private fun assertSuccess(expectedCents: Long, result: OdsPriceParseResult) {
        assertEquals(Money.ofCents(expectedCents), successMoney(result))
    }

    private fun successMoney(result: OdsPriceParseResult): Money =
        (result as OdsPriceParseResult.Success).money

    private fun assertFailure(expected: OdsPriceParseError, result: OdsPriceParseResult) {
        assertEquals(expected, (result as OdsPriceParseResult.Failure).error)
    }

    private fun textCell(text: String): RawOdsCell = RawOdsCell(
        kind = RawOdsCellKind.TEXT,
        text = text,
        rawValue = null,
        currencyCode = null,
    )

    private fun rawValueCell(kind: RawOdsCellKind, rawValue: String): RawOdsCell = RawOdsCell(
        kind = kind,
        text = "",
        rawValue = rawValue,
        currencyCode = if (kind == RawOdsCellKind.CURRENCY) "EUR" else null,
    )

    private fun emptyCell(): RawOdsCell = RawOdsCell(
        kind = RawOdsCellKind.EMPTY,
        text = "",
        rawValue = null,
        currencyCode = null,
    )
}
