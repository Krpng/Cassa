package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidBluetoothAdapterStateProviderTest {
    @Test
    fun `null adapter is UNAVAILABLE`() {
        assertEquals(
            BluetoothAdapterAvailability.UNAVAILABLE,
            resolveBluetoothAdapterAvailability(hasAdapter = false, isEnabled = { true }),
        )
    }

    @Test
    fun `enabled adapter is ENABLED`() {
        assertEquals(
            BluetoothAdapterAvailability.ENABLED,
            resolveBluetoothAdapterAvailability(hasAdapter = true, isEnabled = { true }),
        )
    }

    @Test
    fun `disabled adapter is DISABLED`() {
        assertEquals(
            BluetoothAdapterAvailability.DISABLED,
            resolveBluetoothAdapterAvailability(hasAdapter = true, isEnabled = { false }),
        )
    }

    @Test
    fun `SecurityException from isEnabled maps to UNAVAILABLE without crashing`() {
        assertEquals(
            BluetoothAdapterAvailability.UNAVAILABLE,
            resolveBluetoothAdapterAvailability(
                hasAdapter = true,
                isEnabled = { throw SecurityException("BLUETOOTH_CONNECT") },
            ),
        )
    }
}
