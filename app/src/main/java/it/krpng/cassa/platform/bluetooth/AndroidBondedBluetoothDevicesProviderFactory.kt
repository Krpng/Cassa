package it.krpng.cassa.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Factory for a production [BondedBluetoothDevicesProvider] (D-056 / BT-002).
 *
 * No Hilt binding yet — no runtime UI consumer in this task.
 * Wired when a later consumer (BT-006+) needs it.
 */
object AndroidBondedBluetoothDevicesProviderFactory {
    fun create(context: Context): BondedBluetoothDevicesProvider {
        val appContext = context.applicationContext
        val permissionManager = AndroidBluetoothPermissionManagerFactory.create(appContext)
        val gateway =
            AndroidBondedBluetoothAdapterGateway(
                adapterProvider = {
                    val manager =
                        appContext.getSystemService(BluetoothManager::class.java)
                    manager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                },
            )
        return DefaultBondedBluetoothDevicesProvider(
            permissionManager = permissionManager,
            adapterGateway = gateway,
        )
    }
}
