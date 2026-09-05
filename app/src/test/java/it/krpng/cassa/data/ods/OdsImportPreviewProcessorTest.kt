package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class OdsImportPreviewProcessorTest {
    private val processor = OdsImportPreviewProcessor(
        menuParser = OdsMenuParser(xmlParserFactory = { KXmlParser() }),
    )

    @Test
    fun `valid ODS runs full pipeline and exposes create update unchanged plan`() {
        val input = ods(
            sheet(
                "Prodotti",
                listOf("Prodotto", "Prezzo Asporto", "Categoria"),
                listOf("Margherita", "7,00", "Pizze"),
                listOf("Marinara", "6,00", "Pizze"),
            ),
            sheet(
                "Aggiunte",
                listOf("Prodotto", "Prezzo"),
                listOf("Olive", "2,00"),
            ),
        )

        val result = processor.createPreview(
            input = input,
            existingProducts = listOf(product("Margherita", 700)),
            existingAdditions = listOf(addition("Olive", 100)),
        ) as OdsImportPreviewResult.Ready

        assertEquals(1, result.plan.productsToCreate.size)
        assertEquals(0, result.plan.productsToUpdate.size)
        assertEquals(1, result.plan.unchangedProducts.size)
        assertEquals(0, result.plan.additionsToCreate.size)
        assertEquals(1, result.plan.additionsToUpdate.size)
        assertEquals(0, result.plan.unchangedAdditions.size)
    }

    @Test
    fun `invalid ODS exposes every validation error and produces no plan`() {
        val input = ods(
            sheet(
                "Prodotti",
                listOf("Prodotto", "Prezzo Asporto", "Categoria"),
                listOf("Margherita", "non numerico", "Sconosciuta"),
            ),
            sheet(
                "Aggiunte",
                listOf("Prodotto", "Prezzo"),
                listOf("Cipolle", "testo"),
                listOf("Pomodoro sorrento", ""),
            ),
        )

        val result = processor.createPreview(
            input = input,
            existingProducts = emptyList(),
            existingAdditions = emptyList(),
        ) as OdsImportPreviewResult.Invalid

        assertEquals(4, result.errors.size)
        assertEquals(listOf(2, 2, 2, 3), result.errors.map { it.sourceRow })
        assertTrue(result.errors.any { it.code == MenuImportValidationErrorCode.UNKNOWN_CATEGORY })
        assertTrue(result.errors.any {
            it.code == MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING
        })
    }

    @Test
    fun `preview with only one recognized role does not imply delete or deactivate`() {
        val input = ods(
            sheet(
                "Prodotti rinominati",
                listOf("Prodotto", "Prezzo Asporto", "Categoria"),
                listOf("Margherita", "7,00", "Pizze"),
            ),
        )

        val result = processor.createPreview(
            input = input,
            existingProducts = listOf(product("Margherita", 700)),
            existingAdditions = listOf(addition("Non presente", 100)),
        ) as OdsImportPreviewResult.Ready

        assertEquals(1, result.plan.unchangedProducts.size)
        assertTrue(result.plan.additionsToCreate.isEmpty())
        assertTrue(result.plan.additionsToUpdate.isEmpty())
        assertTrue(result.plan.unchangedAdditions.isEmpty())
        assertFalse(result.plan.productsToCreate.any { it.values.name == "Non presente" })
    }

    private fun sheet(name: String, vararg rows: List<String>): Sheet = Sheet(name, rows.toList())

    private fun ods(vararg sheets: Sheet): ByteArrayInputStream {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append(
                "<office:document-content " +
                    "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" " +
                    "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" " +
                    "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">" +
                    "<office:body><office:spreadsheet>",
            )
            sheets.forEach { sheet ->
                append("<table:table table:name=\"").append(sheet.name.escapeXml()).append("\">")
                sheet.rows.forEach { row ->
                    append("<table:table-row>")
                    row.forEach { value ->
                        append("<table:table-cell office:value-type=\"string\"><text:p>")
                        append(value.escapeXml())
                        append("</text:p></table:table-cell>")
                    }
                    append("</table:table-row>")
                }
                append("</table:table>")
            }
            append("</office:spreadsheet></office:body></office:document-content>")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun String.escapeXml(): String = replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun product(name: String, priceCents: Long): Product = Product(
        id = 1,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        category = ProductCategory.PIZZA,
        price = Money.ofCents(priceCents),
        automaticExtrasPricing = true,
        active = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ingredients = emptyList(),
    )

    private fun addition(name: String, priceCents: Long): Addition = Addition(
        id = 1,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        price = Money.ofCents(priceCents),
        active = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private data class Sheet(
        val name: String,
        val rows: List<List<String>>,
    )
}
