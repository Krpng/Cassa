package it.krpng.cassa.feature.settings

import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.platform.bluetooth.BondedBluetoothDevice

/**
 * UI row for a bonded printer candidate (D-062 / BT-006).
 * [id] is the exact Bluetooth address; never mutate or normalize.
 */
data class PrinterDeviceUi(
    val id: String,
    val displayName: String,
) {
    companion object {
        const val UNNAMED_FALLBACK: String = "Dispositivo Bluetooth"

        fun fromBonded(device: BondedBluetoothDevice): PrinterDeviceUi =
            PrinterDeviceUi(
                id = device.id,
                displayName = device.name?.takeIf { it.isNotBlank() } ?: UNNAMED_FALLBACK,
            )
    }
}

/**
 * Printer settings section state (D-062 / BT-006).
 * Prefer sealed variants over contradictory boolean combinations.
 */
sealed interface PrinterSettingsUiState {
    data object Loading : PrinterSettingsUiState

    data class PermissionRequired(
        val missingPermissions: List<String>,
        val requestable: Boolean,
    ) : PrinterSettingsUiState

    data class PermissionDenied(
        val missingPermissions: List<String>,
        val requestable: Boolean,
    ) : PrinterSettingsUiState

    data object BluetoothUnavailable : PrinterSettingsUiState

    data object BluetoothDisabled : PrinterSettingsUiState

    data class Ready(
        val devices: List<PrinterDeviceUi>,
        val selectedPrinterId: String?,
        val selectedIsStale: Boolean,
        val pricePrintMode: PricePrintMode,
        val persistenceMessage: String? = null,
        val pendingRetry: PersistenceRetry? = null,
    ) : PrinterSettingsUiState

    data class LoadError(
        val message: String,
    ) : PrinterSettingsUiState
}

sealed interface PersistenceRetry {
    data class SelectPrinter(val id: String) : PersistenceRetry

    data object ClearPrinter : PersistenceRetry

    data class SelectPriceMode(val mode: PricePrintMode) : PersistenceRetry
}
