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
 * Generic ESC/POS encoder (PRINT-005 / D-052 / D-060).
 * Deterministic, stateless; connect-path formatting via PrintableLine semantics.
 * Optional physical ESC t from [PrinterProfile.escPosCodeTable] only.
 */
class DefaultEscPosEncoder : EscPosEncoder {
    override fun encode(
        document: PrintableDocument,
        profile: PrinterProfile,
    ): EncodeResult {
        if (profile.feedLines < 0) {
            return EncodeResult.Failure(EncodeError.InvalidProfile)
        }
        val codeTable = profile.escPosCodeTable
        if (codeTable != null && codeTable !in ESC_T_SELECTOR_RANGE) {
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
        if (codeTable != null) {
            out.write(byteArrayOf(0x1B, 0x74, codeTable.toByte()))
        }

        // After ESC @: printer defaults match LEFT / NORMAL emphasis / NORMAL scale.
        var alignment = PrintAlignment.LEFT
        var emphasis = PrintEmphasis.NORMAL
        var textScale = PrintTextScale.NORMAL

        for (line in document.lines) {
            if (line.alignment != alignment) {
                out.write(alignmentCommand(line.alignment))
                alignment = line.alignment
            }
            if (line.emphasis != emphasis) {
                out.write(emphasisCommand(line.emphasis))
                emphasis = line.emphasis
            }
            if (line.textScale != textScale) {
                out.write(textScaleCommand(line.textScale))
                textScale = line.textScale
            }
            when (val encoded = encodeLineText(line.text, charset)) {
                is LineEncode.Ok -> out.write(encoded.bytes)
                is LineEncode.Fail -> return EncodeResult.Failure(encoded.error)
            }
            out.write(LF)
        }

        // Deterministic final reset (always), then feed, then optional cut.
        out.write(ALIGN_LEFT)
        out.write(EMPHASIS_OFF)
        out.write(SCALE_NORMAL)
        repeat(profile.feedLines) { out.write(LF) }
        if (cutBytes != null) {
            out.write(cutBytes)
        }

        return EncodeResult.Success(out.toByteArray())
    }

    private fun alignmentCommand(alignment: PrintAlignment): ByteArray =
        when (alignment) {
            PrintAlignment.LEFT -> ALIGN_LEFT
            PrintAlignment.CENTER -> ALIGN_CENTER
            PrintAlignment.RIGHT -> ALIGN_RIGHT
        }

    private fun emphasisCommand(emphasis: PrintEmphasis): ByteArray =
        when (emphasis) {
            PrintEmphasis.NORMAL -> EMPHASIS_OFF
            PrintEmphasis.EMPHASIZED -> EMPHASIS_ON
        }

    private fun textScaleCommand(textScale: PrintTextScale): ByteArray =
        when (textScale) {
            // D-060 GS ! n (Epson: bits0-3 width, bits4-7 height; 0=1x, 1=2x)
            PrintTextScale.NORMAL -> SCALE_NORMAL
            PrintTextScale.DOUBLE_WIDTH -> SCALE_DOUBLE_WIDTH
            PrintTextScale.DOUBLE_HEIGHT -> SCALE_DOUBLE_HEIGHT
            PrintTextScale.DOUBLE_BOTH -> SCALE_DOUBLE_BOTH
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
        private val ESC_T_SELECTOR_RANGE = 0..255

        private val INIT_BYTES: ByteArray = byteArrayOf(0x1B, 0x40)
        private val ALIGN_LEFT: ByteArray = byteArrayOf(0x1B, 0x61, 0x00)
        private val ALIGN_CENTER: ByteArray = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_RIGHT: ByteArray = byteArrayOf(0x1B, 0x61, 0x02)
        private val EMPHASIS_OFF: ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
        private val EMPHASIS_ON: ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
        private val SCALE_NORMAL: ByteArray = byteArrayOf(0x1D, 0x21, 0x00)
        private val SCALE_DOUBLE_WIDTH: ByteArray = byteArrayOf(0x1D, 0x21, 0x01)
        private val SCALE_DOUBLE_HEIGHT: ByteArray = byteArrayOf(0x1D, 0x21, 0x10)
        private val SCALE_DOUBLE_BOTH: ByteArray = byteArrayOf(0x1D, 0x21, 0x11)
        private val CUT_FULL_BYTES: ByteArray = byteArrayOf(0x1D, 0x56, 0x00)
        private val CUT_PARTIAL_BYTES: ByteArray = byteArrayOf(0x1D, 0x56, 0x01)
    }
}
