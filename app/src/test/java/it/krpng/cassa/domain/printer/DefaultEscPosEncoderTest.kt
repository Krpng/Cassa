package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRINT-005 / D-052 byte-for-byte EscPosEncoder tests.
 * No hardware; no numbered PRINT-T claims.
 */
class DefaultEscPosEncoderTest {
    private val encoder = DefaultEscPosEncoder()

    @Test
    fun `empty document is ESC at then final NORMAL then feed then optional cut`() {
        val result =
            success(
                document(emptyList()),
                profile(feedLines = 0, supportsCut = false),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x1B, 0x45, 0x00),
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
    fun `NORMAL line has no emphasis transition and ends with final NORMAL reset`() {
        val bytes =
            success(
                document(listOf(line("Hi", PrintEmphasis.NORMAL))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(
                0x1B, 0x40,
                'H'.code, 'i'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun `EMPHASIZED line emits ESC E 1 then final ESC E 0`() {
        val bytes =
            success(
                document(listOf(line("Hi", PrintEmphasis.EMPHASIZED))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(
                0x1B, 0x40,
                0x1B, 0x45, 0x01,
                'H'.code, 'i'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
            bytes(
                0x1B, 0x40,
                'A'.code, 0x0A,
                0x1B, 0x45, 0x01,
                'B'.code, 0x0A,
                0x1B, 0x45, 0x00,
                'C'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun `document ending EMPHASIZED still forces final NORMAL`() {
        val bytes =
            success(
                document(listOf(line("X", PrintEmphasis.EMPHASIZED))),
                profile(feedLines = 0),
            )
        val tail = bytes.copyOfRange(bytes.size - 3, bytes.size)
        assertArrayEquals(bytes(0x1B, 0x45, 0x00), tail)
    }

    @Test
    fun `LF after every PrintableLine including last`() {
        val bytes =
            success(
                document(listOf(line("A"), line("B"))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(
                0x1B, 0x40,
                'A'.code, 0x0A,
                'B'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x0A, 0x1B, 0x45, 0x00),
            bytes,
        )
    }

    @Test
    fun `multiple empty lines emit one LF each`() {
        val bytes =
            success(
                document(listOf(line(""), line(""))),
                profile(feedLines = 0),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x0A, 0x0A, 0x1B, 0x45, 0x00),
            bytes,
        )
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
            bytes(
                0x1B, 0x40,
                'l'.code, '\''.code, 'a'.code, 'c'.code, 'q'.code, 'u'.code, 'a'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
            bytes(
                0x1B, 0x40,
                '"'.code, 'c'.code, 'i'.code, 'a'.code, 'o'.code, '"'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
            bytes(
                0x1B, 0x40,
                'a'.code, '-'.code, 'b'.code, '-'.code, 'c'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
        // windows-1252 maps € to 0x80
        assertArrayEquals(
            bytes(0x1B, 0x40, '7'.code, 0x80, 0x0A, 0x1B, 0x45, 0x00),
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
            bytes(
                0x1B, 0x40,
                '7'.code, 'E'.code, 'U'.code, 'R'.code, 0x0A,
                0x1B, 0x45, 0x00,
            ),
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
        // Ensure success path never silently used '?' for this input.
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
            bytes(
                0x1B, 0x40,
                'c'.code, 'a'.code, 'f'.code, 'f'.code, 0xE8, 0x0A,
                0x1B, 0x45, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun `feedLines zero adds no extra LF after final reset`() {
        val bytes = success(document(emptyList()), profile(feedLines = 0))
        assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x45, 0x00), bytes)
    }

    @Test
    fun `feedLines positive appends that many LF after final reset`() {
        val bytes = success(document(emptyList()), profile(feedLines = 3))
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x1B, 0x45, 0x00, 0x0A, 0x0A, 0x0A),
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
        assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x45, 0x00), bytes)
    }

    @Test
    fun `FULL cut emits GS V 0 after feed`() {
        val bytes =
            success(
                document(emptyList()),
                profile(feedLines = 1, supportsCut = true, cutVariant = "FULL"),
            )
        assertArrayEquals(
            bytes(0x1B, 0x40, 0x1B, 0x45, 0x00, 0x0A, 0x1D, 0x56, 0x00),
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
            bytes(0x1B, 0x40, 0x1B, 0x45, 0x00, 0x1D, 0x56, 0x01),
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
    fun `does not emit ESC t code page select`() {
        val bytes = success(document(listOf(line("A"))), profile())
        // ESC t = 1B 74
        for (i in 0 until bytes.size - 1) {
            assertTrue(!(bytes[i] == 0x1B.toByte() && bytes[i + 1] == 0x74.toByte()))
        }
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
    ): PrintableLine = PrintableLine(text = text, emphasis = emphasis)

    private fun profile(
        codePage: String = "ISO-8859-1",
        feedLines: Int = 0,
        supportsCut: Boolean = false,
        cutVariant: String? = null,
    ): PrinterProfile =
        PrinterProfile(
            id = "p1",
            name = "test",
            charsPerLine = 32,
            codePage = codePage,
            feedLines = feedLines,
            supportsCut = supportsCut,
            cutCommandVariant = cutVariant,
        )

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { i -> values[i].toByte() }
}
