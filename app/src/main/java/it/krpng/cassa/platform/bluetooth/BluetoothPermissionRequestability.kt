package it.krpng.cassa.platform.bluetooth

/**
 * UI/platform helper for Bluetooth runtime requestability (D-062 / BT-006).
 *
 * Distinguishes first-request ambiguity from "Android will no longer show a dialog":
 * - never requested → still requestable (even if [shouldShowRationale] is false)
 * - already requested + any missing permission has rationale → requestable
 * - already requested + no rationale → open app settings (non-requestable)
 *
 * Does not own grant checks — use [BluetoothPermissionManager] for those (BT-001).
 */
object BluetoothPermissionRequestability {
    fun isRuntimeRequestPossible(
        missingPermissions: List<String>,
        alreadyRequestedByUi: Boolean,
        shouldShowRationale: (permission: String) -> Boolean,
    ): Boolean {
        if (missingPermissions.isEmpty()) return false
        if (!alreadyRequestedByUi) return true
        return missingPermissions.any(shouldShowRationale)
    }
}
