package it.krpng.cassa.platform.bluetooth

/**
 * Android-facing Bluetooth **runtime** permission evaluation (D-055 / BT-001).
 *
 * Does not present UI, request dialogs, or inspect Bluetooth adapter state.
 * Discovery / SCAN / location are out of scope (bonded-only MVP).
 */
interface BluetoothPermissionManager {
    /** Runtime permissions required on the current device SDK (may be empty). */
    fun requiredRuntimePermissions(): List<String>

    /** Subset of [requiredRuntimePermissions] that are not currently granted. */
    fun missingRuntimePermissions(): List<String>

    /** True when every required runtime permission is granted (true if none required). */
    fun areRequiredRuntimePermissionsGranted(): Boolean

    /** True when the UI/platform layer should launch a permission request. */
    fun isRuntimePermissionRequestNeeded(): Boolean
}
