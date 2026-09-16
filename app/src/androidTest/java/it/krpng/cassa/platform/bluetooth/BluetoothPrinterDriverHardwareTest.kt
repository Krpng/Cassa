package it.krpng.cassa.platform.bluetooth

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.DefaultEscPosEncoder
import it.krpng.cassa.domain.printer.DefaultPrinterService
import it.krpng.cassa.domain.printer.EncodeResult
import it.krpng.cassa.domain.printer.PrinterResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHONE + NETUM hardware gate for BT-004 / D-058 (secure RFCOMM/SPP).
 *
 * PRECONDITIONS:
 * - Pass instrumentation argument `printerId` = bonded Bluetooth address
 *   (same id as [BondedBluetoothDevice.id] / selected printer address).
 * - Device already paired in Android Settings (no discovery/pairing here).
 * - Bluetooth on; BLUETOOTH_CONNECT granted on API 31+.
 *
 * Exercises production [AndroidBluetoothPrinterDriverFactory] /
 * [AndroidBluetoothPrinterDriver] only. Does not validate HW-001 calibration
 * or observe paper eject (that remains a manual check after this test).
 *
 * Example:
 * `adb shell am instrument -w -e printerId AA:BB:CC:DD:EE:FF ...`
 */
@RunWith(AndroidJUnit4::class)
class BluetoothPrinterDriverHardwareTest {
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
    fun secureRfcomm_connectPrintDisconnect_succeedsWithEncodedTestPayload() {
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

            // Same deterministic M8 TEST profile field set as
            // DefaultPrinterServiceTest.testProfile (charsPerLine=32, ISO-8859-1, feedLines=2).
            // Not a NETUM hardware calibration — HW-001 owns that.
            val profile =
                PrinterProfile(
                    id = printerId,
                    name = "Test",
                    charsPerLine = 32,
                    codePage = "ISO-8859-1",
                    feedLines = 2,
                    pricePrintMode = PricePrintMode.DETAILED,
                )

            val document = DefaultPrinterService.testPrintDocument()
            val encoded = DefaultEscPosEncoder().encode(document, profile)
            assertTrue(
                "Expected EncodeResult.Success for M8 testPrintDocument + test profile, got: $encoded",
                encoded is EncodeResult.Success,
            )
            val bytes = (encoded as EncodeResult.Success).bytes
            assertTrue("Encoded test payload must be non-empty", bytes.isNotEmpty())

            val driver = AndroidBluetoothPrinterDriverFactory.create(context)

            val connectResult = driver.connect(profile)
            assertEquals(
                "Secure RFCOMM/SPP connect must succeed for printerId=$printerId; got: $connectResult",
                PrinterResult.Success,
                connectResult,
            )

            val printResult = driver.print(bytes)
            assertEquals(
                "Raw encoded byte write/flush must succeed; got: $printResult",
                PrinterResult.Success,
                printResult,
            )

            driver.disconnect()
            assertEquals(
                "Disconnect after successful print must leave driver disconnected",
                false,
                driver.isConnected,
            )

            Log.i(
                TAG,
                "BT-004 hardware transport PASS for printerId=$printerId. " +
                    "MANUAL CHECK: confirm NETUM ejected paper showing TEST STAMPANTE / Cassa. " +
                    "CharsPerLine/codePage/feed calibration = HW-001 (not this gate).",
            )
        }
    }

    companion object {
        private const val TAG = "BT004RfcommHardwareTest"
        const val ARG_PRINTER_ID: String = "printerId"
    }
}
