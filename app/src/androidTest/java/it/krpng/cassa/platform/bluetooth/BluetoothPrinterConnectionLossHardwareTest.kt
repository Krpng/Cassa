package it.krpng.cassa.platform.bluetooth

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.DefaultEscPosEncoder
import it.krpng.cassa.domain.printer.EncodeResult
import it.krpng.cassa.domain.printer.PrintEmphasis
import it.krpng.cassa.domain.printer.PrintKind
import it.krpng.cassa.domain.printer.PrintableDocument
import it.krpng.cassa.domain.printer.PrintableLine
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHONE + NETUM human-in-the-loop gate for BT-005 connection-loss observation (D-059).
 *
 * PRECONDITIONS:
 * - Pass instrumentation argument `printerId` = bonded Bluetooth address.
 * - Device already paired in Android Settings (no discovery/pairing here).
 * - Bluetooth on; BLUETOOTH_CONNECT granted on API 31+.
 * - Printer powered on for connect + marker print; operator powers it OFF during the
 *   [OPERATOR_WINDOW_MS] window after the marker.
 *
 * Flow: connect → print marker once → wait 15s (manual) → exactly one post-power-off print
 * → expect [PrinterError.ConnectionLost]. No retry / reconnect / insecure RFCOMM.
 *
 * Does not change D-059 if Android buffers the first write after power-off — collect evidence.
 *
 * Example:
 * `adb shell am instrument -w -e printerId AA:BB:CC:DD:EE:FF ...`
 */
@RunWith(AndroidJUnit4::class)
class BluetoothPrinterConnectionLossHardwareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun grantBluetoothConnectIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        }
    }

    @Test
    fun afterMarker_powerOff_singlePrint_surfacesConnectionLost() {
        runBlocking {
            val printerId =
                InstrumentationRegistry.getArguments().getString(ARG_PRINTER_ID)?.trim().orEmpty()
            assertTrue(
                "PRECONDITION: pass instrumentation argument `$ARG_PRINTER_ID` with the " +
                    "bonded Bluetooth address of the target printer " +
                    "(BondedBluetoothDevice.id). Example: -e $ARG_PRINTER_ID AA:BB:CC:DD:EE:FF. " +
                    "Do not rely on discovery or hardcoded MAC/NETUM names.",
                printerId.isNotEmpty(),
            )

            val permissionManager = AndroidBluetoothPermissionManagerFactory.create(context)
            assertTrue(
                "Required Bluetooth runtime permissions must be granted for this PHONE + NETUM gate",
                permissionManager.areRequiredRuntimePermissionsGranted(),
            )

            // Same deterministic M8 TEST profile field set as BT-004 hardware harness /
            // DefaultPrinterServiceTest.testProfile. Not HW-001 calibration.
            val profile =
                PrinterProfile(
                    id = printerId,
                    name = "Test",
                    charsPerLine = 32,
                    codePage = "ISO-8859-1",
                    feedLines = 2,
                    pricePrintMode = PricePrintMode.DETAILED,
                )

            val encoder = DefaultEscPosEncoder()
            val markerBytes = encodeOrFail(encoder, profile, markerDocument())
            val secondBytes = encodeOrFail(encoder, profile, secondProbeDocument())

            val driver = AndroidBluetoothPrinterDriverFactory.create(context)

            try {
                val connectResult = driver.connect(profile)
                assertEquals(
                    "Secure RFCOMM/SPP connect must succeed for printerId=$printerId; got: $connectResult",
                    PrinterResult.Success,
                    connectResult,
                )

                val markerResult = driver.print(markerBytes)
                assertEquals(
                    "Marker print must succeed before operator window; got: $markerResult",
                    PrinterResult.Success,
                    markerResult,
                )

                Log.i(
                    TAG,
                    "MARKER printed for printerId=$printerId. " +
                        "OPERATOR: power OFF the printer NOW within ${OPERATOR_WINDOW_MS}ms. " +
                        "Do not reconnect. Exactly one post-window print will follow.",
                )

                // Human-in-the-loop only — not a production driver timeout.
                delay(OPERATOR_WINDOW_MS)

                // Exactly one post-power-off print — no retry / reconnect.
                val lossResult = driver.print(secondBytes)
                when (lossResult) {
                    is PrinterResult.Success ->
                        fail(
                            "Expected PrinterError.ConnectionLost after power-off window, " +
                                "but got Success. Transport loss was not surfaced by Android " +
                                "on the first post-power-off write. Do not change production " +
                                "for this observation; collect evidence for a separate decision.",
                        )
                    is PrinterResult.Failure -> {
                        assertEquals(
                            "Expected ConnectionLost after single post-power-off print; " +
                                "got typed failure: ${lossResult.error}",
                            PrinterError.ConnectionLost,
                            lossResult.error,
                        )
                        assertFalse(
                            "Driver must be DISCONNECTED after ConnectionLost",
                            driver.isConnected,
                        )
                        Log.i(
                            TAG,
                            "BT-005 connection-loss observation PASS for printerId=$printerId: " +
                                "single post-power-off print → ConnectionLost.",
                        )
                    }
                }
            } finally {
                driver.disconnect()
            }
        }
    }

    private fun encodeOrFail(
        encoder: DefaultEscPosEncoder,
        profile: PrinterProfile,
        document: PrintableDocument,
    ): ByteArray {
        val encoded = encoder.encode(document, profile)
        assertTrue(
            "Expected EncodeResult.Success for marker/probe document + test profile, got: $encoded",
            encoded is EncodeResult.Success,
        )
        val bytes = (encoded as EncodeResult.Success).bytes
        assertTrue("Encoded payload must be non-empty", bytes.isNotEmpty())
        return bytes
    }

    private fun markerDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("BT-005 TEST", PrintEmphasis.EMPHASIZED),
                    PrintableLine("SPEGNI STAMPANTE ORA", PrintEmphasis.NORMAL),
                ),
        )

    private fun secondProbeDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("BT-005 POST POWER-OFF PROBE", PrintEmphasis.NORMAL),
                ),
        )

    companion object {
        private const val TAG = "BT005ConnectionLossHwTest"
        const val ARG_PRINTER_ID: String = "printerId"

        /** Manual operator window only — not production connect timeout. */
        const val OPERATOR_WINDOW_MS: Long = 15_000L
    }
}
