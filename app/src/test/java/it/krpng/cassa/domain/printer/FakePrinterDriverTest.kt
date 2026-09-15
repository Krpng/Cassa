package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.PrinterProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRINT-006 / D-053 FakePrinterDriver tests.
 * Owns PRINT-T026 (Fake timeout). Does not claim PRINT-T020/T022.
 */
class FakePrinterDriverTest {
    private val profile =
        PrinterProfile(
            id = "fake-1",
            name = "Fake",
            charsPerLine = 32,
            codePage = "ISO-8859-1",
            feedLines = 0,
        )

    @Test
    fun `initial state is disconnected`() {
        val fake = FakePrinterDriver()
        assertFalse(fake.isConnected)
        assertEquals(0, fake.capturedCount)
        assertNull(fake.lastCapturedPayload)
        assertTrue(fake.capturedPayloads.isEmpty())
    }

    @Test
    fun `connect default success sets connected`() =
        runBlocking {
            val fake = FakePrinterDriver()
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertTrue(fake.isConnected)
        }

    @Test
    fun `connect failure stays disconnected`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.Timeout))
            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                fake.connect(profile),
            )
            assertFalse(fake.isConnected)
        }

    @Test
    fun `repeated connect is idempotent and does not consume queued connect result`() =
        runBlocking {
            val fake = FakePrinterDriver()
            assertEquals(PrinterResult.Success, fake.connect(profile))
            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.ConnectionFailed))
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertTrue(fake.isConnected)
            // Queued failure still pending for a future disconnected connect.
            fake.disconnect()
            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                fake.connect(profile),
            )
            assertFalse(fake.isConnected)
        }

    @Test
    fun `disconnect is idempotent and does not clear history or queues`() =
        runBlocking {
            val fake = FakePrinterDriver()
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertEquals(PrinterResult.Success, fake.print(byteArrayOf(1, 2)))
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrintFailed))

            fake.disconnect()
            assertFalse(fake.isConnected)
            fake.disconnect()
            assertFalse(fake.isConnected)

            assertEquals(1, fake.capturedCount)
            assertArrayEquals(byteArrayOf(1, 2), fake.lastCapturedPayload)

            // Print queue preserved across disconnect; reconnect then consumes it.
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertEquals(
                PrinterResult.Failure(PrinterError.PrintFailed),
                fake.print(byteArrayOf(9)),
            )
            assertTrue(fake.isConnected)
            assertEquals(2, fake.capturedCount)
        }

    @Test
    fun `print while disconnected returns ConnectionLost without capture or queue consume`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.Timeout))
            val result = fake.print(byteArrayOf(7, 8, 9))
            assertEquals(PrinterResult.Failure(PrinterError.ConnectionLost), result)
            assertEquals(0, fake.capturedCount)
            assertNull(fake.lastCapturedPayload)

            // Queue not consumed: after connect, Timeout is returned.
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                fake.print(byteArrayOf(1)),
            )
            assertEquals(1, fake.capturedCount)
        }

    @Test
    fun `connected successful print is captured`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            val payload = byteArrayOf(0x1B, 0x40, 0x0A)
            assertEquals(PrinterResult.Success, fake.print(payload))
            assertEquals(1, fake.capturedCount)
            assertArrayEquals(payload, fake.lastCapturedPayload)
            assertArrayEquals(payload, fake.capturedPayloads.single())
        }

    @Test
    fun `input ByteArray mutation after print does not alter history`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            val input = byteArrayOf(10, 20, 30)
            fake.print(input)
            input[0] = 99
            assertArrayEquals(byteArrayOf(10, 20, 30), fake.lastCapturedPayload)
            assertArrayEquals(byteArrayOf(10, 20, 30), fake.capturedPayloads.single())
        }

    @Test
    fun `observed ByteArray mutation does not alter internal history`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            fake.print(byteArrayOf(1, 2, 3))
            val observedLast = requireNotNull(fake.lastCapturedPayload)
            observedLast[0] = 50
            val observedList = fake.capturedPayloads
            observedList.single()[1] = 60
            assertArrayEquals(byteArrayOf(1, 2, 3), fake.lastCapturedPayload)
            assertArrayEquals(byteArrayOf(1, 2, 3), fake.capturedPayloads.single())
        }

    @Test
    fun `multiple print captures preserve order`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            fake.print(byteArrayOf(1))
            fake.print(byteArrayOf(2, 2))
            fake.print(byteArrayOf(3, 3, 3))
            assertEquals(3, fake.capturedCount)
            assertArrayEquals(byteArrayOf(1), fake.capturedPayloads[0])
            assertArrayEquals(byteArrayOf(2, 2), fake.capturedPayloads[1])
            assertArrayEquals(byteArrayOf(3, 3, 3), fake.capturedPayloads[2])
            assertArrayEquals(byteArrayOf(3, 3, 3), fake.lastCapturedPayload)
        }

    @Test
    fun `FIFO connect injection`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.Timeout))
            fake.enqueueConnectResult(PrinterResult.Success)
            assertEquals(PrinterResult.Failure(PrinterError.Timeout), fake.connect(profile))
            assertFalse(fake.isConnected)
            assertEquals(PrinterResult.Success, fake.connect(profile))
            assertTrue(fake.isConnected)
        }

    @Test
    fun `FIFO print injection`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrintFailed))
            fake.enqueuePrintResult(PrinterResult.Success)
            assertEquals(
                PrinterResult.Failure(PrinterError.PrintFailed),
                fake.print(byteArrayOf(1)),
            )
            assertEquals(PrinterResult.Success, fake.print(byteArrayOf(2)))
            assertEquals(2, fake.capturedCount)
        }

    @Test
    fun `connect Timeout ConnectionFailed and PrinterNotConfigured`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.Timeout))
            assertEquals(PrinterResult.Failure(PrinterError.Timeout), fake.connect(profile))

            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.ConnectionFailed))
            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                fake.connect(profile),
            )

            fake.enqueueConnectResult(PrinterResult.Failure(PrinterError.PrinterNotConfigured))
            assertEquals(
                PrinterResult.Failure(PrinterError.PrinterNotConfigured),
                fake.connect(profile),
            )
            assertFalse(fake.isConnected)
        }

    @Test
    fun `PRINT-T026 Fake timeout on print`() =
        runBlocking {
            // docs/09_TEST_PLAN.md PRINT-T026: Fake timeout.
            val fake = FakePrinterDriver()
            fake.connect(profile)
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.Timeout))
            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                fake.print(byteArrayOf(0x1B, 0x40)),
            )
            assertTrue(fake.isConnected)
            assertEquals(1, fake.capturedCount)
        }

    @Test
    fun `print ConnectionLost disconnects Fake and still captures`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)
            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.ConnectionLost))
            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                fake.print(byteArrayOf(4, 5)),
            )
            assertFalse(fake.isConnected)
            assertArrayEquals(byteArrayOf(4, 5), fake.lastCapturedPayload)
        }

    @Test
    fun `print Timeout PrintFailed PrinterNotConfigured keep connected and capture`() =
        runBlocking {
            val fake = FakePrinterDriver()
            fake.connect(profile)

            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.Timeout))
            assertEquals(PrinterResult.Failure(PrinterError.Timeout), fake.print(byteArrayOf(1)))
            assertTrue(fake.isConnected)

            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrintFailed))
            assertEquals(
                PrinterResult.Failure(PrinterError.PrintFailed),
                fake.print(byteArrayOf(2)),
            )
            assertTrue(fake.isConnected)

            fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.PrinterNotConfigured))
            assertEquals(
                PrinterResult.Failure(PrinterError.PrinterNotConfigured),
                fake.print(byteArrayOf(3)),
            )
            assertTrue(fake.isConnected)
            assertEquals(3, fake.capturedCount)
        }

    @Test
    fun `deterministic repeated scenario`() =
        runBlocking {
            suspend fun runScenario(): Triple<List<PrinterResult>, Boolean, List<ByteArray>> {
                val fake = FakePrinterDriver()
                fake.enqueueConnectResult(PrinterResult.Success)
                fake.enqueuePrintResult(PrinterResult.Failure(PrinterError.Timeout))
                fake.enqueuePrintResult(PrinterResult.Success)
                val results =
                    listOf(
                        fake.connect(profile),
                        fake.print(byteArrayOf(9, 9)),
                        fake.print(byteArrayOf(8)),
                    )
                return Triple(results, fake.isConnected, fake.capturedPayloads)
            }

            val a = runScenario()
            val b = runScenario()
            assertEquals(a.first, b.first)
            assertEquals(a.second, b.second)
            assertEquals(a.third.size, b.third.size)
            a.third.indices.forEach { i ->
                assertArrayEquals(a.third[i], b.third[i])
            }
        }
}
