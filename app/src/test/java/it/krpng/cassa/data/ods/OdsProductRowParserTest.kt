package it.krpng.cassa.data.ods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OdsProductRowParserTest {
    @Test
    fun `extracts raw product values from reordered columns and preserves source`() {
        val price = cell(
            text = "7,50 €",
            kind = RawOdsCellKind.CURRENCY,
            rawValue = "7.50",
            currencyCode = "EUR",
        )
        val detected = detectedProductSheet(
            sheetName = "Catalogo settembre",
            headers = listOf(
                "Ingredienti",
                "Categoria",
                "Prodotto",
                "Prezzo Sala",
                "Prezzo Asporto",
                "Nome stampato",
            ),
            dataRows = listOf(
                rawRow(
                    cell("Pomodoro, Prorcini"),
                    cell("Pizze"),
                    cell("Margerita"),
                    cell("valore irrilevante"),
                    price,
                    cell("MARG"),
                ),
            ),
        )

        val row = OdsProductRowParser().parse(detected).single()

        assertEquals("Catalogo settembre", row.sheetName)
        assertEquals(2, row.rowNumber)
        assertEquals("Margerita", row.productName?.text)
        assertSame(price, row.takeawayPrice)
        assertEquals("Pizze", row.category?.text)
        assertEquals(
            RawOptionalOdsCell.ColumnPresent(cell("MARG")),
            row.printedName,
        )
        assertEquals(
            RawOptionalOdsCell.ColumnPresent(cell("Pomodoro, Prorcini")),
            row.ingredients,
        )
    }

    @Test
    fun `distinguishes optional columns absent from columns present with empty cells`() {
        val withoutOptionalColumns = detectedProductSheet(
            sheetName = "Senza opzionali",
            headers = listOf("Prodotto", "Prezzo Asporto", "Categoria"),
            dataRows = listOf(rawRow(cell("Marinara"), cell("6,00"), cell("Pizze"))),
        )
        val withEmptyOptionalColumns = detectedProductSheet(
            sheetName = "Con opzionali vuote",
            headers = listOf(
                "Prodotto",
                "Prezzo Asporto",
                "Categoria",
                "Nome stampato",
                "Ingredienti",
            ),
            dataRows = listOf(rawRow(cell("Marinara"), cell("6,00"), cell("Pizze"))),
        )

        val absent = OdsProductRowParser().parse(withoutOptionalColumns).single()
        val presentEmpty = OdsProductRowParser().parse(withEmptyOptionalColumns).single()

        assertEquals(RawOptionalOdsCell.ColumnAbsent, absent.printedName)
        assertEquals(RawOptionalOdsCell.ColumnAbsent, absent.ingredients)
        assertEquals(RawOptionalOdsCell.ColumnPresent(null), presentEmpty.printedName)
        assertEquals(RawOptionalOdsCell.ColumnPresent(null), presentEmpty.ingredients)
    }

    @Test
    fun `ignores fully empty rows but preserves partial rows for later validation`() {
        val detected = detectedProductSheet(
            sheetName = "Prodotti",
            headers = listOf("Prodotto", "Prezzo Asporto", "Categoria"),
            leadingRows = listOf(rawRow(cell("Titolo menu"))),
            dataRows = listOf(
                rawRow(),
                rawRow(emptyCell(), emptyCell(), emptyCell()),
                rawRow(cell("Wrustel e patate")),
            ),
        )

        val row = OdsProductRowParser().parse(detected).single()

        assertEquals(5, row.rowNumber)
        assertEquals("Wrustel e patate", row.productName?.text)
        assertNull(row.takeawayPrice)
        assertNull(row.category)
    }

    @Test
    fun `preserves original display text without normalization or autocorrect`() {
        val detected = detectedProductSheet(
            sheetName = "Prodotti",
            headers = listOf("Prodotto", "Prezzo Asporto", "Categoria", "Ingredienti"),
            dataRows = listOf(
                rawRow(
                    cell("  Margerita  "),
                    cell("7,00"),
                    cell("Pizze"),
                    cell("Prorcini, Wrustel"),
                ),
            ),
        )

        val row = OdsProductRowParser().parse(detected).single()

        assertEquals("  Margerita  ", row.productName?.text)
        assertEquals(
            "Prorcini, Wrustel",
            (row.ingredients as RawOptionalOdsCell.ColumnPresent).cell?.text,
        )
    }

    @Test
    fun `Prezzo Sala content never changes the parsed product row`() {
        val first = detectedProductSheet(
            sheetName = "Prodotti",
            headers = listOf("Prodotto", "Prezzo Sala", "Prezzo Asporto", "Categoria"),
            dataRows = listOf(
                rawRow(cell("Marinara"), cell("non numerico"), cell("6,00"), cell("Pizze")),
            ),
        )
        val second = detectedProductSheet(
            sheetName = "Prodotti",
            headers = listOf("Prodotto", "Prezzo Sala", "Prezzo Asporto", "Categoria"),
            dataRows = listOf(
                rawRow(cell("Marinara"), cell("999,99"), cell("6,00"), cell("Pizze")),
            ),
        )

        assertEquals(
            OdsProductRowParser().parse(first),
            OdsProductRowParser().parse(second),
        )
    }

    @Test
    fun `does not parse price category or ingredients`() {
        val price = cell("€ 7,123")
        val category = cell("Categoria futura")
        val ingredients = cell("Pomodoro, pomodòro,  Prorcini")
        val detected = detectedProductSheet(
            sheetName = "Prodotti",
            headers = listOf("Prodotto", "Prezzo Asporto", "Categoria", "Ingredienti"),
            dataRows = listOf(rawRow(cell("Speciale"), price, category, ingredients)),
        )

        val row = OdsProductRowParser().parse(detected).single()

        assertSame(price, row.takeawayPrice)
        assertSame(category, row.category)
        assertSame(
            ingredients,
            (row.ingredients as RawOptionalOdsCell.ColumnPresent).cell,
        )
    }

    private fun detectedProductSheet(
        sheetName: String,
        headers: List<String>,
        dataRows: List<RawOdsRow>,
        leadingRows: List<RawOdsRow> = emptyList(),
    ): DetectedProductSheet {
        val rawSheet = RawOdsSheet(
            name = sheetName,
            rows = leadingRows + rawRow(*headers.map(::cell).toTypedArray()) + dataRows,
        )
        return requireNotNull(
            OdsSheetDetector().detect(RawMenuImport(listOf(rawSheet))).productSheet,
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
