package it.krpng.cassa.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.domain.model.NumberingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingShowsProgressWithoutInventingModeSelection() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState.Loading,
                    printerState = PrinterSettingsUiState.Loading,
                    onBack = {},
                    onNumberingModeSelected = {},
                    onRetrySave = {},
                    onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Caricamento impostazioni").assertIsDisplayed()
        composeRule.onNodeWithText("SEQUENZIALE").assertDoesNotExist()
        composeRule.onNodeWithText("CASUALE").assertDoesNotExist()
    }

    @Test
    fun numT034SelectedOptionMatchesPersistedMode() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState.Loaded(NumberingMode.RANDOM),
                    printerState = PrinterSettingsUiState.Loading,
                    onBack = {},
                    onNumberingModeSelected = {},
                    onRetrySave = {},
                    onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Modalità numerazione").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("CASUALE").assertIsSelected()
        composeRule.onNodeWithContentDescription("SEQUENZIALE").assertIsNotSelected()
    }

    @Test
    fun numT035SavingAndErrorKeepPersistedSelectionVisible() {
        val state = mutableStateOf<SettingsUiState>(
            SettingsUiState.Saving(
                persistedMode = NumberingMode.SEQUENTIAL,
                requestedMode = NumberingMode.RANDOM,
            ),
        )
        var selected: NumberingMode? = null
        var retries = 0

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state.value,
                    printerState = PrinterSettingsUiState.Loading,
                    onBack = {},
                    onNumberingModeSelected = { selected = it },
                    onRetrySave = { retries += 1 },
                    onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("SEQUENZIALE").assertIsSelected()
        composeRule.onNodeWithContentDescription("CASUALE").assertIsNotSelected()
        composeRule.onNodeWithText("Salvataggio…").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = SettingsUiState.SaveError(
                persistedMode = NumberingMode.SEQUENTIAL,
                attemptedMode = NumberingMode.RANDOM,
                message = "Impossibile salvare la modalità numerazione.",
            )
        }

        composeRule.onNodeWithContentDescription("SEQUENZIALE").assertIsSelected()
        composeRule.onNodeWithContentDescription("CASUALE").assertIsNotSelected()
        composeRule.onNodeWithText("Impossibile salvare la modalità numerazione.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(null, selected)
        }
    }

    @Test
    fun selectingOptionInvokesCallbackWithRequestedMode() {
        var selected: NumberingMode? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState.Loaded(NumberingMode.SEQUENTIAL),
                    printerState = PrinterSettingsUiState.Loading,
                    onBack = {},
                    onNumberingModeSelected = { selected = it },
                    onRetrySave = {},
                    onRetryLoad = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("CASUALE").performClick()
        composeRule.runOnIdle { assertEquals(NumberingMode.RANDOM, selected) }
    }
}
