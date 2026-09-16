package it.krpng.cassa.data.printer

import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.printer.PrinterProfileProvider
import it.krpng.cassa.domain.repository.PrinterSettingsRepository
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevicesProvider
import it.krpng.cassa.platform.bluetooth.BondedDevicesResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [PrinterProfileProvider] (D-062 / BT-006).
 *
 * Selected id + [PricePrintMode] from [PrinterSettingsRepository];
 * physical constants frozen in D-061. Never hardcodes a device MAC.
 */
@Singleton
class DataStorePrinterProfileProvider @Inject constructor(
    private val printerSettingsRepository: PrinterSettingsRepository,
    private val bondedDevicesProvider: BondedBluetoothDevicesProvider,
) : PrinterProfileProvider {
    override suspend fun getActiveProfile(): PrinterProfile? {
        val selectedId = printerSettingsRepository.getSelectedPrinterId() ?: return null
        val pricePrintMode = printerSettingsRepository.getPricePrintMode()
        return PrinterProfile(
            id = selectedId,
            name = resolveDisplayName(selectedId),
            charsPerLine = PHYSICAL_CHARS_PER_LINE,
            codePage = PHYSICAL_CODE_PAGE,
            feedLines = PHYSICAL_FEED_LINES,
            paperWidthMm = PHYSICAL_PAPER_WIDTH_MM,
            supportsCut = PHYSICAL_SUPPORTS_CUT,
            cutCommandVariant = PHYSICAL_CUT_COMMAND_VARIANT,
            pricePrintMode = pricePrintMode,
            escPosCodeTable = PHYSICAL_ESC_POS_CODE_TABLE,
        )
    }

    private fun resolveDisplayName(selectedId: String): String {
        when (val result = bondedDevicesProvider.listBondedDevices()) {
            is BondedDevicesResult.Success -> {
                val bondedName =
                    result.devices
                        .firstOrNull { it.id == selectedId }
                        ?.name
                        ?.takeIf { it.isNotBlank() }
                if (bondedName != null) return bondedName
            }
            is BondedDevicesResult.Failure -> Unit
        }
        return FALLBACK_DISPLAY_NAME
    }

    companion object {
        const val PHYSICAL_PAPER_WIDTH_MM: Int = 80
        const val PHYSICAL_CHARS_PER_LINE: Int = 42
        const val PHYSICAL_CODE_PAGE: String = "IBM00858"
        const val PHYSICAL_ESC_POS_CODE_TABLE: Int = 19
        const val PHYSICAL_FEED_LINES: Int = 3
        const val PHYSICAL_SUPPORTS_CUT: Boolean = false
        val PHYSICAL_CUT_COMMAND_VARIANT: String? = null
        const val FALLBACK_DISPLAY_NAME: String = "Dispositivo Bluetooth"
    }
}
