package it.krpng.cassa.data.ods

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser

class OdsMenuParserTest {
    @Test
    fun `reads content xml regardless of ZIP entry order and preserves raw cells`() {
        val result = parser().parse(
            odsZip(
                contentXml = document(
                    """
                    <t:table t:name="Catalogo">
                      <t:table-row>
                        <t:table-cell o:value-type="string">
                          <x:p>Prima <x:span>riga</x:span></x:p>
                          <x:p>seconda</x:p>
                        </t:table-cell>
                        <t:table-cell/>
                        <t:table-cell o:value-type="float" o:value="7.50"><x:p>7,50</x:p></t:table-cell>
                        <t:table-cell o:value-type="currency" o:value="2.00" o:currency="EUR">
                          <x:p>2,00 €</x:p>
                        </t:table-cell>
                      </t:table-row>
                      <t:table-row/>
                    </t:table>
                    <t:table t:name="Altro"><t:table-row><t:table-cell><x:p>dato</x:p></t:table-cell></t:table-row></t:table>
                    """,
                ),
                entriesBeforeContent = mapOf("META-INF/manifest.xml" to "<manifest/>")
            ),
        )

        assertEquals(2, result.sheets.size)
        assertEquals("Catalogo", result.sheets[0].name)
        assertEquals("Altro", result.sheets[1].name)

        val firstRow = result.sheets[0].rows[0]
        assertEquals(4, firstRow.cells.size)
        assertEquals(RawOdsCellKind.TEXT, firstRow.cells[0].kind)
        assertEquals("Prima riga\nseconda", firstRow.cells[0].text)
        assertEquals(RawOdsCellKind.EMPTY, firstRow.cells[1].kind)
        assertEquals("", firstRow.cells[1].text)
        assertNull(firstRow.cells[1].rawValue)
        assertEquals(RawOdsCellKind.NUMBER, firstRow.cells[2].kind)
        assertEquals("7.50", firstRow.cells[2].rawValue)
        assertEquals("7,50", firstRow.cells[2].text)
        assertEquals(RawOdsCellKind.CURRENCY, firstRow.cells[3].kind)
        assertEquals("2.00", firstRow.cells[3].rawValue)
        assertEquals("EUR", firstRow.cells[3].currencyCode)
        assertEquals("2,00 €", firstRow.cells[3].text)
        assertEquals(emptyList<RawOdsCell>(), result.sheets[0].rows[1].cells)
    }

    @Test
    fun `ODS-021 expands repeated rows and cells together`() {
        val result = parser().parse(
            odsZip(
                document(
                    """
                    <t:table t:name="Ripetizioni">
                      <t:table-row t:number-rows-repeated="3">
                        <t:table-cell t:number-columns-repeated="2"><x:p>A</x:p></t:table-cell>
                        <t:table-cell o:value-type="float" o:value="1.25"/>
                      </t:table-row>
                    </t:table>
                    """,
                ),
            ),
        )

        val rows = result.sheets.single().rows
        assertEquals(3, rows.size)
        rows.forEachIndexed { index, row ->
            assertEquals(3, row.cells.size)
            assertEquals(listOf("A", "A", ""), row.cells.map(RawOdsCell::text))
            assertEquals("1.25", row.cells.last().rawValue)
            assertEquals(index + 1, row.sourceRow)
        }
    }

    @Test
    fun `large trailing empty cell repeat is ignored without expansion`() {
        val result = parser(OdsParserLimits(maxCellsPerRow = 4)).parse(
            odsZip(
                document(
                    """
                    <t:table>
                      <t:table-row>
                        <t:table-cell><x:p>Prodotto</x:p></t:table-cell>
                        <t:table-cell t:number-columns-repeated="1024"/>
                      </t:table-row>
                    </t:table>
                    """,
                ),
            ),
        )

        val cells = result.sheets.single().rows.single().cells
        assertEquals(1, cells.size)
        assertEquals("Prodotto", cells.single().text)
    }

    @Test
    fun `internal empty cell repeat preserves following column position`() {
        val result = parser(OdsParserLimits(maxCellsPerRow = 4)).parse(
            odsZip(
                document(
                    """
                    <t:table>
                      <t:table-row>
                        <t:table-cell><x:p>A</x:p></t:table-cell>
                        <t:table-cell t:number-columns-repeated="2"/>
                        <t:table-cell><x:p>B</x:p></t:table-cell>
                      </t:table-row>
                    </t:table>
                    """,
                ),
            ),
        )

        val cells = result.sheets.single().rows.single().cells
        assertEquals(4, cells.size)
        assertEquals(listOf("A", "", "", "B"), cells.map(RawOdsCell::text))
    }

    @Test
    fun `large trailing empty row repeat is compacted safely`() {
        val result = parser(OdsParserLimits(maxRowsPerSheet = 3)).parse(
            odsZip(
                document(
                    """
                    <t:table>
                      <t:table-row><t:table-cell><x:p>Dato</x:p></t:table-cell></t:table-row>
                      <t:table-row t:number-rows-repeated="1048520"/>
                    </t:table>
                    """,
                ),
            ),
        )

        val rows = result.sheets.single().rows
        assertEquals(2, rows.size)
        assertEquals("Dato", rows[0].cells.single().text)
        assertEquals(emptyList<RawOdsCell>(), rows[1].cells)
        assertEquals(2, rows[1].sourceRow)
    }

