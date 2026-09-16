package it.krpng.cassa.platform.bluetooth

/**
 * Local BT-002 listing failures (D-056).
 *
 * Not [it.krpng.cassa.domain.printer.PrinterError] — do not conflate with transport
 * [it.krpng.cassa.domain.printer.PrinterError.BluetoothDisabled] (BT-004/005).
 */
sealed interface BondedDevicesError {
    data object PermissionDenied : BondedDevicesError

    /** BluetoothAdapter unavailable/null — distinct from empty bonded list and disabled adapter. */
    data object BluetoothUnavailable : BondedDevicesError
}
