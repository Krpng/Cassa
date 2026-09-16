package it.krpng.cassa.platform.bluetooth

import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM unit tests for [AndroidBluetoothPrinterDriver] with a fake [BluetoothRfcommGateway]
 * (D-058 / D-059 / BT-004 / BT-005). Exercises the real driver — not FakePrinterDriver.
 */
class AndroidBluetoothPrinterDriverTest {
    @Test
    fun `permission missing returns PermissionDenied and does not touch adapter`() =
        runBlocking {
            var adapterAccessed = false
            val driver =
                driver(
                    permissionsGranted = false,
                    gateway =
                        object : BluetoothRfcommGateway {
                            override fun adapterOrNull(): BluetoothRfcommAdapterHandle? {
                                adapterAccessed = true
                                return null
                            }
                        },
                )

            val result = driver.connect(profile("AA:BB:CC:DD:EE:FF"))

            assertEquals(PrinterResult.Failure(PrinterError.PermissionDenied), result)
            assertFalse(adapterAccessed)
            assertFalse(driver.isConnected)
        }

    @Test
    fun `adapter unavailable returns BluetoothDisabled`() =
        runBlocking {
            val driver = driver(gateway = FixedGateway(adapter = null))

            assertEquals(
                PrinterResult.Failure(PrinterError.BluetoothDisabled),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `adapter disabled returns BluetoothDisabled`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = false, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.BluetoothDisabled),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertTrue(gateway.secureSocketUuids.isEmpty())
            assertFalse(driver.isConnected)
        }

