package it.krpng.cassa.feature.order

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NewOrderScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyDraftShowsTheOrderShellAndBackUsesTheProvidedNavigation() {
        var backClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = NewOrderUiState.Ready(draftId = "draft-id", isEmpty = true),
                    onBack = { backClicks += 1 },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Nuovo ordine").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi un prodotto per iniziare l'ordine.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun loadingAndSafeFailureStatesAreRenderedWithoutOrderActions() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = NewOrderUiState.Loading,
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Caricamento ordine").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi un prodotto per iniziare l'ordine.")
            .assertDoesNotExist()
    }

    @Test
    fun acceptedOrderIsBlockedAndRetryRemainsAvailable() {
        var retryClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = NewOrderUiState.NotEditable,
                    onBack = {},
                    onRetry = { retryClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Questo ordine non è una bozza modificabile.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").performClick()
        composeRule.runOnIdle { assertEquals(1, retryClicks) }
    }
}
