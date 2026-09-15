package it.krpng.cassa.platform.bluetooth

/**
 * Permission string constants for bonded-only Bluetooth MVP (D-055 / BT-001).
 * Kept as plain strings so JVM unit tests do not depend on Android [Manifest] stubs.
 */
object BluetoothPermissions {
    const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"

    /** Not required by BT-001 / bonded-only MVP — listed only for absence assertions. */
    const val BLUETOOTH_SCAN: String = "android.permission.BLUETOOTH_SCAN"

    const val ACCESS_FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"

    const val ACCESS_COARSE_LOCATION: String = "android.permission.ACCESS_COARSE_LOCATION"
}
