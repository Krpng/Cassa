package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBondedBluetoothDevicesProviderTest {
    @Test
    fun `permission granted returns sorted bonded list`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots =
                    listOf(
                        BondedDeviceSnapshot("BB:BB:BB:BB:BB:BB", "beta"),
                        BondedDeviceSnapshot("AA:AA:AA:AA:AA:AA", "Alpha"),
                    ),
            )

        val result = provider.listBondedDevices()

        assertEquals(
            BondedDevicesResult.Success(
                listOf(
                    BondedBluetoothDevice("AA:AA:AA:AA:AA:AA", "Alpha"),
                    BondedBluetoothDevice("BB:BB:BB:BB:BB:BB", "beta"),
                ),
            ),
            result,
        )
    }

    @Test
    fun `permission missing returns PermissionDenied and does not access bonded devices`() {
        var accessed = false
        val provider =
            DefaultBondedBluetoothDevicesProvider(
                permissionManager = grantedManager(false),
                adapterGateway = {
                    accessed = true
                    emptyList()
                },
            )

        val result = provider.listBondedDevices()

        assertEquals(
            BondedDevicesResult.Failure(BondedDevicesError.PermissionDenied),
            result,
        )
        assertFalse(accessed)
    }

    @Test
    fun `adapter null returns BluetoothUnavailable`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots = null,
            )

        assertEquals(
            BondedDevicesResult.Failure(BondedDevicesError.BluetoothUnavailable),
            provider.listBondedDevices(),
        )
    }

    @Test
    fun `empty bonded set returns Success emptyList`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots = emptyList(),
            )

        assertEquals(
            BondedDevicesResult.Success(emptyList()),
            provider.listBondedDevices(),
        )
    }

    @Test
    fun `multiple devices all returned with stable address ids`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots =
                    listOf(
                        BondedDeviceSnapshot("11:11:11:11:11:11", "One"),
                        BondedDeviceSnapshot("22:22:22:22:22:22", "Two"),
                        BondedDeviceSnapshot("33:33:33:33:33:33", "Three"),
                    ),
            )

        val result = provider.listBondedDevices() as BondedDevicesResult.Success
        assertEquals(3, result.devices.size)
        assertEquals(
            listOf("11:11:11:11:11:11", "33:33:33:33:33:33", "22:22:22:22:22:22"),
            result.devices.map { it.id },
        )
        assertEquals(listOf("One", "Three", "Two"), result.devices.map { it.name })
    }

    @Test
    fun `duplicate names retained as separate devices`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots =
                    listOf(
                        BondedDeviceSnapshot("02", "Same"),
                        BondedDeviceSnapshot("01", "Same"),
                    ),
            )

        val result = provider.listBondedDevices() as BondedDevicesResult.Success
        assertEquals(2, result.devices.size)
        assertEquals(listOf("01", "02"), result.devices.map { it.id })
    }

    @Test
    fun `null and blank names retained and sorted after named`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots =
                    listOf(
                        BondedDeviceSnapshot("03", null),
                        BondedDeviceSnapshot("02", ""),
                        BondedDeviceSnapshot("01", "Named"),
                        BondedDeviceSnapshot("04", "  "),
                    ),
            )

        val result = provider.listBondedDevices() as BondedDevicesResult.Success
        assertEquals(
            listOf(
                BondedBluetoothDevice("01", "Named"),
                BondedBluetoothDevice("02", ""),
                BondedBluetoothDevice("03", null),
                BondedBluetoothDevice("04", "  "),
            ),
            result.devices,
        )
    }

    @Test
    fun `SecurityException reading bonded snapshots maps to PermissionDenied`() {
        val provider =
            DefaultBondedBluetoothDevicesProvider(
                permissionManager = grantedManager(true),
                adapterGateway = { throw SecurityException("bondedDevices") },
            )

        assertEquals(
            BondedDevicesResult.Failure(BondedDevicesError.PermissionDenied),
            provider.listBondedDevices(),
        )
    }

    @Test
    fun `blank address snapshots are dropped as invalid ids`() {
        val provider =
            provider(
                permissionsGranted = true,
                snapshots =
                    listOf(
                        BondedDeviceSnapshot("", "Ghost"),
                        BondedDeviceSnapshot("  ", "Ghost2"),
                        BondedDeviceSnapshot("AA:AA:AA:AA:AA:AA", "Keep"),
                    ),
            )

        val result = provider.listBondedDevices() as BondedDevicesResult.Success
        assertEquals(
            listOf(BondedBluetoothDevice("AA:AA:AA:AA:AA:AA", "Keep")),
            result.devices,
        )
    }

    @Test
    fun `pre-31 permission manager allows listing without CONNECT grant`() {
        val manager =
            DefaultBluetoothPermissionManager(
                sdkInt = 30,
                isPermissionGranted = { false },
            )
        val provider =
            DefaultBondedBluetoothDevicesProvider(
                permissionManager = manager,
                adapterGateway = {
                    listOf(BondedDeviceSnapshot("AA:AA:AA:AA:AA:AA", "Legacy"))
                },
            )

        val result = provider.listBondedDevices() as BondedDevicesResult.Success
        assertEquals(1, result.devices.size)
    }

    @Test
    fun `result errors do not include BluetoothDisabled or PrinterError types`() {
        val denied =
            BondedDevicesResult.Failure(BondedDevicesError.PermissionDenied)
        val unavailable =
            BondedDevicesResult.Failure(BondedDevicesError.BluetoothUnavailable)

        assertTrue(denied.error is BondedDevicesError.PermissionDenied)
        assertTrue(unavailable.error is BondedDevicesError.BluetoothUnavailable)
        assertFalse(denied.error.toString().contains("BluetoothDisabled"))
    }

    @Test
    fun `gateway is not asked to discover — only bonded snapshots`() {
        var readCount = 0
        val provider =
            DefaultBondedBluetoothDevicesProvider(
                permissionManager = grantedManager(true),
                adapterGateway = {
                    readCount += 1
                    emptyList()
                },
            )

        provider.listBondedDevices()
        assertEquals(1, readCount)
    }

    private fun provider(
        permissionsGranted: Boolean,
        snapshots: List<BondedDeviceSnapshot>?,
    ): DefaultBondedBluetoothDevicesProvider =
        DefaultBondedBluetoothDevicesProvider(
            permissionManager = grantedManager(permissionsGranted),
            adapterGateway = { snapshots },
        )

    private fun grantedManager(granted: Boolean): BluetoothPermissionManager =
        object : BluetoothPermissionManager {
            override fun requiredRuntimePermissions(): List<String> =
                if (granted) emptyList() else listOf(BluetoothPermissions.BLUETOOTH_CONNECT)

            override fun missingRuntimePermissions(): List<String> =
                if (granted) emptyList() else listOf(BluetoothPermissions.BLUETOOTH_CONNECT)

            override fun areRequiredRuntimePermissionsGranted(): Boolean = granted

            override fun isRuntimePermissionRequestNeeded(): Boolean = !granted
        }
}
