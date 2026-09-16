package it.krpng.cassa.platform.bluetooth

import java.io.OutputStream
import java.util.UUID

/**
 * Minimal RFCOMM/SPP transport access for [AndroidBluetoothPrinterDriver] (D-058 / BT-004).
 *
 * No discovery, pairing, or insecure socket APIs.
 * May throw [SecurityException] when protected Bluetooth APIs deny access.
 */
interface BluetoothRfcommGateway {
    /** `null` when [android.bluetooth.BluetoothAdapter] is unavailable. */
    fun adapterOrNull(): BluetoothRfcommAdapterHandle?
}

interface BluetoothRfcommAdapterHandle {
    val isEnabled: Boolean

    /** Bonded device with [address], or `null` if not in the bonded set. */
    fun findBondedDevice(address: String): BluetoothRfcommDeviceHandle?
}

interface BluetoothRfcommDeviceHandle {
    val address: String

    /**
     * Secure SPP socket only — maps to
     * `BluetoothDevice.createRfcommSocketToServiceRecord(uuid)`.
     */
    fun createSecureSppSocket(uuid: UUID): BluetoothRfcommSocketHandle
}

interface BluetoothRfcommSocketHandle {
    fun connect()

    fun getOutputStream(): OutputStream

    fun close()
}
