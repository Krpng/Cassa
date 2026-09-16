package it.krpng.cassa.platform.bluetooth

import android.bluetooth.BluetoothAdapter

/**
 * Production [BluetoothAdapterStateProvider] (D-062 / BT-006).
 *
 * Evaluates presence + [BluetoothAdapter.isEnabled] only.
 * [SecurityException] from a CONNECT revoke race maps to [BluetoothAdapterAvailability.UNAVAILABLE]
 * (cannot safely read enabled state) — never crashes callers.
 */
class AndroidBluetoothAdapterStateProvider(
    private val adapterProvider: () -> BluetoothAdapter?,
) : BluetoothAdapterStateProvider {
    override fun currentAvailability(): BluetoothAdapterAvailability {
        val adapter = adapterProvider()
        return resolveBluetoothAdapterAvailability(
            hasAdapter = adapter != null,
            isEnabled = { requireNotNull(adapter).isEnabled },
        )
    }
}

/**
 * Pure mapping for adapter availability (testable without a real [BluetoothAdapter]).
 */
internal fun resolveBluetoothAdapterAvailability(
    hasAdapter: Boolean,
    isEnabled: () -> Boolean,
): BluetoothAdapterAvailability {
    if (!hasAdapter) return BluetoothAdapterAvailability.UNAVAILABLE
    return try {
        if (isEnabled()) {
            BluetoothAdapterAvailability.ENABLED
        } else {
            BluetoothAdapterAvailability.DISABLED
        }
    } catch (_: SecurityException) {
        BluetoothAdapterAvailability.UNAVAILABLE
    }
}
