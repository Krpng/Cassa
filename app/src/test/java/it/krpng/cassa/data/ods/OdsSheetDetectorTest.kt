package it.krpng.cassa.data.ods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OdsSheetDetectorTest {
    @Test
    fun `ODS-011 detects product and addition sheets by headers not names`() {
        val products = sheet(
            name = "Lista estate",
            rows = listOf(
                row("Categoria", "Colonna extra", "Prodotto", "Prezzo Asporto"),
                row("Pizze", "ignorata", "Margerita", "7,00"),
            ),
        )
        val additions = sheet(
            name = "Supplementi 2026",
            rows = listOf(
                row("Prezzo", "Prodotto", "Nome stampato"),
                row("1,50", "Mozzarella", "MOZZ"),
            ),
        )

        val result = OdsSheetDetector().detect(RawMenuImport(listOf(additions, products)))

        assertEquals(products, result.productSheet?.source)
        assertEquals(
            mapOf(
                ProductOdsColumn.CATEGORY to 0,
                ProductOdsColumn.PRODUCT to 2,
                ProductOdsColumn.TAKEAWAY_PRICE to 3,
            ),
            result.productSheet?.columns,
        )
        assertEquals(additions, result.additionSheet?.source)
        assertEquals(
            mapOf(
                AdditionOdsColumn.PRICE to 0,
                AdditionOdsColumn.PRODUCT to 1,
                AdditionOdsColumn.PRINTED_NAME to 2,
            ),
            result.additionSheet?.columns,
        )
    }

    @Test
    fun `normalizes header case accents and whitespace and finds a later header row`() {
        val products = sheet(
            name = "Catalogo",
            rows = listOf(
                row(),
                row("Menu settembre"),
                row(
                    "  PRODOTTO  ",
                    "PREZZO\tASPORTO",
                    "CATEGORÌA",
                    "PREZZO   SALA",
                    "NOME STAMPATO",
                    "INGREDIENTI",
                ),
            ),
        )

        val detected = OdsSheetDetector()
            .detect(RawMenuImport(listOf(products)))
            .productSheet

        assertEquals(2, detected?.headerRowIndex)
        assertEquals(
            mapOf(
                ProductOdsColumn.PRODUCT to 0,
                ProductOdsColumn.TAKEAWAY_PRICE to 1,
                ProductOdsColumn.CATEGORY to 2,
                ProductOdsColumn.ROOM_PRICE to 3,
                ProductOdsColumn.PRINTED_NAME to 4,
                ProductOdsColumn.INGREDIENTS to 5,
            ),
            detected?.columns,
        )
    }

    @Test
    fun `optional headers may be absent`() {
        val result = OdsSheetDetector().detect(
            RawMenuImport(
                listOf(
                    sheet("Prodotti minimi", listOf(row("Prodotto", "Prezzo Asporto", "Categoria"))),
                    sheet("Aggiunte minime", listOf(row("Prodotto", "Prezzo"))),
                ),
            ),
        )

        assertEquals(3, result.productSheet?.columns?.size)
        assertEquals(2, result.additionSheet?.columns?.size)
    }

    @Test
    fun `ODS-012 ignores empty sheet and leaves unknown sheet unclassified`() {
        val empty = sheet(
            name = "Terzo foglio",
            rows = listOf(row(), row("  ", rawValues = listOf(null))),
        )
        val unknown = sheet("Note", listOf(row("Titolo", "Testo")))
        val numericOnly = sheet(
            name = "Statistiche",
            rows = listOf(row("", rawValues = listOf("42"))),
        )

        val result = OdsSheetDetector().detect(RawMenuImport(listOf(empty, unknown, numericOnly)))

        assertNull(result.productSheet)
        assertNull(result.additionSheet)
        assertEquals(listOf(empty), result.emptySheets)
        assertEquals(listOf(unknown, numericOnly), result.unclassifiedSheets)
    }

    @Test
    fun `rejects duplicate normalized headers in a detected header row`() {
        val sheet = sheet(
            "Catalogo",
            listOf(row("Prodotto", " PRODOTTO ", "Prezzo Asporto", "Categoria")),
        )

        val error = assertThrows(DuplicateOdsHeaderException::class.java) {
            OdsSheetDetector().detect(RawMenuImport(listOf(sheet)))
        }

        assertEquals("prodotto", error.normalizedHeader)
        assertEquals(0, error.headerRowIndex)
    }

    @Test
    fun `rejects multiple product sheet candidates`() {
        val first = sheet("Nord", listOf(row("Prodotto", "Prezzo Asporto", "Categoria")))
        val second = sheet("Sud", listOf(row("Categoria", "Prodotto", "Prezzo Asporto")))

        val error = assertThrows(AmbiguousOdsSheetException::class.java) {
            OdsSheetDetector().detect(RawMenuImport(listOf(first, second)))
        }

        assertEquals(OdsSheetRole.PRODUCTS, error.role)
        assertEquals(listOf("Nord", "Sud"), error.sheetNames)
    }

    @Test
    fun `rejects multiple addition sheet candidates`() {
        val first = sheet("Extra uno", listOf(row("Prodotto", "Prezzo")))
        val second = sheet("Extra due", listOf(row("Prezzo", "Prodotto")))

        val error = assertThrows(AmbiguousOdsSheetException::class.java) {
            OdsSheetDetector().detect(RawMenuImport(listOf(first, second)))
        }

        assertEquals(OdsSheetRole.ADDITIONS, error.role)
    }

    @Test
    fun `rejects a header row matching both semantic roles`() {
        val ambiguous = sheet(
            "Tutto",
            listOf(row("Prodotto", "Prezzo", "Prezzo Asporto", "Categoria")),
        )

        assertThrows(AmbiguousOdsSheetRoleException::class.java) {
            OdsSheetDetector().detect(RawMenuImport(listOf(ambiguous)))
        }
    }

    private fun sheet(name: String?, rows: List<RawOdsRow>): RawOdsSheet =
        RawOdsSheet(name = name, rows = rows)

    private fun row(
        vararg values: String,
        rawValues: List<String?> = List(values.size) { null },
    ): RawOdsRow = RawOdsRow(
        cells = values.mapIndexed { index, value ->
            RawOdsCell(
                kind = if (value.isEmpty() && rawValues.getOrNull(index) == null) {
                    RawOdsCellKind.EMPTY
                } else {
                    RawOdsCellKind.TEXT
                },
                text = value,
                rawValue = rawValues.getOrNull(index),
                currencyCode = null,
            )
        },
    )
}
