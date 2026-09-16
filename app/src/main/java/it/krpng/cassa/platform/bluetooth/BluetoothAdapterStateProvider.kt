package it.krpng.cassa.platform.bluetooth

/**
 * Thin adapter availability check for BT-006 settings UI (D-062).
 *
 * Not RFCOMM transport. Does not discover, pair, or list bonded devices.
 */
fun interface BluetoothAdapterStateProvider {
    fun currentAvailability(): BluetoothAdapterAvailability
}
