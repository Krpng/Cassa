package it.krpng.cassa.data.ods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OdsAdditionRowParserTest {
    @Test
    fun `extracts raw addition values from reordered columns and preserves source`() {
        val price = cell(
            text = "1,50 €",
            kind = RawOdsCellKind.CURRENCY,
            rawValue = "1.50",
            currencyCode = "EUR",
        )
        val detected = detectedAdditionSheet(
            sheetName = "Supplementi settembre",
            headers = listOf("Nome stampato", "Prezzo", "Prodotto"),
            dataRows = listOf(rawRow(cell("MOZZ"), price, cell("Mozzarella"))),
        )

        val row = OdsAdditionRowParser().parse(detected).single()

        assertEquals("Supplementi settembre", row.sheetName)
        assertEquals(2, row.rowNumber)
        assertEquals("Mozzarella", row.additionName?.text)
        assertSame(price, row.price)
        assertEquals(
            RawOptionalOdsCell.ColumnPresent(cell("MOZZ")),
            row.printedName,
        )
    }

    @Test
    fun `distinguishes printed name column absent from present with empty cell`() {
        val withoutPrintedName = detectedAdditionSheet(
            sheetName = "Senza nome stampato",
            headers = listOf("Prodotto", "Prezzo"),
            dataRows = listOf(rawRow(cell("Pomodoro"), cell("0"))),
        )
        val withEmptyPrintedName = detectedAdditionSheet(
            sheetName = "Con nome stampato vuoto",
            headers = listOf("Prodotto", "Prezzo", "Nome stampato"),
            dataRows = listOf(rawRow(cell("Pomodoro"), cell("0"))),
        )

        val absent = OdsAdditionRowParser().parse(withoutPrintedName).single()
        val presentEmpty = OdsAdditionRowParser().parse(withEmptyPrintedName).single()

        assertEquals(RawOptionalOdsCell.ColumnAbsent, absent.printedName)
        assertEquals(RawOptionalOdsCell.ColumnPresent(null), presentEmpty.printedName)
    }

    @Test
    fun `ignores fully empty rows but preserves partial rows for later validation`() {
        val detected = detectedAdditionSheet(
            sheetName = "Aggiunte",
            headers = listOf("Prodotto", "Prezzo"),
            leadingRows = listOf(rawRow(cell("Listino aggiunte"))),
            dataRows = listOf(
                rawRow(),
                rawRow(emptyCell(), emptyCell()),
                rawRow(cell("Pomodoro sorrento")),
            ),
        )

        val row = OdsAdditionRowParser().parse(detected).single()

        assertEquals(5, row.rowNumber)
        assertEquals("Pomodoro sorrento", row.additionName?.text)
        assertNull(row.price)
    }

    @Test
    fun `preserves zero price as raw input`() {
        val zeroPrice = cell(
            text = "0,00 €",
            kind = RawOdsCellKind.CURRENCY,
            rawValue = "0",
            currencyCode = "EUR",
        )
        val detected = detectedAdditionSheet(
            sheetName = "Aggiunte",
            headers = listOf("Prodotto", "Prezzo"),
            dataRows = listOf(rawRow(cell("Bionda"), zeroPrice)),
        )

        val row = OdsAdditionRowParser().parse(detected).single()

        assertSame(zeroPrice, row.price)
        assertEquals("0", row.price?.rawValue)
        assertEquals("0,00 €", row.price?.text)
    }

    @Test
    fun `preserves invalid and blank prices for later validation`() {
        val invalidPrice = cell("Gorgonzola")
        val blankPrice = emptyCell()
        val detected = detectedAdditionSheet(
            sheetName = "Aggiunte",
            headers = listOf("Prodotto", "Prezzo"),
            dataRows = listOf(
                rawRow(cell("Cipolle"), invalidPrice),
                rawRow(cell("Mignon"), blankPrice),
            ),
        )

        val rows = OdsAdditionRowParser().parse(detected)

        assertEquals(2, rows.size)
        assertSame(invalidPrice, rows[0].price)
        assertSame(blankPrice, rows[1].price)
    }

    @Test
    fun `preserves original addition display text without normalization`() {
        val detected = detectedAdditionSheet(
            sheetName = "Aggiunte",
            headers = listOf("Prodotto", "Prezzo"),
            dataRows = listOf(rawRow(cell("  Prorcini  "), cell("2,00"))),
        )

        val row = OdsAdditionRowParser().parse(detected).single()

        assertEquals("  Prorcini  ", row.additionName?.text)
    }

    private fun detectedAdditionSheet(
        sheetName: String,
        headers: List<String>,
        dataRows: List<RawOdsRow>,
        leadingRows: List<RawOdsRow> = emptyList(),
    ): DetectedAdditionSheet {
        val rawSheet = RawOdsSheet(
            name = sheetName,
            rows = leadingRows + rawRow(*headers.map(::cell).toTypedArray()) + dataRows,
        )
        return requireNotNull(
            OdsSheetDetector().detect(RawMenuImport(listOf(rawSheet))).additionSheet,
        )
    }

    private fun rawRow(vararg cells: RawOdsCell): RawOdsRow = RawOdsRow(cells.toList())

    private fun emptyCell(): RawOdsCell = cell(text = "", kind = RawOdsCellKind.EMPTY)

    private fun cell(
        text: String,
        kind: RawOdsCellKind = RawOdsCellKind.TEXT,
        rawValue: String? = null,
        currencyCode: String? = null,
    ): RawOdsCell = RawOdsCell(
        kind = kind,
        text = text,
        rawValue = rawValue,
        currencyCode = currencyCode,
    )
}
