package it.krpng.cassa.platform.bluetooth

/**
 * Default [BluetoothPermissionManager] (D-055 / BT-001).
 *
 * [sdkInt] and [isPermissionGranted] are injectable so JVM tests cover API 30/31+ without
 * depending on the host [android.os.Build.VERSION.SDK_INT].
 */
class DefaultBluetoothPermissionManager(
    private val sdkInt: Int,
    private val isPermissionGranted: (permission: String) -> Boolean,
) : BluetoothPermissionManager {
    override fun requiredRuntimePermissions(): List<String> =
        BluetoothRuntimePermissionPolicy.requiredRuntimePermissions(sdkInt)

    override fun missingRuntimePermissions(): List<String> =
        requiredRuntimePermissions().filterNot(isPermissionGranted)

    override fun areRequiredRuntimePermissionsGranted(): Boolean =
        missingRuntimePermissions().isEmpty()

    override fun isRuntimePermissionRequestNeeded(): Boolean =
        missingRuntimePermissions().isNotEmpty()
}