    @Test
    fun `blank profile id returns PrinterNotConfigured without Bluetooth access`() =
        runBlocking {
            var adapterAccessed = false
            val driver =
                driver(
                    gateway =
                        object : BluetoothRfcommGateway {
                            override fun adapterOrNull(): BluetoothRfcommAdapterHandle? {
                                adapterAccessed = true
                                return null
                            }
                        },
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.PrinterNotConfigured),
                driver.connect(profile("   ")),
            )
            assertFalse(adapterAccessed)
            assertFalse(driver.isConnected)
        }

    @Test
    fun `selected id not bonded returns ConnectionFailed`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "11:22:33:44:55:66")
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertTrue(gateway.secureSocketUuids.isEmpty())
            assertFalse(driver.isConnected)
        }

    @Test
    fun `successful connect uses secure SPP UUID exactly and marks connected`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)

            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertTrue(driver.isConnected)
            assertEquals(
                listOf(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")),
                gateway.secureSocketUuids,
            )
            assertEquals(AndroidBluetoothPrinterDriver.SPP_UUID, gateway.secureSocketUuids.single())
            assertFalse(gateway.insecureSocketRequested)
            assertFalse(gateway.discoveryRequested)
        }

    @Test
    fun `connect while connected is Success without second socket`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)

            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertEquals(1, gateway.secureSocketUuids.size)
            assertTrue(driver.isConnected)
        }

    @Test
    fun `failed connect leaves disconnected and closes attempted socket`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    connectThrows = IOException("boom"),
                )
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertEquals(1, gateway.createdSockets.size)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `print while disconnected returns ConnectionLost`() =
        runBlocking {
            val driver = driver(gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"))

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                driver.print(byteArrayOf(1, 2, 3)),
            )
        }

    @Test
    fun `successful print writes exact bytes and flushes without mutating payload`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            val payload = byteArrayOf(0x1B, 0x40, 0x41, 0x42)
            val original = payload.copyOf()
            assertEquals(PrinterResult.Success, driver.print(payload))

            assertArrayEquals(original, payload)
            assertArrayEquals(original, gateway.lastSocket!!.written.toByteArray())
            assertTrue(gateway.lastSocket!!.flushed)
        }

    @Test
    fun `write IOException returns ConnectionLost and disconnects`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrows = IOException("write failed"),
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            val sock = gateway.lastSocket!!

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                driver.print(byteArrayOf(9)),
            )
            assertFalse(driver.isConnected)
            assertTrue(sock.closed)
        }

    @Test
    fun `flush IOException returns ConnectionLost and disconnects`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    flushThrows = IOException("flush failed"),
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            val sock = gateway.lastSocket!!

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                driver.print(byteArrayOf(9)),
            )
            assertFalse(driver.isConnected)
            assertTrue(sock.closed)
        }

    @Test
    fun `cleanup IOException does not mask ConnectionLost`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrows = IOException("write failed"),
                    closeThrows = IOException("close boom"),
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                driver.print(byteArrayOf(9)),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `output stream acquisition failure returns ConnectionFailed and cleans up`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    outputStreamThrows = IOException("no stream"),
                )
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `mid-session SecurityException on write returns PermissionDenied and disconnects`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrowsSecurity = true,
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            val sock = gateway.lastSocket!!

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.print(byteArrayOf(1)),
            )
            assertFalse(driver.isConnected)
            assertTrue(sock.closed)
        }

    @Test
    fun `disconnect connected closes resources and becomes disconnected`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            val sock = gateway.lastSocket!!

            driver.disconnect()

            assertFalse(driver.isConnected)
            assertTrue(sock.closed)
        }

    @Test
    fun `disconnect while disconnected is idempotent Success`() =
        runBlocking {
            val driver = driver(gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"))

            driver.disconnect()
            driver.disconnect()

            assertFalse(driver.isConnected)
        }

    @Test
    fun `SecurityException on adapter access returns PermissionDenied`() =
        runBlocking {
            val driver =
                driver(
                    gateway =
                        object : BluetoothRfcommGateway {
                            override fun adapterOrNull(): BluetoothRfcommAdapterHandle? {
                                throw SecurityException("denied")
                            }
                        },
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
        }

    @Test
    fun `SecurityException on bonded lookup returns PermissionDenied`() =
        runBlocking {
            val driver =
                driver(
                    gateway =
                        object : BluetoothRfcommGateway {
                            override fun adapterOrNull(): BluetoothRfcommAdapterHandle =
                                object : BluetoothRfcommAdapterHandle {
                                    override val isEnabled: Boolean = true

                                    override fun findBondedDevice(address: String): BluetoothRfcommDeviceHandle? {
                                        throw SecurityException("denied")
                                    }
                                }
                        },
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
        }

    @Test
    fun `no discovery APIs are invoked on connect path`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver = driver(gateway = gateway)

            driver.connect(profile("AA:BB:CC:DD:EE:FF"))

            assertFalse(gateway.discoveryRequested)
            assertNull(gateway.pairingRequestedAddress)
        }

    @Test
    fun `production default connect timeout is 10000 ms`() {
        val driver =
            driver(gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals(10_000L, AndroidBluetoothPrinterDriver.DEFAULT_CONNECT_TIMEOUT_MS)
        assertEquals(10_000L, driver.connectTimeoutMs)
    }

    @Test
    fun `injected connect timeout is used`() {
        val driver =
            driver(
                gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"),
                connectTimeoutMs = 42L,
            )
        assertEquals(42L, driver.connectTimeoutMs)
    }

    @Test
    fun `connect succeeds before timeout`() =
        runBlocking {
            val gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 5_000L,
                )

            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertTrue(driver.isConnected)
        }

    @Test
    fun `connect IOException before timeout returns ConnectionFailed`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    connectThrows = IOException("refused"),
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 5_000L,
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `timeout wins closes in-flight socket and returns Timeout not ConnectionFailed`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockConnectUntilClosed = true,
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 40L,
                )

            val result = driver.connect(profile("AA:BB:CC:DD:EE:FF"))

            assertEquals(PrinterResult.Failure(PrinterError.Timeout), result)
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
            assertEquals(1, gateway.createdSockets.size)
        }

    @Test
    fun `close-induced connect IOException after timeout win still Timeout`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockConnectUntilClosed = true,
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 40L,
                )

            // FakeSocket.connect throws IOException("closed by timeout") when released by close —
            // driver must still map via timeoutWon flag, not exception message.
            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `timeout leaves DISCONNECTED and clears session without retry`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockConnectUntilClosed = true,
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 40L,
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertEquals(1, gateway.secureSocketUuids.size)

            // No automatic retry/reconnect — second explicit connect creates a new attempt.
            gateway.blockConnectUntilClosed = false
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertEquals(2, gateway.secureSocketUuids.size)
        }

    @Test
    fun `external CancellationException during connect is rethrown not Timeout`() =
        runBlocking {
            val entered = CountDownLatch(1)
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockConnectUntilClosed = true,
                    onConnectEntered = { entered.countDown() },
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 5_000L,
                )

            val deferred =
                async(Dispatchers.IO) {
                    driver.connect(profile("AA:BB:CC:DD:EE:FF"))
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            deferred.cancel(CancellationException("external cancel"))
            try {
                deferred.await()
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                // expected
            }
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `CancellationException during print is rethrown`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrowsCancellation = true,
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            try {
                driver.print(byteArrayOf(1))
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                // expected — not mapped to Unknown / ConnectionLost
            }
        }

    @Test
    fun `no automatic print retry after ConnectionLost`() =
        runBlocking {
            val writeAttempts = AtomicInteger(0)
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrows = IOException("write failed"),
                    onWrite = { writeAttempts.incrementAndGet() },
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionLost),
                driver.print(byteArrayOf(1)),
            )
            assertEquals(1, writeAttempts.get())
        }

    @Test
    fun `zero connectTimeoutMs is rejected`() {
        try {
            driver(
                gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"),
                connectTimeoutMs = 0L,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("connectTimeoutMs"))
        }
    }

    @Test
    fun `negative connectTimeoutMs is rejected`() {
        try {
            driver(
                gateway = RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF"),
                connectTimeoutMs = -1L,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("connectTimeoutMs"))
        }
    }

    @Test
    fun `timeout wins then connect returns success still Timeout without publishing session`() =
        runBlocking {
            // Deterministic order: connect blocks until close; timeout closes socket then
            // connect returns SUCCESS (not IOException). Driver must still honor timeoutWon.
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockUntilClosedThenSucceed = true,
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 20L,
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
            assertFalse(gateway.createdSockets.single().outputStreamRequested)
        }

    @Test
    fun `post-scope timeout ownership cannot yield Success or ConnectionFailed`() =
        runBlocking {
            // Structural guarantee: after coroutineScope joins, timeout child is dead.
            // Exact mid-check TOCTOU injection would need a production test hook — not added.
            // Prove stable success path: connect completes, final ownership false, session published;
            // and close-then-SUCCESS still maps to Timeout (ownership true before return).
            val successGateway =
                RecordingGateway(enabled = true, bondedAddress = "AA:BB:CC:DD:EE:FF")
            val successDriver =
                driver(
                    gateway = successGateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 5_000L,
                )
            assertEquals(PrinterResult.Success, successDriver.connect(profile("AA:BB:CC:DD:EE:FF")))
            assertTrue(successDriver.isConnected)
            assertTrue(successGateway.createdSockets.single().outputStreamRequested)

            val timeoutGateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockUntilClosedThenSucceed = true,
                )
            val timeoutDriver =
                driver(
                    gateway = timeoutGateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 20L,
                )
            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                timeoutDriver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(timeoutDriver.isConnected)
            assertFalse(timeoutGateway.createdSockets.single().outputStreamRequested)
        }

    @Test
    fun `Timeout primary preserved when cleanup close fails`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    blockConnectUntilClosed = true,
                    closeThrows = IOException("close boom"),
                )
            val driver =
                driver(
                    gateway = gateway,
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMs = 20L,
                )

            assertEquals(
                PrinterResult.Failure(PrinterError.Timeout),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `ConnectionFailed primary preserved when cleanup close fails`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    connectThrows = IOException("refused"),
                    closeThrows = IOException("close boom"),
                )
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.ConnectionFailed),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `PermissionDenied primary preserved when cleanup close fails after write`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrowsSecurity = true,
                    closeThrows = IOException("close boom"),
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.print(byteArrayOf(1)),
            )
            assertFalse(driver.isConnected)
        }

    @Test
    fun `SecurityException during socket connect returns PermissionDenied`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    connectThrowsSecurity = true,
                )
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `SecurityException during getOutputStream returns PermissionDenied`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    outputStreamThrowsSecurity = true,
                )
            val driver = driver(gateway = gateway)

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.connect(profile("AA:BB:CC:DD:EE:FF")),
            )
            assertFalse(driver.isConnected)
            assertTrue(gateway.createdSockets.single().closed)
        }

    @Test
    fun `SecurityException during flush returns PermissionDenied and disconnects`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    flushThrowsSecurity = true,
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))
            val sock = gateway.lastSocket!!

            assertEquals(
                PrinterResult.Failure(PrinterError.PermissionDenied),
                driver.print(byteArrayOf(1)),
            )
            assertFalse(driver.isConnected)
            assertTrue(sock.closed)
        }

    private fun driver(
        permissionsGranted: Boolean = true,
        gateway: BluetoothRfcommGateway,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        connectTimeoutMs: Long = AndroidBluetoothPrinterDriver.DEFAULT_CONNECT_TIMEOUT_MS,
    ): AndroidBluetoothPrinterDriver =
        AndroidBluetoothPrinterDriver(
            permissionManager = FakePermissionManager(permissionsGranted),
            gateway = gateway,
            ioDispatcher = ioDispatcher,
            connectTimeoutMs = connectTimeoutMs,
        )

    private fun profile(id: String): PrinterProfile =
        PrinterProfile(
            id = id,
            name = "Test",
            charsPerLine = 32,
            codePage = "CP437",
            feedLines = 3,
        )

    private class FakePermissionManager(
        private val granted: Boolean,
    ) : BluetoothPermissionManager {
        override fun requiredRuntimePermissions(): List<String> =
            if (granted) emptyList() else listOf("android.permission.BLUETOOTH_CONNECT")

        override fun missingRuntimePermissions(): List<String> = requiredRuntimePermissions()

        override fun areRequiredRuntimePermissionsGranted(): Boolean = granted

        override fun isRuntimePermissionRequestNeeded(): Boolean = !granted
    }

    private class FixedGateway(
        private val adapter: BluetoothRfcommAdapterHandle?,
    ) : BluetoothRfcommGateway {
        override fun adapterOrNull(): BluetoothRfcommAdapterHandle? = adapter
    }

    private class RecordingGateway(
        private val enabled: Boolean,
        private val bondedAddress: String?,
        private val connectThrows: IOException? = null,
        private val writeThrows: IOException? = null,
        private val flushThrows: IOException? = null,
        private val outputStreamThrows: IOException? = null,
        private val closeThrows: IOException? = null,
        private val writeThrowsSecurity: Boolean = false,
        private val flushThrowsSecurity: Boolean = false,
        private val writeThrowsCancellation: Boolean = false,
        private val connectThrowsSecurity: Boolean = false,
        private val outputStreamThrowsSecurity: Boolean = false,
        var blockConnectUntilClosed: Boolean = false,
        var blockUntilClosedThenSucceed: Boolean = false,
        private val onConnectEntered: (() -> Unit)? = null,
        private val onWrite: (() -> Unit)? = null,
    ) : BluetoothRfcommGateway {
        val secureSocketUuids = mutableListOf<UUID>()
        val createdSockets = mutableListOf<FakeSocket>()
        var lastSocket: FakeSocket? = null
        var insecureSocketRequested: Boolean = false
        var discoveryRequested: Boolean = false
        var pairingRequestedAddress: String? = null

        override fun adapterOrNull(): BluetoothRfcommAdapterHandle =
            object : BluetoothRfcommAdapterHandle {
                override val isEnabled: Boolean = enabled

                override fun findBondedDevice(address: String): BluetoothRfcommDeviceHandle? {
                    if (bondedAddress == null || bondedAddress != address) return null
                    return object : BluetoothRfcommDeviceHandle {
                        override val address: String = address

                        override fun createSecureSppSocket(uuid: UUID): BluetoothRfcommSocketHandle {
                            secureSocketUuids += uuid
                            val socket =
                                FakeSocket(
                                    connectThrows = connectThrows,
                                    writeThrows = writeThrows,
                                    flushThrows = flushThrows,
                                    outputStreamThrows = outputStreamThrows,
                                    closeThrows = closeThrows,
                                    writeThrowsSecurity = writeThrowsSecurity,
                                    flushThrowsSecurity = flushThrowsSecurity,
                                    writeThrowsCancellation = writeThrowsCancellation,
                                    connectThrowsSecurity = connectThrowsSecurity,
                                    outputStreamThrowsSecurity = outputStreamThrowsSecurity,
                                    blockConnectUntilClosed = { blockConnectUntilClosed },
                                    blockUntilClosedThenSucceed = { blockUntilClosedThenSucceed },
                                    onConnectEntered = onConnectEntered,
                                    onWrite = onWrite,
                                )
                            createdSockets += socket
                            lastSocket = socket
                            return socket
                        }
                    }
                }
            }
    }

    private class FakeSocket(
        private val connectThrows: IOException?,
        private val writeThrows: IOException?,
        private val flushThrows: IOException?,
        private val outputStreamThrows: IOException?,
        private val closeThrows: IOException?,
        private val writeThrowsSecurity: Boolean,
        private val flushThrowsSecurity: Boolean,
        private val writeThrowsCancellation: Boolean,
        private val connectThrowsSecurity: Boolean,
        private val outputStreamThrowsSecurity: Boolean,
        private val blockConnectUntilClosed: () -> Boolean,
        private val blockUntilClosedThenSucceed: () -> Boolean,
        private val onConnectEntered: (() -> Unit)?,
        private val onWrite: (() -> Unit)?,
    ) : BluetoothRfcommSocketHandle {
        val written = ByteArrayOutputStream()
        var flushed: Boolean = false
        var closed: Boolean = false
        var outputStreamRequested: Boolean = false
        private var connected: Boolean = false
        private val release = CountDownLatch(1)

        override fun connect() {
            onConnectEntered?.invoke()
            if (blockUntilClosedThenSucceed()) {
                // Wait until timeout (or cancel) closes us, then return SUCCESS.
                release.await()
                connected = true
                return
            }
            if (blockConnectUntilClosed()) {
                release.await()
                throw IOException("closed by timeout")
            }
            if (connectThrowsSecurity) throw SecurityException("denied")
            if (connectThrows != null) throw connectThrows
            connected = true
        }

        override fun getOutputStream(): OutputStream {
            outputStreamRequested = true
            check(connected) { "not connected" }
            if (outputStreamThrowsSecurity) throw SecurityException("denied")
            if (outputStreamThrows != null) throw outputStreamThrows
            return object : OutputStream() {
                override fun write(b: Int) {
                    onWrite?.invoke()
                    if (writeThrowsCancellation) throw CancellationException("print cancelled")
                    if (writeThrowsSecurity) throw SecurityException("denied")
                    if (writeThrows != null) throw writeThrows
                    written.write(b)
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    onWrite?.invoke()
                    if (writeThrowsCancellation) throw CancellationException("print cancelled")
                    if (writeThrowsSecurity) throw SecurityException("denied")
                    if (writeThrows != null) throw writeThrows
                    written.write(b, off, len)
                }

                override fun flush() {
                    if (flushThrowsSecurity) throw SecurityException("denied")
                    if (flushThrows != null) throw flushThrows
                    if (writeThrows != null) throw writeThrows
                    flushed = true
                }

                override fun close() {
                    // no-op; socket close owns lifecycle
                }
            }
        }

        override fun close() {
            closed = true
            release.countDown()
            if (closeThrows != null) throw closeThrows
        }
    }
}
