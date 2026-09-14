package it.krpng.cassa.feature.acceptance

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.pricing.OrderTotalResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AcceptancePreviewScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyPreviewShowsSectionsSnapshotsNoteTotalAndEnabledAccept() {
        var backClicks = 0
        var acceptClicks = 0
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = AcceptancePreviewUiState.Ready(
                        draftId = "draft-1",
                        sections = listOf(
                            AcceptancePreviewSectionUi(
                                title = "PIZZE",
                                lines = listOf(
                                    AcceptancePreviewLineUi(
                                        itemId = "p1",
                                        quantity = 2,
                                        productName = "Margherita",
                                        productPrintedName = "MARGHERITA",
                                        additionNames = listOf("Funghi"),
                                        removalNames = listOf("Basilico"),
                                        note = "ben cotta",
                                        finalUnitPrice = Money.ofCents(800),
                                        lineTotal = Money.ofCents(1_600),
                                    ),
                                ),
                            ),
                            AcceptancePreviewSectionUi(
                                title = "BIBITE",
                                lines = listOf(
                                    AcceptancePreviewLineUi(
                                        itemId = "b1",
                                        quantity = 1,
                                        productName = "Acqua",
                                        productPrintedName = "ACQUA",
                                        additionNames = emptyList(),
                                        removalNames = emptyList(),
                                        note = null,
                                        finalUnitPrice = Money.ofCents(150),
                                        lineTotal = Money.ofCents(150),
                                    ),
                                ),
                            ),
                        ),
                        generalNote = "Tavolo 4",
                        orderTotal = OrderTotalResult.Success(Money.ofCents(1_750)),
                    ),
                    onBack = { backClicks += 1 },
                    onRetry = {},
                    onAccept = { acceptClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("PIZZE").assertIsDisplayed()
        composeRule.onNodeWithText("BIBITE").assertIsDisplayed()
        composeRule.onNodeWithText("FRITTURA").assertDoesNotExist()
        composeRule.onNodeWithText("2 × Margherita").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiunte: Funghi").assertIsDisplayed()
        composeRule.onNodeWithText("Rimossi: Basilico").assertIsDisplayed()
        composeRule.onNodeWithText("Nota: ben cotta").assertIsDisplayed()
        composeRule.onNodeWithText("Tavolo 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale anteprima: 17,50 €").assertIsDisplayed()
        composeRule.onNodeWithText("ACCETTA").assertIsEnabled()
        composeRule.onNodeWithText("ACCETTA").performClick()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, acceptClicks)
        }
    }

    @Test
    fun overflowShowsErrorAndKeepsAcceptDisabled() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = AcceptancePreviewUiState.Ready(
                        draftId = "draft-1",
                        sections = listOf(
                            AcceptancePreviewSectionUi(
                                title = "PIZZE",
                                lines = listOf(
                                    AcceptancePreviewLineUi(
                                        itemId = "p1",
                                        quantity = 1,
                                        productName = "Margherita",
                                        productPrintedName = "MARGHERITA",
                                        additionNames = emptyList(),
                                        removalNames = emptyList(),
                                        note = null,
                                        finalUnitPrice = Money.ofCents(700),
                                        lineTotal = null,
                                    ),
                                ),
                            ),
                        ),
                        generalNote = null,
                        orderTotal = OrderTotalResult.AmountOverflow,
                    ),
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }

        composeRule.onNodeWithText("Totale non calcolabile").assertIsDisplayed()
        composeRule.onNodeWithText("L'accettazione non è disponibile.").assertIsDisplayed()
        composeRule.onNodeWithText("ACCETTA").assertIsNotEnabled()
    }
}
