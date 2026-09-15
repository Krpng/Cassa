package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRuntimePermissionPolicyTest {
    @Test
    fun `API 31 requires only BLUETOOTH_CONNECT`() {
        assertEquals(
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT),
            BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(31),
        )
    }

    @Test
    fun `API above 31 requires only BLUETOOTH_CONNECT`() {
        assertEquals(
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT),
            BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(36),
        )
    }

    @Test
    fun `API 30 requires no runtime Bluetooth permissions`() {
        assertEquals(
            emptyList<String>(),
            BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(30),
        )
    }

    @Test
    fun `API below 30 within minSdk requires no runtime Bluetooth permissions`() {
        assertEquals(
            emptyList<String>(),
            BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(26),
        )
    }

    @Test
    fun `required list is deterministic with no duplicates or scan or location`() {
        val required = BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(31)
        assertEquals(required, required.distinct())
        assertFalse(required.contains(BluetoothPermissions.BLUETOOTH_SCAN))
        assertFalse(required.contains(BluetoothPermissions.ACCESS_FINE_LOCATION))
        assertFalse(required.contains(BluetoothPermissions.ACCESS_COARSE_LOCATION))

        val pre31 = BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(30)
        assertFalse(pre31.contains(BluetoothPermissions.BLUETOOTH_SCAN))
        assertFalse(pre31.contains(BluetoothPermissions.ACCESS_FINE_LOCATION))
        assertFalse(pre31.contains(BluetoothPermissions.ACCESS_COARSE_LOCATION))
    }
}
