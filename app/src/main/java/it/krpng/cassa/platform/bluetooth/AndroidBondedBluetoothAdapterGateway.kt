package it.krpng.cassa.platform.bluetooth

import android.bluetooth.BluetoothAdapter

/**
 * Reads [BluetoothAdapter.getBondedDevices] only (D-056 / BT-002).
 *
 * Does not call discovery APIs and does not evaluate [BluetoothAdapter.isEnabled].
 */
class AndroidBondedBluetoothAdapterGateway(
    private val adapterProvider: () -> BluetoothAdapter?,
) : BondedBluetoothAdapterGateway {
    override fun readBondedSnapshots(): List<BondedDeviceSnapshot>? {
        val adapter = adapterProvider() ?: return null
        val bonded = adapter.bondedDevices
        return bonded.map { device ->
            BondedDeviceSnapshot(
                address = device.address,
                name = device.name,
            )
        }
    }
}
