package it.krpng.cassa.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Factory for a production [AndroidBluetoothPrinterDriver] (D-058 / D-059 / BT-004 / BT-005).
 *
 * No Hilt binding yet — no UI/PrinterService consumer in this task.
 * Wired when a later consumer (BT-006+ / print integration) needs it.
 * Connect timeout defaults to [AndroidBluetoothPrinterDriver.DEFAULT_CONNECT_TIMEOUT_MS].
 */
object AndroidBluetoothPrinterDriverFactory {
    fun create(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        connectTimeoutMs: Long = AndroidBluetoothPrinterDriver.DEFAULT_CONNECT_TIMEOUT_MS,
    ): AndroidBluetoothPrinterDriver {
        val appContext = context.applicationContext
        val permissionManager = AndroidBluetoothPermissionManagerFactory.create(appContext)
        val gateway =
            AndroidBluetoothRfcommGateway(
                adapterProvider = {
                    val manager =
                        appContext.getSystemService(BluetoothManager::class.java)
                    manager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                },
            )
        return AndroidBluetoothPrinterDriver(
            permissionManager = permissionManager,
            gateway = gateway,
            ioDispatcher = ioDispatcher,
            connectTimeoutMs = connectTimeoutMs,
        )
    }
}