    @Test
    fun `internal empty row repeat preserves source row for following data`() {
        val parsed = parser(OdsParserLimits(maxRowsPerSheet = 5)).parse(
            odsZip(
                document(
                    """
                    <t:table t:name="Catalogo">
                      <t:table-row>
                        <t:table-cell><x:p>Prodotto</x:p></t:table-cell>
                        <t:table-cell><x:p>Prezzo Asporto</x:p></t:table-cell>
                        <t:table-cell><x:p>Categoria</x:p></t:table-cell>
                      </t:table-row>
                      <t:table-row t:number-rows-repeated="5"/>
                      <t:table-row>
                        <t:table-cell><x:p>Margherita</x:p></t:table-cell>
                        <t:table-cell><x:p>7,00</x:p></t:table-cell>
                        <t:table-cell><x:p>Pizze</x:p></t:table-cell>
                      </t:table-row>
                    </t:table>
                    """,
                ),
            ),
        )

        val detected = requireNotNull(OdsSheetDetector().detect(parsed).productSheet)
        val productRow = OdsProductRowParser().parse(detected).single()

        assertEquals(7, productRow.rowNumber)
    }

    @Test
    fun `text paragraph preserves ODF spaces tabs and line breaks`() {
        val result = parser().parse(
            odsZip(
                document(
                    """
                    <t:table>
                      <t:table-row>
                        <t:table-cell><x:p>A<x:s x:c="3"/>B<x:tab/>C<x:line-break/>D</x:p></t:table-cell>
                      </t:table-row>
                    </t:table>
                    """,
                ),
            ),
        )

        assertEquals("A   B\tC\nD", result.sheets.single().rows.single().cells.single().text)
    }

    @Test
    fun `rejects malformed ZIP with a controlled error`() {
        assertThrows(MalformedOdsArchiveException::class.java) {
            parser().parse(ByteArrayInputStream("not a zip".toByteArray()))
        }
    }

    @Test
    fun `rejects archive without content xml`() {
        val archive = odsZip(contentXml = null, entriesBeforeContent = mapOf("styles.xml" to "<styles/>"))

        assertThrows(MissingOdsContentException::class.java) {
            parser().parse(archive)
        }
    }

    @Test
    fun `rejects malformed content xml with a controlled error`() {
        val archive = odsZip("<document><broken></document>")

        assertThrows(MalformedOdsXmlException::class.java) {
            parser().parse(archive)
        }
    }

    @Test
    fun `rejects repeated significant rows beyond configured limit without truncation`() {
        val archive = odsZip(
            document(
                """
                <t:table>
                  <t:table-row t:number-rows-repeated="4">
                    <t:table-cell><x:p>dato</x:p></t:table-cell>
                  </t:table-row>
                </t:table>
                """,
            ),
        )

        assertThrows(OdsExpansionLimitException::class.java) {
            parser(OdsParserLimits(maxRowsPerSheet = 3)).parse(archive)
        }
    }

    @Test
    fun `rejects repeated significant cells beyond configured limit without truncation`() {
        val archive = odsZip(
            document(
                """
                <t:table>
                  <t:table-row>
                    <t:table-cell t:number-columns-repeated="5"><x:p>dato</x:p></t:table-cell>
                  </t:table-row>
                </t:table>
                """,
            ),
        )

        assertThrows(OdsExpansionLimitException::class.java) {
            parser(OdsParserLimits(maxCellsPerRow = 4)).parse(archive)
        }
    }

    @Test
    fun `rejects content xml beyond configured byte limit`() {
        val archive = odsZip(document("<t:table/>"))

        assertThrows(OdsExpansionLimitException::class.java) {
            parser(OdsParserLimits(maxContentXmlBytes = 32)).parse(archive)
        }
    }

    @Test
    fun `rejects pathological repeated text spaces`() {
        val archive = odsZip(
            document(
                """
                <t:table>
                  <t:table-row><t:table-cell><x:p>A<x:s x:c="1000"/></x:p></t:table-cell></t:table-row>
                </t:table>
                """,
            ),
        )

        assertThrows(OdsExpansionLimitException::class.java) {
            parser(OdsParserLimits(maxTextCharactersPerCell = 20)).parse(archive)
        }
    }

    private fun parser(limits: OdsParserLimits = OdsParserLimits()): OdsMenuParser =
        OdsMenuParser(limits = limits, xmlParserFactory = ::KXmlParser)

    private fun document(spreadsheetContent: String): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <o:document-content
            xmlns:o="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:t="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
            xmlns:x="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
          <o:body>
            <o:spreadsheet>
              $spreadsheetContent
            </o:spreadsheet>
          </o:body>
        </o:document-content>
        """.trimIndent()

    private fun odsZip(
        contentXml: String?,
        entriesBeforeContent: Map<String, String> = emptyMap(),
    ): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entriesBeforeContent.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
                contentXml?.let { content ->
                    zip.putNextEntry(ZipEntry("content.xml"))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        return ByteArrayInputStream(bytes)
    }
}
