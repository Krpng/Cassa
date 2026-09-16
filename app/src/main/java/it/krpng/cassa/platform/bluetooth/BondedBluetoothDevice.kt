package it.krpng.cassa.platform.bluetooth

/**
 * App-safe bonded Bluetooth device (D-056 / BT-002).
 *
 * [id] is the Android Bluetooth device address. [name] may be null/blank.
 * No UI placeholder strings — display fallback belongs to BT-006.
 */
data class BondedBluetoothDevice(
    val id: String,
    val name: String?,
)
