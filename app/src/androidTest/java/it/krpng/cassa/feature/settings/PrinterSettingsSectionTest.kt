package it.krpng.cassa.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.domain.model.PricePrintMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrinterSettingsSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedStateShowsRadioAndAddress() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices =
                                listOf(
                                    PrinterDeviceUi("AA:01", "Cucina"),
                                    PrinterDeviceUi("AA:02", "Bar"),
                                ),
                            selectedPrinterId = "AA:01",
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Cucina, AA:01").assertIsSelected()
        composeRule.onNodeWithContentDescription("Bar, AA:02").assertIsNotSelected()
        composeRule.onNodeWithText("AA:01").assertIsDisplayed()
    }

    @Test
    fun staleStateShowsUnavailableCopyAndStoredId() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Alive")),
                            selectedPrinterId = "STALE:99",
                            selectedIsStale = true,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("Stampante selezionata non disponibile").assertIsDisplayed()
        composeRule.onNodeWithText("Non attualmente associata").assertIsDisplayed()
        composeRule.onNodeWithText("STALE:99").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Alive, AA:01").assertIsNotSelected()
    }

    @Test
    fun noDevicesShowsEmptyStateAndBluetoothSettingsCta() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = emptyList(),
                            selectedPrinterId = null,
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("Nessun dispositivo Bluetooth associato.").assertIsDisplayed()
        composeRule.onNodeWithText("Apri impostazioni Bluetooth").assertIsDisplayed()
    }

    @Test
    fun testPrintEnabledForValidSelectedBondedPrinter() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Cucina")),
                            selectedPrinterId = "AA:01",
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    testPrintState = TestPrintUiState.Idle,
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("STAMPA DI PROVA").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun testPrintDisabledForStaleSelection() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Alive")),
                            selectedPrinterId = "STALE:99",
                            selectedIsStale = true,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    testPrintState = TestPrintUiState.Idle,
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("STAMPA DI PROVA").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun testPrintShowsProgressAndDisablesWhilePrinting() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Cucina")),
                            selectedPrinterId = "AA:01",
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    testPrintState = TestPrintUiState.Printing,
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("STAMPA IN CORSO…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stampa di prova in corso").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA DI PROVA").assertDoesNotExist()
    }

    @Test
    fun testPrintTapEmitsExactlyOneCallback() {
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Cucina")),
                            selectedPrinterId = "AA:01",
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    testPrintState = TestPrintUiState.Idle,
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                    onTestPrint = { taps += 1 },
                )
            }
        }

        composeRule.onNodeWithText("STAMPA DI PROVA").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun testPrintSuccessMessageIsVisible() {
        composeRule.setContent {
            MaterialTheme {
                PrinterSettingsSection(
                    state =
                        PrinterSettingsUiState.Ready(
                            devices = listOf(PrinterDeviceUi("AA:01", "Cucina")),
                            selectedPrinterId = "AA:01",
                            selectedIsStale = false,
                            pricePrintMode = PricePrintMode.DETAILED,
                        ),
                    testPrintState = TestPrintUiState.Success(),
                    onRetryLoad = {},
                    onRetryPermission = {},
                    onPermissionResult = {},
                    onSelectPrinter = {},
                    onClearPrinter = {},
                    onSelectPricePrintMode = {},
                    onRetryPersistence = {},
                )
            }
        }

        composeRule.onNodeWithText("Test stampa inviato").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA DI PROVA").assertIsEnabled()
    }
}
