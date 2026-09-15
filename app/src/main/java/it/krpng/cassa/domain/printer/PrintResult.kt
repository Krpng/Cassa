package it.krpng.cassa.domain.printer

/**
 * Outcome of [PrinterService] operations (D-048).
 * Business flow is typed — raw exceptions are not normal results.
 */
sealed interface PrintResult {
    data object Success : PrintResult

    data class Failure(
        val error: PrinterError,
    ) : PrintResult
}