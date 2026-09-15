package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class PrinterContractsTest {
    @Test
    fun `printer errors match architecture section 23 plus D-054 extensions`() {
        val errors: List<PrinterError> =
            listOf(
                PrinterError.BluetoothDisabled,
                PrinterError.PermissionDenied,
                PrinterError.PrinterNotConfigured,
                PrinterError.ConnectionFailed,
                PrinterError.ConnectionLost,
                PrinterError.Timeout,
                PrinterError.PrintFailed,
                PrinterError.UnsupportedEncoding,
                PrinterError.UnencodableCharacter,
                PrinterError.InvalidPrinterProfile,
                PrinterError.OrderNotFound,
                PrinterError.InvalidOrderState,
                PrinterError.Unknown,
            )

        assertEquals(
            listOf(
                "BluetoothDisabled",
                "PermissionDenied",
                "PrinterNotConfigured",
                "ConnectionFailed",
                "ConnectionLost",
                "Timeout",
                "PrintFailed",
                "UnsupportedEncoding",
                "UnencodableCharacter",
                "InvalidPrinterProfile",
                "OrderNotFound",
                "InvalidOrderState",
                "Unknown",
            ),
            errors.map { it::class.simpleName },
        )
    }

    @Test
    fun `typed printer and print results are constructible`() {
        assertSame(PrinterResult.Success, PrinterResult.Success)
        val printerFailure = PrinterResult.Failure(PrinterError.Timeout)
        assertEquals(PrinterError.Timeout, printerFailure.error)

        assertSame(PrintResult.Success, PrintResult.Success)
        val printFailure = PrintResult.Failure(PrinterError.PrintFailed)
        assertEquals(PrinterError.PrintFailed, printFailure.error)
    }

    @Test
    fun `PrinterDriver contract compiles with pure Kotlin types only`() {
        val driver: PrinterDriver =
            object : PrinterDriver {
                override suspend fun connect(profile: PrinterProfile): PrinterResult =
                    PrinterResult.Success

                override suspend fun print(data: ByteArray): PrinterResult =
                    PrinterResult.Failure(PrinterError.PrinterNotConfigured)

                override suspend fun disconnect() = Unit
            }

        assertNotNull(driver)
    }

    @Test
    fun `PrinterService contract compiles with orderId String and PrintResult`() {
        val service: PrinterService =
            object : PrinterService {
                override suspend fun printDraft(orderId: String): PrintResult =
                    PrintResult.Success

                override suspend fun printAccepted(orderId: String): PrintResult =
                    PrintResult.Failure(PrinterError.ConnectionFailed)

                override suspend fun testPrint(): PrintResult =
                    PrintResult.Failure(PrinterError.Unknown)
            }

        assertNotNull(service)
    }
}
