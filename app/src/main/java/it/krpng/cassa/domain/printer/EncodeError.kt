package it.krpng.cassa.domain.printer

/**
 * Typed ESC/POS encode failures (D-052 / PRINT-005).
 * Mapping to [PrinterError]: one-to-one in [DefaultPrinterService] (D-054 / PRINT-007).
 */
sealed interface EncodeError {
    data object UnsupportedEncoding : EncodeError

    data object UnencodableCharacter : EncodeError

    data object InvalidProfile : EncodeError
}
