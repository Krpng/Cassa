package it.krpng.cassa.data.ods

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class OdsParserLimits(
    val maxContentXmlBytes: Long = 8L * 1024L * 1024L,
    val maxSheets: Int = 32,
    val maxRowsPerSheet: Int = 10_000,
    val maxCellsPerRow: Int = 256,
    val maxTotalCells: Int = 500_000,
    val maxTextCharactersPerCell: Int = 100_000,
) {
    init {
        require(maxContentXmlBytes > 0)
        require(maxSheets > 0)
        require(maxRowsPerSheet > 0)
        require(maxCellsPerRow > 0)
        require(maxTotalCells > 0)
        require(maxTextCharactersPerCell > 0)
    }
}

sealed class OdsMenuParseException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class MalformedOdsArchiveException(cause: Throwable? = null) :
    OdsMenuParseException("Il file non è un archivio ODS valido.", cause)

class MissingOdsContentException :
    OdsMenuParseException("L'archivio ODS non contiene content.xml.")

class MalformedOdsXmlException(cause: Throwable? = null) :
    OdsMenuParseException("Il content.xml dell'ODS non è valido.", cause)

class OdsExpansionLimitException(message: String) : OdsMenuParseException(message)

class OdsMenuParser(
    private val limits: OdsParserLimits = OdsParserLimits(),
    private val xmlParserFactory: () -> XmlPullParser = { Xml.newPullParser() },
) {
    fun parse(input: InputStream): RawMenuImport {
        val zipInput = openZip(input)

        try {
            while (true) {
                val entry = zipInput.nextEntry ?: throw MissingOdsContentException()
                if (!entry.isDirectory && entry.name.normalizedZipPath() == CONTENT_XML_PATH) {
                    if (entry.size > limits.maxContentXmlBytes) {
                        throw OdsExpansionLimitException(
                            "content.xml supera il limite di ${limits.maxContentXmlBytes} byte.",
                        )
                    }
                    val contentXml = readContentXml(zipInput)
                    return parseContentXml(ByteArrayInputStream(contentXml))
                }
                zipInput.closeEntry()
            }
        } catch (error: OdsMenuParseException) {
            throw error
        } catch (error: ZipException) {
            throw MalformedOdsArchiveException(error)
        } catch (error: IOException) {
            throw MalformedOdsArchiveException(error)
        }
    }

    private fun readContentXml(zipInput: ZipInputStream): ByteArray {
        val limitedInput = LimitedInputStream(zipInput, limits.maxContentXmlBytes)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val count = limitedInput.read(buffer)
            if (count < 0) return output.toByteArray()
            output.write(buffer, 0, count)
        }
    }

    private fun parseContentXml(input: InputStream): RawMenuImport {
        try {
            val parser = xmlParserFactory()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(input, null)

            val sheets = mutableListOf<RawOdsSheet>()
            var totalCells = 0L

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.isStartTag(TABLE_NAMESPACE, TABLE_TAG)) {
                    if (sheets.size >= limits.maxSheets) {
                        throw OdsExpansionLimitException(
                            "Il numero di fogli supera il limite di ${limits.maxSheets}.",
                        )
                    }
                    val parsedSheet = parseSheet(parser, totalCells)
                    sheets += parsedSheet.sheet
                    totalCells = parsedSheet.totalCells
                } else {
                    parser.next()
                }
            }

            return RawMenuImport(sheets = sheets)
        } catch (error: OdsMenuParseException) {
            throw error
        } catch (error: ZipException) {
            throw MalformedOdsArchiveException(error)
        } catch (error: XmlPullParserException) {
            error.findOdsMenuParseException()?.let { parseError -> throw parseError }
            throw MalformedOdsXmlException(error)
        } catch (error: IOException) {
            throw MalformedOdsXmlException(error)
        } catch (error: RuntimeException) {
            error.findOdsMenuParseException()?.let { parseError -> throw parseError }
            throw MalformedOdsXmlException(error)
        }
    }

    private fun parseSheet(parser: XmlPullParser, initialTotalCells: Long): ParsedSheet {
        val sheetName = parser.getAttributeValue(TABLE_NAMESPACE, NAME_ATTRIBUTE)
        val rows = mutableListOf<RawOdsRow>()
        var totalCells = initialTotalCells
        var nextSourceRow = 1L

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.isStartTag(TABLE_NAMESPACE, ROW_TAG)) {
                    val rowRepeat = parser.positiveRepeat(ROWS_REPEATED_ATTRIBUTE)
                    val row = parseRow(parser)
                    val lastSourceRow = nextSourceRow + rowRepeat.toLong() - 1L
                    if (lastSourceRow > Int.MAX_VALUE.toLong()) {
                        throw OdsExpansionLimitException(
                            "La numerazione delle righe supera il limite supportato.",
                        )
                    }

                    if (row.isSemanticallyEmpty()) {
                        if (rows.size >= limits.maxRowsPerSheet) {
                            throw OdsExpansionLimitException(
                                "Il foglio supera il limite di ${limits.maxRowsPerSheet} righe materializzate.",
                            )
                        }
                        rows += row.copy(sourceRow = nextSourceRow.toInt())
                    } else {
                        if (rows.size.toLong() + rowRepeat > limits.maxRowsPerSheet.toLong()) {
                            throw OdsExpansionLimitException(
                                "Il foglio supera il limite di ${limits.maxRowsPerSheet} righe materializzate.",
                            )
                        }
                        val expandedCells = row.cells.size.toLong() * rowRepeat.toLong()
                        if (expandedCells > limits.maxTotalCells.toLong() - totalCells) {
                            throw OdsExpansionLimitException(
                                "Il documento supera il limite di ${limits.maxTotalCells} celle.",
                            )
                        }

                        repeat(rowRepeat) { repeatIndex ->
                            rows += row.copy(sourceRow = nextSourceRow.toInt() + repeatIndex)
                        }
                        totalCells += expandedCells
                    }
                    nextSourceRow = lastSourceRow + 1L
                }

                XmlPullParser.END_TAG -> if (parser.isEndTag(TABLE_NAMESPACE, TABLE_TAG)) {
                    return ParsedSheet(
                        sheet = RawOdsSheet(name = sheetName, rows = rows),
                        totalCells = totalCells,
                    )
                }

                XmlPullParser.END_DOCUMENT -> throw MalformedOdsXmlException()
            }
        }
    }

    private fun parseRow(parser: XmlPullParser): RawOdsRow {
        val cells = mutableListOf<RawOdsCell>()
        var pendingEmptyCells = 0L

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.isStartTag(TABLE_NAMESPACE, CELL_TAG)) {
                    val cellRepeat = parser.positiveRepeat(COLUMNS_REPEATED_ATTRIBUTE)
                    val cell = parseCell(parser)
                    if (cell.isSemanticallyEmpty()) {
                        pendingEmptyCells += cellRepeat.toLong()
                    } else {
                        val expandedSize =
                            cells.size.toLong() + pendingEmptyCells + cellRepeat.toLong()
                        if (expandedSize > limits.maxCellsPerRow.toLong()) {
                            throw OdsExpansionLimitException(
                                "Una riga supera il limite di ${limits.maxCellsPerRow} celle significative o posizionali.",
                            )
                        }
                        repeat(pendingEmptyCells.toInt()) { cells += EMPTY_POSITIONAL_CELL }
                        pendingEmptyCells = 0L
                        repeat(cellRepeat) { cells += cell }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.isEndTag(TABLE_NAMESPACE, ROW_TAG)) {
                    // Empty repeated cells still pending here are only trailing spreadsheet padding.
                    return RawOdsRow(cells = cells)
                }

                XmlPullParser.END_DOCUMENT -> throw MalformedOdsXmlException()
            }
        }
    }

    private fun parseCell(parser: XmlPullParser): RawOdsCell {
        val declaredType = parser.getAttributeValue(OFFICE_NAMESPACE, VALUE_TYPE_ATTRIBUTE)
        val numericValue = parser.getAttributeValue(OFFICE_NAMESPACE, VALUE_ATTRIBUTE)
        val stringValue = parser.getAttributeValue(OFFICE_NAMESPACE, STRING_VALUE_ATTRIBUTE)
        val currencyCode = parser.getAttributeValue(OFFICE_NAMESPACE, CURRENCY_ATTRIBUTE)
        val paragraphs = mutableListOf<String>()
        var textCharacterCount = 0

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.isStartTag(TEXT_NAMESPACE, PARAGRAPH_TAG)) {
                    val paragraph = parseParagraph(parser)
                    val separatorLength = if (paragraphs.isEmpty()) 0 else 1
                    textCharacterCount += separatorLength + paragraph.length
                    if (textCharacterCount > limits.maxTextCharactersPerCell) {
                        throw OdsExpansionLimitException(
                            "Il testo di una cella supera il limite di " +
                                "${limits.maxTextCharactersPerCell} caratteri.",
                        )
                    }
                    paragraphs += paragraph
                }

                XmlPullParser.END_TAG -> if (parser.isEndTag(TABLE_NAMESPACE, CELL_TAG)) {
                    val text = if (paragraphs.isEmpty()) {
                        stringValue.orEmpty()
                    } else {
                        paragraphs.joinToString(separator = "\n")
                    }
                    val rawValue = numericValue ?: stringValue
                    val kind = when {
                        text.isEmpty() && rawValue == null -> RawOdsCellKind.EMPTY
                        declaredType == STRING_VALUE_TYPE -> RawOdsCellKind.TEXT
                        declaredType == FLOAT_VALUE_TYPE -> RawOdsCellKind.NUMBER
                        declaredType == CURRENCY_VALUE_TYPE -> RawOdsCellKind.CURRENCY
                        declaredType == null && text.isNotEmpty() -> RawOdsCellKind.TEXT
                        else -> RawOdsCellKind.OTHER
                    }
                    return RawOdsCell(
                        kind = kind,
                        text = text,
                        rawValue = rawValue,
                        currencyCode = currencyCode,
                    )
                }

                XmlPullParser.END_DOCUMENT -> throw MalformedOdsXmlException()
            }
        }
    }

    private fun parseParagraph(parser: XmlPullParser): String {
        val text = StringBuilder()

        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                    text.appendWithinLimit(parser.text.orEmpty())

                XmlPullParser.START_TAG -> when {
                    parser.isStartTag(TEXT_NAMESPACE, SPACE_TAG) -> {
                        val count = parser.getAttributeValue(TEXT_NAMESPACE, SPACE_COUNT_ATTRIBUTE)
                            ?.toPositiveIntOrNull()
                            ?: 1
                        if (text.length.toLong() + count > limits.maxTextCharactersPerCell.toLong()) {
                            throw OdsExpansionLimitException(
                                "Il testo di una cella supera il limite di " +
                                    "${limits.maxTextCharactersPerCell} caratteri.",
                            )
                        }
                        repeat(count) { text.append(' ') }
                    }

                    parser.isStartTag(TEXT_NAMESPACE, TAB_TAG) -> text.appendWithinLimit("\t")
                    parser.isStartTag(TEXT_NAMESPACE, LINE_BREAK_TAG) -> text.appendWithinLimit("\n")
                }

                XmlPullParser.END_TAG -> if (parser.isEndTag(TEXT_NAMESPACE, PARAGRAPH_TAG)) {
                    return text.toString()
                }

                XmlPullParser.END_DOCUMENT -> throw MalformedOdsXmlException()
            }
        }
    }

    private fun StringBuilder.appendWithinLimit(value: String) {
        if (length.toLong() + value.length > limits.maxTextCharactersPerCell.toLong()) {
            throw OdsExpansionLimitException(
                "Il testo di una cella supera il limite di " +
                    "${limits.maxTextCharactersPerCell} caratteri.",
            )
        }
        append(value)
    }

    private fun XmlPullParser.positiveRepeat(attributeName: String): Int {
        val rawValue = getAttributeValue(TABLE_NAMESPACE, attributeName) ?: return 1
        return rawValue.toPositiveIntOrNull()
            ?: throw MalformedOdsXmlException(
                IllegalArgumentException("$attributeName deve essere un intero positivo."),
            )
    }

    private fun openZip(input: InputStream): ZipInputStream {
        val pushbackInput = PushbackInputStream(input, ZIP_HEADER_SIZE)
        val header = ByteArray(ZIP_HEADER_SIZE)
        var bytesRead = 0
        while (bytesRead < header.size) {
            val count = pushbackInput.read(header, bytesRead, header.size - bytesRead)
            if (count < 0) break
            bytesRead += count
        }

        if (bytesRead != ZIP_HEADER_SIZE || !header.isZipLocalFileHeader()) {
            throw MalformedOdsArchiveException()
        }
        pushbackInput.unread(header)
        return ZipInputStream(pushbackInput)
    }

    private fun ByteArray.isZipLocalFileHeader(): Boolean =
        this[0] == 0x50.toByte() &&
            this[1] == 0x4B.toByte() &&
            this[2] == 0x03.toByte() &&
            this[3] == 0x04.toByte()

    private fun String.normalizedZipPath(): String = replace('\\', '/').removePrefix("./")

    private fun String.toPositiveIntOrNull(): Int? =
        toIntOrNull()?.takeIf { value -> value > 0 }

    private fun Throwable.findOdsMenuParseException(): OdsMenuParseException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is OdsMenuParseException) return current
            current = current.cause
        }
        return null
    }

    private fun XmlPullParser.isStartTag(namespace: String, localName: String): Boolean =
        eventType == XmlPullParser.START_TAG && this.namespace == namespace && name == localName

    private fun XmlPullParser.isEndTag(namespace: String, localName: String): Boolean =
        eventType == XmlPullParser.END_TAG && this.namespace == namespace && name == localName

    private data class ParsedSheet(
        val sheet: RawOdsSheet,
        val totalCells: Long,
    )

    private class LimitedInputStream(
        input: InputStream,
        private val maxBytes: Long,
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            if (bytesRead == maxBytes) {
                if (super.read() < 0) return -1
                throw OdsExpansionLimitException("content.xml supera il limite di $maxBytes byte.")
            }
            return super.read().also { value ->
                if (value >= 0) bytesRead++
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val remaining = maxBytes - bytesRead
            if (remaining == 0L) return read()
            val allowed = minOf(length.toLong(), remaining).toInt()
            return super.read(buffer, offset, allowed).also { count ->
                if (count > 0) bytesRead += count.toLong()
            }
        }
    }

    private companion object {
        const val CONTENT_XML_PATH = "content.xml"
        const val ZIP_HEADER_SIZE = 4

        const val TABLE_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        const val TEXT_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        const val OFFICE_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"

        const val TABLE_TAG = "table"
        const val ROW_TAG = "table-row"
        const val CELL_TAG = "table-cell"
        const val PARAGRAPH_TAG = "p"
        const val SPACE_TAG = "s"
        const val TAB_TAG = "tab"
        const val LINE_BREAK_TAG = "line-break"

        const val NAME_ATTRIBUTE = "name"
        const val ROWS_REPEATED_ATTRIBUTE = "number-rows-repeated"
        const val COLUMNS_REPEATED_ATTRIBUTE = "number-columns-repeated"
        const val VALUE_TYPE_ATTRIBUTE = "value-type"
        const val VALUE_ATTRIBUTE = "value"
        const val STRING_VALUE_ATTRIBUTE = "string-value"
        const val CURRENCY_ATTRIBUTE = "currency"
        const val SPACE_COUNT_ATTRIBUTE = "c"

        const val STRING_VALUE_TYPE = "string"
        const val FLOAT_VALUE_TYPE = "float"
        const val CURRENCY_VALUE_TYPE = "currency"

        val EMPTY_POSITIONAL_CELL = RawOdsCell(
            kind = RawOdsCellKind.EMPTY,
            text = "",
            rawValue = null,
            currencyCode = null,
        )
    }
}

private fun RawOdsRow.isSemanticallyEmpty(): Boolean = cells.all(RawOdsCell::isSemanticallyEmpty)

private fun RawOdsCell.isSemanticallyEmpty(): Boolean =
    text.isEmpty() && rawValue == null && currencyCode == null
