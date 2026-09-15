package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBluetoothPermissionManagerTest {
    @Test
    fun `API 31 CONNECT granted means all required granted and no request needed`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 31,
                isPermissionGranted = { it == BluetoothPermissions.BLUETOOTH_CONNECT },
            )

        assertEquals(
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT),
            manager.requiredRuntimePermissions(),
        )
        assertTrue(manager.areRequiredRuntimePermissionsGranted())
        assertTrue(manager.missingRuntimePermissions().isEmpty())
        assertFalse(manager.isRuntimePermissionRequestNeeded())
    }

    @Test
    fun `API 31 CONNECT denied means missing only CONNECT`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 31,
                isPermissionGranted = { false },
            )

        assertFalse(manager.areRequiredRuntimePermissionsGranted())
        assertEquals(
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT),
            manager.missingRuntimePermissions(),
        )
        assertTrue(manager.isRuntimePermissionRequestNeeded())
        assertFalse(manager.missingRuntimePermissions().contains(BluetoothPermissions.BLUETOOTH_SCAN))
    }

    @Test
    fun `API above 31 CONNECT denied is same as API 31`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 34,
                isPermissionGranted = { false },
            )

        assertEquals(
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT),
            manager.missingRuntimePermissions(),
        )
    }

    @Test
    fun `pre-31 runtime permission is satisfied without CONNECT grant check`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 30,
                isPermissionGranted = { false },
            )

        assertEquals(emptyList<String>(), manager.requiredRuntimePermissions())
        assertTrue(manager.areRequiredRuntimePermissionsGranted())
        assertTrue(manager.missingRuntimePermissions().isEmpty())
        assertFalse(manager.isRuntimePermissionRequestNeeded())
    }

    @Test
    fun `minSdk 26 runtime permission is satisfied`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 26,
                isPermissionGranted = { false },
            )

        assertTrue(manager.areRequiredRuntimePermissionsGranted())
        assertFalse(manager.isRuntimePermissionRequestNeeded())
    }

    @Test
    fun `required permissions never include scan or location`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 31,
                isPermissionGranted = { false },
            )
        val required = manager.requiredRuntimePermissions()
        assertFalse(required.contains(BluetoothPermissions.BLUETOOTH_SCAN))
        assertFalse(required.contains(BluetoothPermissions.ACCESS_FINE_LOCATION))
        assertFalse(required.contains(BluetoothPermissions.ACCESS_COARSE_LOCATION))
    }
}
