package it.krpng.cassa.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore file + keys for printer/device preferences (D-050 / D-057).
 * Stable names — do not rename casually once shipped.
 */
object PrinterPreferences {
    const val DATA_STORE_FILE_NAME: String = "printer_preferences"

    val PRICE_PRINT_MODE = stringPreferencesKey("price_print_mode")

    /** Selected bonded printer identity = [it.krpng.cassa.platform.bluetooth.BondedBluetoothDevice.id]. */
    val SELECTED_PRINTER_ID = stringPreferencesKey("selected_printer_id")
}