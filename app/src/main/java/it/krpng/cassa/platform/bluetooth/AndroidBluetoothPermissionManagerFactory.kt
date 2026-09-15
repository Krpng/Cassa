package it.krpng.cassa.platform.bluetooth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Factory for a production [BluetoothPermissionManager] using the device SDK and
 * [Context.checkSelfPermission] (D-055 / BT-001).
 *
 * No Hilt binding yet — first consumer (BT-002+) wires when needed.
 */
object AndroidBluetoothPermissionManagerFactory {
    fun create(context: Context): BluetoothPermissionManager {
        val appContext = context.applicationContext
        return DefaultBluetoothPermissionManager(
            sdkInt = Build.VERSION.SDK_INT,
            isPermissionGranted = { permission ->
                appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
}
