package it.krpng.cassa.domain.printer

/**
 * Typed printer failures (architecture §23 + D-048).
 * UnsupportedEncoding intentionally omitted — deferred until an encoder task needs it.
 */
sealed interface PrinterError {
    data object BluetoothDisabled : PrinterError

    data object PermissionDenied : PrinterError

    data object PrinterNotConfigured : PrinterError

    data object ConnectionFailed : PrinterError

    data object ConnectionLost : PrinterError

    data object Timeout : PrinterError

    data object PrintFailed : PrinterError

    data object Unknown : PrinterError
}