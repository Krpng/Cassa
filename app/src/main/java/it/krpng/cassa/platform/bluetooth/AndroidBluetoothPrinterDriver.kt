package it.krpng.cassa.platform.bluetooth

import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.PrinterDriver
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterResult
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Bluetooth Classic RFCOMM/SPP [PrinterDriver] (D-058 / BT-004).
 *
 * Secure SPP only; bonded devices only; no DataStore / discovery / timeout / retry.
 * Blocking I/O runs on [ioDispatcher]. Not thread-safe — PrinterService Mutex serializes jobs.
 */
class AndroidBluetoothPrinterDriver(
    private val permissionManager: BluetoothPermissionManager,
    private val gateway: BluetoothRfcommGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PrinterDriver {
    private var connected: Boolean = false
    private var socket: BluetoothRfcommSocketHandle? = null
    private var outputStream: OutputStream? = null

    /** Read-only connection observation for tests (no public PrinterState domain type). */
    val isConnected: Boolean
        get() = connected

    override suspend fun connect(profile: PrinterProfile): PrinterResult =
        withContext(ioDispatcher) {
            if (connected) {
                return@withContext PrinterResult.Success
            }
            if (profile.id.isBlank()) {
                return@withContext PrinterResult.Failure(PrinterError.PrinterNotConfigured)
            }
            if (!permissionManager.areRequiredRuntimePermissionsGranted()) {
                return@withContext PrinterResult.Failure(PrinterError.PermissionDenied)
            }

            val adapter =
                try {
                    gateway.adapterOrNull()
                } catch (_: SecurityException) {
                    return@withContext PrinterResult.Failure(PrinterError.PermissionDenied)
                }
            if (adapter == null) {
                return@withContext PrinterResult.Failure(PrinterError.BluetoothDisabled)
            }
            if (!adapter.isEnabled) {
                return@withContext PrinterResult.Failure(PrinterError.BluetoothDisabled)
            }

            val device =
                try {
                    adapter.findBondedDevice(profile.id)
                } catch (_: SecurityException) {
                    return@withContext PrinterResult.Failure(PrinterError.PermissionDenied)
                }
            if (device == null) {
                return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed)
            }

            var attempt: BluetoothRfcommSocketHandle? = null
            try {
                attempt = device.createSecureSppSocket(SPP_UUID)
                attempt.connect()
                val stream = attempt.getOutputStream()
                socket = attempt
                outputStream = stream
                connected = true
                PrinterResult.Success
            } catch (_: SecurityException) {
                closeQuietly(attempt)
                clearSession()
                PrinterResult.Failure(PrinterError.PermissionDenied)
            } catch (_: IOException) {
                closeQuietly(attempt)
                clearSession()
                PrinterResult.Failure(PrinterError.ConnectionFailed)
            }
        }

    override suspend fun print(data: ByteArray): PrinterResult =
        withContext(ioDispatcher) {
            if (!connected) {
                return@withContext PrinterResult.Failure(PrinterError.ConnectionLost)
            }
            val stream = outputStream
            if (stream == null) {
                clearSession()
                return@withContext PrinterResult.Failure(PrinterError.ConnectionLost)
            }
            try {
                stream.write(data)
                stream.flush()
                PrinterResult.Success
            } catch (_: SecurityException) {
                PrinterResult.Failure(PrinterError.PermissionDenied)
            } catch (_: IOException) {
                // Preliminary BT-004 mapping; refined uncertain/timeout = BT-005.
                PrinterResult.Failure(PrinterError.PrintFailed)
            }
        }

    override suspend fun disconnect() {
        withContext(ioDispatcher) {
            val activeSocket = socket
            clearSession()
            closeQuietly(activeSocket)
        }
    }

    private fun clearSession() {
        connected = false
        outputStream = null
        socket = null
    }

    private fun closeQuietly(handle: BluetoothRfcommSocketHandle?) {
        if (handle == null) return
        try {
            handle.close()
        } catch (_: Exception) {
            // Best-effort cleanup; no DisconnectFailed type (D-054 / D-058).
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
