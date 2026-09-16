package it.krpng.cassa.platform.bluetooth

/**
 * Default [BondedBluetoothDevicesProvider] (D-056 / BT-002).
 *
 * Permission check via [BluetoothPermissionManager] before any bonded access.
 * Does not evaluate adapter [android.bluetooth.BluetoothAdapter.isEnabled].
 */
class DefaultBondedBluetoothDevicesProvider(
    private val permissionManager: BluetoothPermissionManager,
    private val adapterGateway: BondedBluetoothAdapterGateway,
) : BondedBluetoothDevicesProvider {
    override fun listBondedDevices(): BondedDevicesResult {
        if (!permissionManager.areRequiredRuntimePermissionsGranted()) {
            return BondedDevicesResult.Failure(BondedDevicesError.PermissionDenied)
        }
        return try {
            val snapshots =
                adapterGateway.readBondedSnapshots()
                    ?: return BondedDevicesResult.Failure(BondedDevicesError.BluetoothUnavailable)
            val devices =
                snapshots
                    .asSequence()
                    .filter { it.address.isNotBlank() }
                    .map { BondedBluetoothDevice(id = it.address, name = it.name) }
                    .toList()
                    .let(BondedBluetoothDeviceOrdering::sort)
            BondedDevicesResult.Success(devices)
        } catch (_: SecurityException) {
            BondedDevicesResult.Failure(BondedDevicesError.PermissionDenied)
        }
    }
}
