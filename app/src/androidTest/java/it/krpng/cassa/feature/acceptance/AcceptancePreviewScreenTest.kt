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
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithContentDescription("Totale anteprima: 17,50 €")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA BOZZA").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("ACCETTA").assertIsEnabled()
        composeRule.onNodeWithText("ACCETTA").performClick()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, acceptClicks)
        }
    }

    @Test
    fun emptyDraftDoesNotShowStampaBozza() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = AcceptancePreviewUiState.EmptyDraft,
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }

        composeRule.onNodeWithText("STAMPA BOZZA").assertDoesNotExist()
        composeRule.onNodeWithText("ACCETTA").assertDoesNotExist()
        composeRule.onNodeWithText("Aggiungi almeno un prodotto per accettare l'ordine.")
            .assertIsDisplayed()
    }

    @Test
    fun printingDisablesStampaBozzaAndAccept() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = readyState(),
                    draftPrintState = DraftPrintUiState.Printing,
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }

        composeRule.onNodeWithText("STAMPA IN CORSO…").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA BOZZA").assertDoesNotExist()
        composeRule.onNodeWithText("ACCETTA").assertIsNotEnabled()
        composeRule.onNodeWithText("ANNULLA").assertIsEnabled()
    }

    @Test
    fun stampaBozzaTapEmitsExactlyOneCallback() {
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = readyState(),
                    draftPrintState = DraftPrintUiState.Idle,
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                    onDraftPrint = { taps += 1 },
                )
            }
        }

        composeRule.onNodeWithText("STAMPA BOZZA").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun draftPrintSuccessMessageIsVisible() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = readyState(),
                    draftPrintState = DraftPrintUiState.Success(),
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }

        composeRule.onNodeWithText("Bozza inviata alla stampante").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA BOZZA").assertIsEnabled()
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

    @Test
    fun acceptT019AcceptedShowsNumberTotalDisabledPrintHomeAndNewOrder() {
        var homeClicks = 0
        var newOrderClicks = 0
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = AcceptancePreviewUiState.Accepted(
                        orderId = "order-1",
                        displayNumber = "001",
                        total = Money.ofCents(1_400),
                        acceptedAt = java.time.Instant.parse("2026-09-14T18:00:00Z"),
                        businessDate = java.time.LocalDate.parse("2026-09-14"),
                        sections = listOf(
                            AcceptancePreviewSectionUi(
                                title = "PIZZE",
                                lines = listOf(
                                    AcceptancePreviewLineUi(
                                        itemId = "p1",
                                        quantity = 2,
                                        productName = "Margherita",
                                        productPrintedName = "MARGHERITA",
                                        additionNames = emptyList(),
                                        removalNames = emptyList(),
                                        note = null,
                                        finalUnitPrice = Money.ofCents(700),
                                        lineTotal = Money.ofCents(1_400),
                                    ),
                                ),
                            ),
                        ),
                        generalNote = null,
                    ),
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                    onHome = { homeClicks += 1 },
                    onNewOrder = { newOrderClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Numero ordine 001").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale ordine: 14,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("2 × Margherita").assertIsDisplayed()
        composeRule.onNodeWithText("ACCETTA").assertDoesNotExist()
        composeRule.onNodeWithText("ANNULLA").assertDoesNotExist()
        composeRule.onNodeWithText("COMPLETA").assertDoesNotExist()
        composeRule.onNodeWithText("NOTE").assertDoesNotExist()
        composeRule.onNodeWithText("CERCA").assertDoesNotExist()
        composeRule.onNodeWithText("RIMUOVI").assertDoesNotExist()
        composeRule.onNodeWithText("SALVA").assertDoesNotExist()
        composeRule.onNodeWithText("STAMPA").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA").assertIsEnabled()
        composeRule.onNodeWithText("STAMPA BOZZA").assertDoesNotExist()
        composeRule.onNodeWithText("RISTAMPA").assertDoesNotExist()
        composeRule.onNodeWithText("HOME").assertIsEnabled()
        composeRule.onNodeWithText("NUOVO ORDINE").assertIsEnabled()
        composeRule.onNodeWithText("HOME").performClick()
        composeRule.onNodeWithText("NUOVO ORDINE").performClick()
        composeRule.runOnIdle {
            assertEquals(1, homeClicks)
            assertEquals(1, newOrderClicks)
        }
    }

    @Test
    fun acceptedStampaTapEmitsExactlyOneCallback() {
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = acceptedState(),
                    acceptedPrintState = AcceptedPrintUiState.Idle,
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                    onAcceptedPrint = { taps += 1 },
                )
            }
        }
        composeRule.onNodeWithText("STAMPA").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun acceptedPrintingDisablesStampa() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = acceptedState(),
                    acceptedPrintState = AcceptedPrintUiState.Printing,
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }
        composeRule.onNodeWithText("STAMPA IN CORSO…").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA").assertDoesNotExist()
        composeRule.onNodeWithText("STAMPA BOZZA").assertDoesNotExist()
        composeRule.onNodeWithText("NUOVO ORDINE").assertIsNotEnabled()
    }

    @Test
    fun acceptedPrintSuccessMessageIsVisible() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = acceptedState(),
                    acceptedPrintState = AcceptedPrintUiState.Success(),
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }
        composeRule.onNodeWithText("Ordine inviato alla stampante").assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA").assertIsEnabled()
    }

    @Test
    fun acceptedPrintFailureAfterAcceptMessageIsVisible() {
        composeRule.setContent {
            MaterialTheme {
                AcceptancePreviewScreen(
                    state = acceptedState(),
                    acceptedPrintState = AcceptedPrintUiState.Error(
                        "Ordine accettato. Nessuna stampante selezionata",
                    ),
                    onBack = {},
                    onRetry = {},
                    onAccept = {},
                )
            }
        }
        composeRule.onNodeWithText("Ordine accettato. Nessuna stampante selezionata")
            .assertIsDisplayed()
        composeRule.onNodeWithText("STAMPA").assertIsEnabled()
        composeRule.onNodeWithText("RISTAMPA").assertDoesNotExist()
    }

    private fun readyState(): AcceptancePreviewUiState.Ready =
        AcceptancePreviewUiState.Ready(
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
                            lineTotal = Money.ofCents(700),
                        ),
                    ),
                ),
            ),
            generalNote = null,
            orderTotal = OrderTotalResult.Success(Money.ofCents(700)),
        )

    private fun acceptedState(): AcceptancePreviewUiState.Accepted =
        AcceptancePreviewUiState.Accepted(
            orderId = "order-1",
            displayNumber = "001",
            total = Money.ofCents(1_400),
            acceptedAt = java.time.Instant.parse("2026-09-14T18:00:00Z"),
            businessDate = java.time.LocalDate.parse("2026-09-14"),
            sections = listOf(
                AcceptancePreviewSectionUi(
                    title = "PIZZE",
                    lines = listOf(
                        AcceptancePreviewLineUi(
                            itemId = "p1",
                            quantity = 2,
                            productName = "Margherita",
                            productPrintedName = "MARGHERITA",
                            additionNames = emptyList(),
                            removalNames = emptyList(),
                            note = null,
                            finalUnitPrice = Money.ofCents(700),
                            lineTotal = Money.ofCents(1_400),
                        ),
                    ),
                ),
            ),
            generalNote = null,
        )
}