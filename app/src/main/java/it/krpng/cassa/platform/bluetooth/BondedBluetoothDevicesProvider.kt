package it.krpng.cassa.platform.bluetooth

/**
 * Android-facing bonded-device listing (D-056 / BT-002).
 *
 * Bonded/paired only — no discovery, pairing, RFCOMM, or enabled-state checks.
 */
interface BondedBluetoothDevicesProvider {
    fun listBondedDevices(): BondedDevicesResult
}
