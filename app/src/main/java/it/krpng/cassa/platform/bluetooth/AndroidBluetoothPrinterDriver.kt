package it.krpng.cassa.platform.bluetooth

import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.PrinterDriver
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterResult
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Bluetooth Classic RFCOMM/SPP [PrinterDriver] (D-058 / D-059 / BT-004 / BT-005).
 *
 * Secure SPP only; bonded devices only; connect-only timeout; no write/flush timeout;
 * no automatic retry/reconnect. Blocking I/O runs on [ioDispatcher].
 * Not thread-safe — PrinterService Mutex serializes jobs.
 */
class AndroidBluetoothPrinterDriver(
    private val permissionManager: BluetoothPermissionManager,
    private val gateway: BluetoothRfcommGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
) : PrinterDriver {
    private var connected: Boolean = false
    private var socket: BluetoothRfcommSocketHandle? = null
    private var outputStream: OutputStream? = null

    init {
        require(connectTimeoutMs > 0) {
            "connectTimeoutMs must be > 0 (was $connectTimeoutMs)"
        }
    }

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
                connectBlockingWithTimeout(attempt)
                val stream = attempt.getOutputStream()
                socket = attempt
                outputStream = stream
                connected = true
                PrinterResult.Success
            } catch (e: CancellationException) {
                closeQuietly(attempt)
                clearSession()
                throw e
            } catch (_: SecurityException) {
                closeQuietly(attempt)
                clearSession()
                PrinterResult.Failure(PrinterError.PermissionDenied)
            } catch (_: ConnectTimeout) {
                closeQuietly(attempt)
                clearSession()
                PrinterResult.Failure(PrinterError.Timeout)
            } catch (_: IOException) {
                closeQuietly(attempt)
                clearSession()
                PrinterResult.Failure(PrinterError.ConnectionFailed)
            }
        }

    /**
     * Arms a concurrent timeout that closes [attempt] when it wins, unblocking native
     * [BluetoothRfcommSocketHandle.connect]. Close-induced [IOException] maps to timeout
     * via [timeoutWon], not message matching. After a successful [BluetoothRfcommSocketHandle.connect]
     * return, [timeoutWon] is checked immediately and again after [coroutineScope] joins all children
     * so a concurrent timeout cannot proceed to stream acquisition / session publication. External
     * cancel also closes the in-flight socket and rethrows [CancellationException]
     * (not Timeout / ConnectionFailed).
     */
    private suspend fun connectBlockingWithTimeout(attempt: BluetoothRfcommSocketHandle) {
        val timeoutWon = AtomicBoolean(false)
        val connectFinished = AtomicBoolean(false)
        try {
            coroutineScope {
                val timeoutJob =
                    launch {
                        delay(connectTimeoutMs)
                        timeoutWon.set(true)
                        closeQuietly(attempt)
                    }
                val cancelCloser =
                    launch {
                        try {
                            awaitCancellation()
                        } finally {
                            if (!connectFinished.get()) {
                                closeQuietly(attempt)
                            }
                        }
                    }
                try {
                    attempt.connect()
                    ensureActive()
                    // Fast path: timeout may already own after connect() returned.
                    if (timeoutWon.get()) {
                        throw ConnectTimeout()
                    }
                } catch (e: IOException) {
                    if (timeoutWon.get()) {
                        throw ConnectTimeout()
                    }
                    ensureActive()
                    throw e
                } finally {
                    connectFinished.set(true)
                    timeoutJob.cancel()
                    cancelCloser.cancel()
                }
            }
            // Residual TOCTOU closed: after coroutineScope joins children, no timeout child
            // can still mutate timeoutWon. Re-check before treating connect as definitive.
            if (timeoutWon.get()) {
                throw ConnectTimeout()
            }
        } catch (e: CancellationException) {
            closeQuietly(attempt)
            throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                failSession(PrinterError.PermissionDenied)
            } catch (_: IOException) {
                // D-059: RFCOMM write/flush IOException → ConnectionLost (uncertain outcome).
                failSession(PrinterError.ConnectionLost)
            }
        }

    override suspend fun disconnect() {
        withContext(ioDispatcher) {
            val activeSocket = socket
            clearSession()
            closeQuietly(activeSocket)
        }
    }

    private fun failSession(error: PrinterError): PrinterResult.Failure {
        val activeSocket = socket
        clearSession()
        closeQuietly(activeSocket)
        return PrinterResult.Failure(error)
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
            // Best-effort cleanup; no DisconnectFailed type (D-054 / D-058 / D-059).
        }
    }

    /** Internal signal: connect timeout won before a usable connection. */
    private class ConnectTimeout : Exception()

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Production default connect timeout (D-059 Q1). */
        const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 10_000L
    }
}
