package it.krpng.cassa.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.OutputStream
import java.util.UUID

/**
 * Production [BluetoothRfcommGateway] wrapping Android Classic Bluetooth APIs (D-058 / BT-004).
 *
 * Secure RFCOMM only — never calls [BluetoothDevice.createInsecureRfcommSocketToServiceRecord].
 * Does not start discovery or pairing.
 */
class AndroidBluetoothRfcommGateway(
    private val adapterProvider: () -> BluetoothAdapter?,
) : BluetoothRfcommGateway {
    override fun adapterOrNull(): BluetoothRfcommAdapterHandle? {
        val adapter = adapterProvider() ?: return null
        return AndroidBluetoothRfcommAdapterHandle(adapter)
    }
}

private class AndroidBluetoothRfcommAdapterHandle(
    private val adapter: BluetoothAdapter,
) : BluetoothRfcommAdapterHandle {
    override val isEnabled: Boolean
        get() = adapter.isEnabled

    override fun findBondedDevice(address: String): BluetoothRfcommDeviceHandle? {
        val device =
            adapter.bondedDevices.firstOrNull { bonded ->
                bonded.address.equals(address, ignoreCase = false)
            } ?: return null
        return AndroidBluetoothRfcommDeviceHandle(device)
    }
}

private class AndroidBluetoothRfcommDeviceHandle(
    private val device: BluetoothDevice,
) : BluetoothRfcommDeviceHandle {
    override val address: String
        get() = device.address

    override fun createSecureSppSocket(uuid: UUID): BluetoothRfcommSocketHandle {
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        return AndroidBluetoothRfcommSocketHandle(socket)
    }
}

private class AndroidBluetoothRfcommSocketHandle(
    private val socket: BluetoothSocket,
) : BluetoothRfcommSocketHandle {
    override fun connect() {
        socket.connect()
    }

    override fun getOutputStream(): OutputStream = socket.outputStream

    override fun close() {
        socket.close()
    }
}
