package it.krpng.cassa.platform.bluetooth

import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.PrinterError
import it.krpng.cassa.domain.printer.PrinterResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [AndroidBluetoothPrinterDriver] with a fake [BluetoothRfcommGateway]
 * (D-058 / BT-004). Exercises the real driver — not FakePrinterDriver.
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
    fun `write failure returns PrintFailed`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    enabled = true,
                    bondedAddress = "AA:BB:CC:DD:EE:FF",
                    writeThrows = IOException("write failed"),
                )
            val driver = driver(gateway = gateway)
            assertEquals(PrinterResult.Success, driver.connect(profile("AA:BB:CC:DD:EE:FF")))

            assertEquals(
                PrinterResult.Failure(PrinterError.PrintFailed),
                driver.print(byteArrayOf(9)),
            )
            assertTrue(driver.isConnected)
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

    private fun driver(
        permissionsGranted: Boolean = true,
        gateway: BluetoothRfcommGateway,
    ): AndroidBluetoothPrinterDriver =
        AndroidBluetoothPrinterDriver(
            permissionManager = FakePermissionManager(permissionsGranted),
            gateway = gateway,
            ioDispatcher = Dispatchers.Unconfined,
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
                            val socket = FakeSocket(connectThrows, writeThrows)
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
    ) : BluetoothRfcommSocketHandle {
        val written = ByteArrayOutputStream()
        var flushed: Boolean = false
        var closed: Boolean = false
        private var connected: Boolean = false

        override fun connect() {
            if (connectThrows != null) throw connectThrows
            connected = true
        }

        override fun getOutputStream(): OutputStream {
            check(connected) { "not connected" }
            return object : OutputStream() {
                override fun write(b: Int) {
                    if (writeThrows != null) throw writeThrows
                    written.write(b)
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (writeThrows != null) throw writeThrows
                    written.write(b, off, len)
                }

                override fun flush() {
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
        }
    }
}
