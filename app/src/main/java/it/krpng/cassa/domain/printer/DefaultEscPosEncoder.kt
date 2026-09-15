package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.UnsupportedCharsetException

/**
 * Generic M8 ESC/POS encoder implementation (PRINT-005 / D-052).
 * Deterministic, stateless; no ESC t / align / double-size / underline.
 */
class DefaultEscPosEncoder : EscPosEncoder {
    override fun encode(
        document: PrintableDocument,
        profile: PrinterProfile,
    ): EncodeResult {
        if (profile.feedLines < 0) {
            return EncodeResult.Failure(EncodeError.InvalidProfile)
        }
        val cutBytes: ByteArray? =
            when {
                !profile.supportsCut -> null
                profile.cutCommandVariant == CUT_FULL -> CUT_FULL_BYTES
                profile.cutCommandVariant == CUT_PARTIAL -> CUT_PARTIAL_BYTES
                else -> return EncodeResult.Failure(EncodeError.InvalidProfile)
            }

        val charset =
            try {
                Charset.forName(profile.codePage)
            } catch (_: UnsupportedCharsetException) {
                return EncodeResult.Failure(EncodeError.UnsupportedEncoding)
            } catch (_: IllegalArgumentException) {
                return EncodeResult.Failure(EncodeError.UnsupportedEncoding)
            }

        val out = ByteArrayOutputStream()
        out.write(INIT_BYTES)

        var emphasis = PrintEmphasis.NORMAL
        for (line in document.lines) {
            if (line.emphasis != emphasis) {
                out.write(emphasisCommand(line.emphasis))
                emphasis = line.emphasis
            }
            when (val encoded = encodeLineText(line.text, charset)) {
                is LineEncode.Ok -> out.write(encoded.bytes)
                is LineEncode.Fail -> return EncodeResult.Failure(encoded.error)
            }
            out.write(LF)
        }

        // Deterministic final NORMAL reset (always, even if already NORMAL).
        out.write(EMPHASIS_OFF)
        repeat(profile.feedLines) { out.write(LF) }
        if (cutBytes != null) {
            out.write(cutBytes)
        }

        return EncodeResult.Success(out.toByteArray())
    }

    private fun emphasisCommand(emphasis: PrintEmphasis): ByteArray =
        when (emphasis) {
            PrintEmphasis.NORMAL -> EMPHASIS_OFF
            PrintEmphasis.EMPHASIZED -> EMPHASIS_ON
        }

    private fun encodeLineText(
        text: String,
        charset: Charset,
    ): LineEncode {
        val normalized = normalizeForPrint(text)
        val withEuro = replaceEuroIfUnencodable(normalized, charset)
        return encodeWithReport(withEuro, charset)
    }

    private fun normalizeForPrint(text: String): String {
        if (text.isEmpty()) return text
        val builder = StringBuilder(text.length)
        for (ch in text) {
            builder.append(
                when (ch) {
                    '\u2018', '\u2019', '\u201A', '\u2032' -> '\''
                    '\u201C', '\u201D', '\u201E', '\u2033' -> '"'
                    '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212' -> '-'
                    else -> ch
                },
            )
        }
        return builder.toString()
    }

    private fun replaceEuroIfUnencodable(
        text: String,
        charset: Charset,
    ): String {
        if (!text.contains(EURO)) return text
        val encoder =
            charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        if (encoder.canEncode(EURO)) return text
        return text.replace(EURO.toString(), EURO_FALLBACK)
    }

    private fun encodeWithReport(
        text: String,
        charset: Charset,
    ): LineEncode {
        if (text.isEmpty()) return LineEncode.Ok(ByteArray(0))
        val encoder =
            charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val encoded = encoder.encode(CharBuffer.wrap(text))
            LineEncode.Ok(toByteArray(encoded))
        } catch (_: CharacterCodingException) {
            LineEncode.Fail(EncodeError.UnencodableCharacter)
        }
    }

    private fun toByteArray(buffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private sealed interface LineEncode {
        class Ok(
            val bytes: ByteArray,
        ) : LineEncode

        class Fail(
            val error: EncodeError,
        ) : LineEncode
    }

    companion object {
        private const val LF: Int = 0x0A
        private const val EURO = '\u20AC'
        private const val EURO_FALLBACK = "EUR"
        private const val CUT_FULL = "FULL"
        private const val CUT_PARTIAL = "PARTIAL"

        private val INIT_BYTES: ByteArray = byteArrayOf(0x1B, 0x40)
        private val EMPHASIS_OFF: ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
        private val EMPHASIS_ON: ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
        private val CUT_FULL_BYTES: ByteArray = byteArrayOf(0x1D, 0x56, 0x00)
        private val CUT_PARTIAL_BYTES: ByteArray = byteArrayOf(0x1D, 0x56, 0x01)
    }
}
