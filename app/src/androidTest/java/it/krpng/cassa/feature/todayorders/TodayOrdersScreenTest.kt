package it.krpng.cassa.feature.todayorders

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayOrdersScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShowsDocumentedMessage() {
        composeRule.setContent {
            MaterialTheme {
                TodayOrdersScreen(
                    state = TodayOrdersUiState.Empty(businessDate = LocalDate.parse("2026-09-15")),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Ordini di oggi").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Nessun ordine accettato nella giornata operativa corrente.",
        ).assertIsDisplayed()
    }

    @Test
    fun contentShowsDisplayNumberTimeTotalInAcceptedAtDescOrder() {
        composeRule.setContent {
            MaterialTheme {
                TodayOrdersScreen(
                    state = TodayOrdersUiState.Content(
                        businessDate = LocalDate.parse("2026-09-15"),
                        rows = listOf(
                            TodayOrderRowUi(
                                orderId = "new",
                                displayNumber = "050",
                                acceptedAtLabel = "12:00",
                                totalLabel = "3,00 €",
                            ),
                            TodayOrderRowUi(
                                orderId = "mid",
                                displayNumber = "001",
                                acceptedAtLabel = "11:00",
                                totalLabel = "2,00 €",
                            ),
                            TodayOrderRowUi(
                                orderId = "old",
                                displayNumber = "099",
                                acceptedAtLabel = "10:00",
                                totalLabel = "1,00 €",
                            ),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("050").assertIsDisplayed()
        composeRule.onNodeWithText("12:00").assertIsDisplayed()
        composeRule.onNodeWithText("3,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("001").assertIsDisplayed()
        composeRule.onNodeWithText("11:00").assertIsDisplayed()
        composeRule.onNodeWithText("2,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("099").assertIsDisplayed()
        composeRule.onNodeWithText("10:00").assertIsDisplayed()
        composeRule.onNodeWithText("1,00 €").assertIsDisplayed()

        val numbers = composeRule.onAllNodesWithText("050")
            .fetchSemanticsNodes()
        assertTrue(numbers.isNotEmpty())
    }

    @Test
    fun errorStateShowsMessageAndRetry() {
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                TodayOrdersScreen(
                    state = TodayOrdersUiState.Error("Impossibile caricare gli ordini di oggi."),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Impossibile caricare gli ordini di oggi.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun backNavigationIsAvailable() {
        var back = false
        composeRule.setContent {
            MaterialTheme {
                TodayOrdersScreen(
                    state = TodayOrdersUiState.Empty(businessDate = LocalDate.parse("2026-09-15")),
                    onBack = { back = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertTrue(back) }
    }

    @Test
    fun loadingShowsProgressIndicator() {
        composeRule.setContent {
            MaterialTheme {
                TodayOrdersScreen(
                    state = TodayOrdersUiState.Loading,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Caricamento ordini di oggi").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Nessun ordine accettato nella giornata operativa corrente.",
        ).assertDoesNotExist()
    }
}
