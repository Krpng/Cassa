package it.krpng.cassa.domain.printer

/**
 * Outcome of [PrinterDriver] connect/print (D-048).
 * Business flow is typed — raw exceptions are not normal results.
 */
sealed interface PrinterResult {
    data object Success : PrinterResult

    data class Failure(
        val error: PrinterError,
    ) : PrinterResult
}