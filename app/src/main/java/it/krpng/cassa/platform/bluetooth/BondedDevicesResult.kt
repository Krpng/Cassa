package it.krpng.cassa.platform.bluetooth

/**
 * Outcome of listing bonded Bluetooth devices (D-056 / BT-002).
 * Platform-local — not PrintResult / PrinterResult / PrinterService.
 */
sealed interface BondedDevicesResult {
    data class Success(
        val devices: List<BondedBluetoothDevice>,
    ) : BondedDevicesResult

    data class Failure(
        val error: BondedDevicesError,
    ) : BondedDevicesResult
}
