package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPermissionRequestabilityTest {
    private val connect = "android.permission.BLUETOOTH_CONNECT"

    @Test
    fun `A initial missing permission is requestable even when rationale is false`() {
        assertTrue(
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = listOf(connect),
                alreadyRequestedByUi = false,
                shouldShowRationale = { false },
            ),
        )
    }

    @Test
    fun `B never requested remains requestable for first launch`() {
        assertTrue(
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = listOf(connect),
                alreadyRequestedByUi = false,
                shouldShowRationale = { true },
            ),
        )
    }

    @Test
    fun `C after request with rationale still requestable`() {
        assertTrue(
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = listOf(connect),
                alreadyRequestedByUi = true,
                shouldShowRationale = { it == connect },
            ),
        )
    }

    @Test
    fun `D after request without rationale is not requestable`() {
        assertFalse(
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = listOf(connect),
                alreadyRequestedByUi = true,
                shouldShowRationale = { false },
            ),
        )
    }

    @Test
    fun `empty missing is never requestable`() {
        assertFalse(
            BluetoothPermissionRequestability.isRuntimeRequestPossible(
                missingPermissions = emptyList(),
                alreadyRequestedByUi = false,
                shouldShowRationale = { true },
            ),
        )
    }
}
