package it.krpng.cassa.feature.accepteddetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AcceptedOrderDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun archT021EditControlsAbsent() {
        setContent(contentState())
        composeRule.onNodeWithText("+").assertDoesNotExist()
        composeRule.onNodeWithText("-").assertDoesNotExist()
        composeRule.onNodeWithText("RIMUOVI").assertDoesNotExist()
        composeRule.onNodeWithText("SALVA").assertDoesNotExist()
        composeRule.onNodeWithText("ACCETTA").assertDoesNotExist()
        composeRule.onNodeWithText("COMPLETA").assertDoesNotExist()
    }

    @Test
    fun archT022StampaVisibleAndDisabled() {
        setContent(contentState())
        composeRule.onNodeWithText("STAMPA").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stampa non disponibile").assertIsNotEnabled()
    }

    @Test
    fun archT023RistampaAbsent() {
        setContent(contentState())
        composeRule.onNodeWithText("RISTAMPA").assertDoesNotExist()
    }

    @Test
    fun archT024NuovoOrdineDaQuestoAbsent() {
        setContent(contentState())
        composeRule.onNodeWithText("NUOVO ORDINE DA QUESTO").assertDoesNotExist()
    }

    @Test
    fun archT025IndietroInvokesBackToToday() {
        var backClicks = 0
        setContent(contentState(), onBackToToday = { backClicks += 1 })
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun archT026HomeInvokesHome() {
        var homeClicks = 0
        setContent(contentState(), onHome = { homeClicks += 1 })
        composeRule.onNodeWithText("HOME").performClick()
        composeRule.runOnIdle { assertEquals(1, homeClicks) }
    }

    @Test
    fun contentShowsHeaderNoteAndSnapshots() {
        setContent(contentState())
        composeRule.onNodeWithText("042").assertIsDisplayed()
        composeRule.onNodeWithText("15/09/2026 12:30").assertIsDisplayed()
        composeRule.onNodeWithText("Margherita", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Consegna alle 21").assertIsDisplayed()
        composeRule.onNodeWithText("Nota: Ben cotta").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiunte: Bufala").assertIsDisplayed()
        composeRule.onNodeWithText("Rimossi: Pomodoro").assertIsDisplayed()
        composeRule.onNodeWithText("Prezzo manuale: 6,50 €").assertIsDisplayed()
    }

    @Test
    fun unavailableShowsMessageWithoutStaleContent() {
        setContent(AcceptedOrderDetailUiState.Unavailable)
        composeRule.onNodeWithText("Ordine non disponibile.").assertIsDisplayed()
        composeRule.onNodeWithText("042").assertDoesNotExist()
        composeRule.onNodeWithText("STAMPA").assertIsDisplayed()
        composeRule.onNodeWithText("HOME").assertIsDisplayed()
    }

    private fun setContent(
        state: AcceptedOrderDetailUiState,
        onBackToToday: () -> Unit = {},
        onHome: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                AcceptedOrderDetailScreen(
                    state = state,
                    onBackToToday = onBackToToday,
                    onHome = onHome,
                )
            }
        }
    }

    private fun contentState(): AcceptedOrderDetailUiState.Content =
        AcceptedOrderDetailUiState.Content(
            orderId = "o1",
            displayNumber = "042",
            acceptedAtLabel = "15/09/2026 12:30",
            totalLabel = Money.ofCents(1_400).formatEur(),
            total = Money.ofCents(1_400),
            generalNote = "Consegna alle 21",
            sections = listOf(
                AcceptedOrderDetailSectionUi(
                    title = "PIZZE",
                    lines = listOf(
                        AcceptedOrderDetailLineUi(
                            itemId = "i1",
                            quantity = 2,
                            productName = "Margherita",
                            productPrintedName = "MARGHERITA",
                            additionNames = listOf("Bufala"),
                            removalNames = listOf("Pomodoro"),
                            note = "Ben cotta",
                            finalUnitPrice = Money.ofCents(700),
                            lineTotal = Money.ofCents(1_400),
                            manualUnitPrice = Money.ofCents(650),
                        ),
                    ),
                ),
            ),
        )
}
