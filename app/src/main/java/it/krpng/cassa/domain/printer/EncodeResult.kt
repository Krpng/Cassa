package it.krpng.cassa.domain.printer

/**
 * Result of [EscPosEncoder.encode] (D-052 / PRINT-005).
 * Carries success bytes without reshaping [PrinterResult] / [PrintResult].
 */
sealed interface EncodeResult {
    data class Success(
        val bytes: ByteArray,
    ) : EncodeResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class Failure(
        val error: EncodeError,
    ) : EncodeResult
}
