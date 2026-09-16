package it.krpng.cassa.platform.bluetooth

/**
 * Coarse Bluetooth adapter presence/enabled state for settings UI (D-062 / BT-006).
 *
 * Distinct from RFCOMM connect errors ([it.krpng.cassa.domain.printer.PrinterError]).
 * Does not list bonded devices.
 */
enum class BluetoothAdapterAvailability {
    /** No BluetoothAdapter available on this device. */
    UNAVAILABLE,

    /** Adapter present but disabled. */
    DISABLED,

    /** Adapter present and enabled. */
    ENABLED,
}
