package it.krpng.cassa.platform.bluetooth

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHONE ONLY manual gate for BT-002 / D-056.
 *
 * PRECONDITION: the physical Android device must have at least one already
 * paired/bonded Bluetooth device (Android Settings). Pairing is not performed here.
 *
 * Uses production [AndroidBondedBluetoothDevicesProviderFactory] against the real
 * Bluetooth stack — no discovery, SCAN, RFCOMM, or NETUM-specific behavior.
 */
@RunWith(AndroidJUnit4::class)
class BondedBluetoothDevicesAndroidTest {
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
    fun listBondedDevices_returnsSuccessWithAtLeastOneStableId() {
        val permissionManager = AndroidBluetoothPermissionManagerFactory.create(context)
        assertTrue(
            "Required Bluetooth runtime permissions must be granted for this PHONE ONLY gate",
            permissionManager.areRequiredRuntimePermissionsGranted(),
        )

        val provider = AndroidBondedBluetoothDevicesProviderFactory.create(context)
        val result = provider.listBondedDevices()

        assertTrue(
            "Expected BondedDevicesResult.Success from real Android bondedDevices, got: $result",
            result is BondedDevicesResult.Success,
        )

        val devices = (result as BondedDevicesResult.Success).devices
        assertTrue(
            "PRECONDITION: the physical Android device must have at least one already " +
                "paired/bonded Bluetooth device. Open Android Settings → Bluetooth, pair a " +
                "device (any bonded device is enough — NETUM not required), then re-run this test.",
            devices.isNotEmpty(),
        )

        devices.forEach { device ->
            assertTrue(
                "Every bonded device must have a nonblank stable id/address, got: $device",
                device.id.isNotBlank(),
            )
            Log.i(TAG, "Bonded device id=${device.id} name=${device.name}")
        }
    }

    companion object {
        private const val TAG = "BT002BondedAndroidTest"
    }
}
