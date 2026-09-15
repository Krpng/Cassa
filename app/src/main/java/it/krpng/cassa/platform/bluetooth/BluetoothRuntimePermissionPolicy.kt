package it.krpng.cassa.platform.bluetooth

/**
 * Pure SDK-level policy for Bluetooth **runtime** permissions (D-055 / BT-001).
 * No Android Context / Build.VERSION — inject [sdkInt] for deterministic JVM tests.
 *
 * API 31+: [BluetoothPermissions.BLUETOOTH_CONNECT] only.
 * API ≤30: empty runtime list (legacy BLUETOOTH is install-time / maxSdk 30).
 */
object BluetoothRuntimePermissionPolicy {
    /** First API level that requires runtime [BluetoothPermissions.BLUETOOTH_CONNECT]. */
    const val CONNECT_RUNTIME_API: Int = 31

    fun requiredRuntimePermissions(sdkInt: Int): List<String> {
        require(sdkInt >= 1) { "sdkInt must be >= 1" }
        return if (sdkInt >= CONNECT_RUNTIME_API) {
            listOf(BluetoothPermissions.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
    }
}
