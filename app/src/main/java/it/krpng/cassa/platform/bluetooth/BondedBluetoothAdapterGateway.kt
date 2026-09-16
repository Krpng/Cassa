package it.krpng.cassa.platform.bluetooth

/**
 * Minimal access to the bonded set (D-056 / BT-002).
 *
 * @return `null` when [android.bluetooth.BluetoothAdapter] is unavailable;
 *   otherwise the bonded snapshots (possibly empty).
 * Must **not** evaluate adapter enabled/disabled.
 * May throw [SecurityException] when protected Bluetooth APIs deny access.
 */
fun interface BondedBluetoothAdapterGateway {
    fun readBondedSnapshots(): List<BondedDeviceSnapshot>?
}
