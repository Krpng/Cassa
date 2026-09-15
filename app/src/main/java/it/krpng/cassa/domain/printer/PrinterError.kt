package it.krpng.cassa.domain.printer

/**
 * Typed printer / print-service failures (architecture §23 + D-048 + D-054).
 */
sealed interface PrinterError {
    data object BluetoothDisabled : PrinterError

    data object PermissionDenied : PrinterError

    data object PrinterNotConfigured : PrinterError

    data object ConnectionFailed : PrinterError

    data object ConnectionLost : PrinterError

    data object Timeout : PrinterError

    data object PrintFailed : PrinterError

    /** Mapped from [EncodeError.UnsupportedEncoding] (D-054). */
    data object UnsupportedEncoding : PrinterError

    /** Mapped from [EncodeError.UnencodableCharacter] (D-054). */
    data object UnencodableCharacter : PrinterError

    /** Mapped from [EncodeError.InvalidProfile] (D-054). */
    data object InvalidPrinterProfile : PrinterError

    /** Order id missing for printDraft/printAccepted (D-054). */
    data object OrderNotFound : PrinterError

    /** Order exists but status is not eligible for the print entry point (D-054). */
    data object InvalidOrderState : PrinterError

    data object Unknown : PrinterError
}
