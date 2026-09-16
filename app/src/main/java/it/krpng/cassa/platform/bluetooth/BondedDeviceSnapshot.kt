package it.krpng.cassa.platform.bluetooth

/**
 * Platform-internal bonded snapshot before app-safe mapping (D-056 / BT-002).
 * Keeps Android [android.bluetooth.BluetoothDevice] out of the provider result.
 */
data class BondedDeviceSnapshot(
    val address: String,
    val name: String?,
)
