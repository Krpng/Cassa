package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRINT-005 / D-052 / D-060 byte-for-byte EscPosEncoder tests.
 * No hardware; no numbered PRINT-T claims.
 */
class DefaultEscPosEncoderTest {
    private val encoder = DefaultEscPosEncoder()

    @Test
    fun `empty document is ESC at then final reset then feed then optional cut`() {
        val result =
            success(
                document(emptyList()),
                profile(feedLines = 0, supportsCut = false),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) + FINAL_RESET,
            result,
        )
    }

    @Test
    fun `starts with ESC at initialization`() {
        val bytes = success(document(listOf(line("A"))), profile())
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }

    @Test
    fun `NORMAL line has no emphasis transition and ends with final reset`() {
        val bytes =
            success(
                document(listOf(line("Hi", PrintEmphasis.NORMAL))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('H'.code, 'i'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `EMPHASIZED line emits ESC E 1 then final reset`() {
        val bytes =
            success(
                document(listOf(line("Hi", PrintEmphasis.EMPHASIZED))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes(0x1B, 0x45, 0x01) +
                bytes('H'.code, 'i'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `emphasis transitions NORMAL to EMPHASIZED to NORMAL then final reset`() {
        val bytes =
            success(
                document(
                    listOf(
                        line("A", PrintEmphasis.NORMAL),
                        line("B", PrintEmphasis.EMPHASIZED),
                        line("C", PrintEmphasis.NORMAL),
                    ),
                ),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('A'.code, 0x0A) +
                bytes(0x1B, 0x45, 0x01) +
                bytes('B'.code, 0x0A) +
                bytes(0x1B, 0x45, 0x00) +
                bytes('C'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `document ending EMPHASIZED still forces final NORMAL emphasis`() {
        val bytes =
            success(
                document(listOf(line("X", PrintEmphasis.EMPHASIZED))),
                profile(feedLines = 0),
            )
        val emphasisTail = bytes.copyOfRange(bytes.size - 6, bytes.size - 3)
        assertArrayEquals(bytes(0x1B, 0x45, 0x00), emphasisTail)
    }

    @Test
    fun `LF after every PrintableLine including last`() {
        val bytes =
            success(
                document(listOf(line("A"), line("B"))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('A'.code, 0x0A) +
                bytes('B'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
        assertTrue(bytes.none { it == 0x0D.toByte() })
    }

    @Test
    fun `empty PrintableLine emits only LF`() {
        val bytes =
            success(
                document(listOf(line(""))),
                profile(feedLines = 0),
            )
        assertArrayEquals(bytes(0x1B, 0x40, 0x0A) + FINAL_RESET, bytes)
    }

    @Test
    fun `multiple empty lines emit one LF each`() {
        val bytes =
            success(
                document(listOf(line(""), line(""))),
                profile(feedLines = 0),
            )
        assertArrayEquals(bytes(0x1B, 0x40, 0x0A, 0x0A) + FINAL_RESET, bytes)
    }

    @Test
    fun `deterministic repeated encode yields identical bytes`() {
        val doc = document(listOf(line("Pizza"), line("Totale", PrintEmphasis.EMPHASIZED)))
        val profile = profile(feedLines = 2, supportsCut = true, cutVariant = "FULL")
        val a = success(doc, profile)
        val b = success(doc, profile)
        assertArrayEquals(a, b)
    }

    @Test
    fun `unknown charset returns UnsupportedEncoding`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(codePage = "Totally-Fake-Charset-999"),
            )
        assertEquals(EncodeResult.Failure(EncodeError.UnsupportedEncoding), result)
    }

    @Test
    fun `smart single quotes normalize to ASCII apostrophe`() {
        val bytes =
            success(
                document(listOf(line("l\u2019acqua"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('l'.code, '\''.code, 'a'.code, 'c'.code, 'q'.code, 'u'.code, 'a'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `smart double quotes normalize to ASCII quote`() {
        val bytes =
            success(
                document(listOf(line("\u201Cciao\u201D"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('"'.code, 'c'.code, 'i'.code, 'a'.code, 'o'.code, '"'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `unicode dash variants normalize to ASCII hyphen`() {
        val bytes =
            success(
                document(listOf(line("a\u2013b\u2014c"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('a'.code, '-'.code, 'b'.code, '-'.code, 'c'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `euro is encoded when charset can represent it`() {
        val bytes =
            success(
                document(listOf(line("7\u20AC"))),
                profile(codePage = "windows-1252"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40, '7'.code, 0x80, 0x0A) + FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `euro falls back to EUR when charset cannot represent it`() {
        val bytes =
            success(
                document(listOf(line("7\u20AC"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('7'.code, 'E'.code, 'U'.code, 'R'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `remaining unmappable character returns UnencodableCharacter without question mark`() {
        val result =
            encoder.encode(
                document(listOf(line("ok\uD83D\uDE00"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertEquals(EncodeResult.Failure(EncodeError.UnencodableCharacter), result)
        if (result is EncodeResult.Success) {
            assertTrue(result.bytes.none { it == '?'.code.toByte() })
        }
    }

    @Test
    fun `accented text encodes on compatible charset`() {
        val bytes =
            success(
                document(listOf(line("caffè"))),
                profile(codePage = "ISO-8859-1"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('c'.code, 'a'.code, 'f'.code, 'f'.code, 0xE8, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `feedLines zero adds no extra LF after final reset`() {
        val bytes = success(document(emptyList()), profile(feedLines = 0))
        assertArrayEquals(bytes(0x1B, 0x40) + FINAL_RESET, bytes)
    }

    @Test
    fun `feedLines positive appends that many LF after final reset`() {
        val bytes = success(document(emptyList()), profile(feedLines = 3))
        assertArrayEquals(
            bytes(0x1B, 0x40) + FINAL_RESET + bytes(0x0A, 0x0A, 0x0A),
            bytes,
        )
    }

    @Test
    fun `feedLines negative returns InvalidProfile`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(feedLines = -1),
            )
        assertEquals(EncodeResult.Failure(EncodeError.InvalidProfile), result)
    }

    @Test
    fun `supportsCut false emits no cut bytes and ignores variant`() {
        val bytes =
            success(
                document(emptyList()),
                profile(feedLines = 0, supportsCut = false, cutVariant = "FULL"),
            )
        assertArrayEquals(bytes(0x1B, 0x40) + FINAL_RESET, bytes)
    }

    @Test
    fun `FULL cut emits GS V 0 after feed`() {
        val bytes =
            success(
                document(emptyList()),
                profile(feedLines = 1, supportsCut = true, cutVariant = "FULL"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) + FINAL_RESET + bytes(0x0A, 0x1D, 0x56, 0x00),
            bytes,
        )
    }

    @Test
    fun `PARTIAL cut emits GS V 1 after feed`() {
        val bytes =
            success(
                document(emptyList()),
                profile(feedLines = 0, supportsCut = true, cutVariant = "PARTIAL"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) + FINAL_RESET + bytes(0x1D, 0x56, 0x01),
            bytes,
        )
    }

    @Test
    fun `supportsCut true with missing variant returns InvalidProfile`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(supportsCut = true, cutVariant = null),
            )
        assertEquals(EncodeResult.Failure(EncodeError.InvalidProfile), result)
    }

    @Test
    fun `supportsCut true with unknown variant returns InvalidProfile`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(supportsCut = true, cutVariant = "HALF"),
            )
        assertEquals(EncodeResult.Failure(EncodeError.InvalidProfile), result)
    }

    @Test
    fun `escPosCodeTable null does not emit ESC t`() {
        val bytes = success(document(listOf(line("A"))), profile(escPosCodeTable = null))
        for (i in 0 until bytes.size - 1) {
            assertTrue(!(bytes[i] == 0x1B.toByte() && bytes[i + 1] == 0x74.toByte()))
        }
    }

    @Test
    fun `LEFT CENTER RIGHT emit ESC a 0 1 2 and return to LEFT`() {
        val bytes =
            success(
                document(
                    listOf(
                        line("L", alignment = PrintAlignment.LEFT),
                        line("C", alignment = PrintAlignment.CENTER),
                        line("R", alignment = PrintAlignment.RIGHT),
                        line("Back", alignment = PrintAlignment.LEFT),
                    ),
                ),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('L'.code, 0x0A) +
                bytes(0x1B, 0x61, 0x01) +
                bytes('C'.code, 0x0A) +
                bytes(0x1B, 0x61, 0x02) +
                bytes('R'.code, 0x0A) +
                bytes(0x1B, 0x61, 0x00) +
                bytes('B'.code, 'a'.code, 'c'.code, 'k'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `text scales emit GS excl NORMAL WIDTH HEIGHT BOTH without leakage`() {
        val bytes =
            success(
                document(
                    listOf(
                        line("N", textScale = PrintTextScale.NORMAL),
                        line("W", textScale = PrintTextScale.DOUBLE_WIDTH),
                        line("H", textScale = PrintTextScale.DOUBLE_HEIGHT),
                        line("B", textScale = PrintTextScale.DOUBLE_BOTH),
                        line("back", textScale = PrintTextScale.NORMAL),
                    ),
                ),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes('N'.code, 0x0A) +
                bytes(0x1D, 0x21, 0x01) +
                bytes('W'.code, 0x0A) +
                bytes(0x1D, 0x21, 0x10) +
                bytes('H'.code, 0x0A) +
                bytes(0x1D, 0x21, 0x11) +
                bytes('B'.code, 0x0A) +
                bytes(0x1D, 0x21, 0x00) +
                bytes('b'.code, 'a'.code, 'c'.code, 'k'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `DOUBLE_BOTH then NORMAL clears scale leakage`() {
        val bytes =
            success(
                document(
                    listOf(
                        line("BIG", textScale = PrintTextScale.DOUBLE_BOTH),
                        line("ok", textScale = PrintTextScale.NORMAL),
                    ),
                ),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes(0x1D, 0x21, 0x11) +
                bytes('B'.code, 'I'.code, 'G'.code, 0x0A) +
                bytes(0x1D, 0x21, 0x00) +
                bytes('o'.code, 'k'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `escPosCodeTable valid selector emits ESC t after initialize`() {
        val bytes =
            success(
                document(listOf(line("A"))),
                profile(feedLines = 0, escPosCodeTable = 16),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x1B, 0x74, 16) +
                bytes('A'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    @Test
    fun `escPosCodeTable 0 is accepted`() {
        val bytes =
            success(
                document(listOf(line("A"))),
                profile(feedLines = 0, escPosCodeTable = 0),
            )
        assertEquals(0x1B.toByte(), bytes[2])
        assertEquals(0x74.toByte(), bytes[3])
        assertEquals(0x00.toByte(), bytes[4])
    }

    @Test
    fun `escPosCodeTable 255 is accepted`() {
        val bytes =
            success(
                document(listOf(line("A"))),
                profile(feedLines = 0, escPosCodeTable = 255),
            )
        assertEquals(0x1B.toByte(), bytes[2])
        assertEquals(0x74.toByte(), bytes[3])
        assertEquals(0xFF.toByte(), bytes[4])
    }

    @Test
    fun `escPosCodeTable -1 returns InvalidProfile`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(escPosCodeTable = -1),
            )
        assertEquals(EncodeResult.Failure(EncodeError.InvalidProfile), result)
    }

    @Test
    fun `escPosCodeTable 256 returns InvalidProfile`() {
        val result =
            encoder.encode(
                document(listOf(line("A"))),
                profile(escPosCodeTable = 256),
            )
        assertEquals(EncodeResult.Failure(EncodeError.InvalidProfile), result)
    }

    @Test
    fun `CENTER EMPHASIZED DOUBLE_BOTH are independent on one line`() {
        val bytes =
            success(
                document(
                    listOf(
                        line(
                            text = "X",
                            emphasis = PrintEmphasis.EMPHASIZED,
                            alignment = PrintAlignment.CENTER,
                            textScale = PrintTextScale.DOUBLE_BOTH,
                        ),
                    ),
                ),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40) +
                bytes(0x1B, 0x61, 0x01) +
                bytes(0x1B, 0x45, 0x01) +
                bytes(0x1D, 0x21, 0x11) +
                bytes('X'.code, 0x0A) +
                FINAL_RESET,
            bytes,
        )
    }

    // --- helpers ---

    private fun success(
        document: PrintableDocument,
        profile: PrinterProfile,
    ): ByteArray {
        val result = encoder.encode(document, profile)
        assertTrue("expected Success but was $result", result is EncodeResult.Success)
        return (result as EncodeResult.Success).bytes
    }

    private fun document(lines: List<PrintableLine>): PrintableDocument =
        PrintableDocument(kind = PrintKind.FINAL, lines = lines)

    private fun line(
        text: String,
        emphasis: PrintEmphasis = PrintEmphasis.NORMAL,
        alignment: PrintAlignment = PrintAlignment.LEFT,
        textScale: PrintTextScale = PrintTextScale.NORMAL,
    ): PrintableLine =
        PrintableLine(
            text = text,
            emphasis = emphasis,
            alignment = alignment,
            textScale = textScale,
        )

    private fun profile(
        codePage: String = "ISO-8859-1",
        feedLines: Int = 0,
        supportsCut: Boolean = false,
        cutVariant: String? = null,
        escPosCodeTable: Int? = null,
    ): PrinterProfile =
        PrinterProfile(
            id = "p1",
            name = "test",
            charsPerLine = 32,
            codePage = codePage,
            feedLines = feedLines,
            supportsCut = supportsCut,
            cutCommandVariant = cutVariant,
            escPosCodeTable = escPosCodeTable,
        )

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { i -> values[i].toByte() }

    companion object {
        /** ESC a 0 + ESC E 0 + GS ! 0 — D-060 final formatting reset. */
        private val FINAL_RESET: ByteArray =
            byteArrayOf(
                0x1B, 0x61, 0x00,
                0x1B, 0x45, 0x00,
                0x1D, 0x21, 0x00,
            )
    }
}
