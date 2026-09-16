package it.krpng.cassa.data.printer

import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.repository.PrinterSettingsRepository
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevice
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevicesProvider
import it.krpng.cassa.platform.bluetooth.BondedDevicesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DataStorePrinterProfileProviderTest {
    @Test
    fun `O selected id and D-061 physical values with persisted PricePrintMode`() = runTest {
        val repository =
            FakeRepo(
                selectedId = "AA:BB:CC:DD:EE:11",
                priceMode = PricePrintMode.TOTAL_ONLY,
            )
        val bonded =
            object : BondedBluetoothDevicesProvider {
                override fun listBondedDevices(): BondedDevicesResult =
                    BondedDevicesResult.Success(
                        listOf(BondedBluetoothDevice("AA:BB:CC:DD:EE:11", "Cassa BT")),
                    )
            }
        val provider = DataStorePrinterProfileProvider(repository, bonded)

        val profile = provider.getActiveProfile()!!

        assertEquals("AA:BB:CC:DD:EE:11", profile.id)
        assertEquals("Cassa BT", profile.name)
        assertEquals(80, profile.paperWidthMm)
        assertEquals(42, profile.charsPerLine)
        assertEquals("IBM00858", profile.codePage)
        assertEquals(19, profile.escPosCodeTable)
        assertEquals(3, profile.feedLines)
        assertFalse(profile.supportsCut)
        assertNull(profile.cutCommandVariant)
        assertEquals(PricePrintMode.TOTAL_ONLY, profile.pricePrintMode)
    }

    @Test
    fun `P no selected printer returns null per contract`() = runTest {
        val repository = FakeRepo(selectedId = null, priceMode = PricePrintMode.DETAILED)
        val bonded =
            object : BondedBluetoothDevicesProvider {
                override fun listBondedDevices(): BondedDevicesResult =
                    BondedDevicesResult.Success(emptyList())
            }
        val provider = DataStorePrinterProfileProvider(repository, bonded)

        assertNull(provider.getActiveProfile())
    }

    @Test
    fun `stale selected id still builds profile with fallback name`() = runTest {
        val repository =
            FakeRepo(
                selectedId = "STALE:MAC",
                priceMode = PricePrintMode.DETAILED,
            )
        val bonded =
            object : BondedBluetoothDevicesProvider {
                override fun listBondedDevices(): BondedDevicesResult =
                    BondedDevicesResult.Success(
                        listOf(BondedBluetoothDevice("OTHER", "Other")),
                    )
            }
        val provider = DataStorePrinterProfileProvider(repository, bonded)

        val profile = provider.getActiveProfile()!!
        assertEquals("STALE:MAC", profile.id)
        assertEquals(DataStorePrinterProfileProvider.FALLBACK_DISPLAY_NAME, profile.name)
        assertEquals(PricePrintMode.DETAILED, profile.pricePrintMode)
    }

    private class FakeRepo(
        private var selectedId: String?,
        private var priceMode: PricePrintMode,
    ) : PrinterSettingsRepository {
        override suspend fun getPricePrintMode(): PricePrintMode = priceMode

        override fun observePricePrintMode(): Flow<PricePrintMode> = flowOf(priceMode)

        override suspend fun updatePricePrintMode(mode: PricePrintMode) {
            priceMode = mode
        }

        override suspend fun getSelectedPrinterId(): String? = selectedId

        override fun observeSelectedPrinterId(): Flow<String?> = flowOf(selectedId)

        override suspend fun setSelectedPrinterId(id: String) {
            selectedId = id
        }

        override suspend fun clearSelectedPrinterId() {
            selectedId = null
        }
    }
}
