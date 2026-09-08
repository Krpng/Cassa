package it.krpng.cassa.feature.order

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OrderItemDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun genericEditorExposesQuantityNoteAndOptionalManualPriceActions() {
        var saved = 0
        var back = 0
        composeRule.setContent {
            val state = remember {
                mutableStateOf(
                    OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Coca Cola",
                        automaticUnitPrice = Money.ofCents(250),
                        quantityInput = "2",
                    ),
                )
            }
            MaterialTheme {
                OrderItemDetailScreen(
                    state = state.value,
                    onBack = { back += 1 },
                    onRetry = {},
                    onQuantityChanged = { state.value = state.value.copy(quantityInput = it) },
                    onDecreaseQuantity = {},
                    onIncreaseQuantity = {},
                    onNoteChanged = { state.value = state.value.copy(note = it) },
                    onStartManualPriceEdit = {
                        state.value = state.value.copy(manualPriceInput = "2,50")
                    },
                    onManualPriceChanged = {
                        state.value = state.value.copy(manualPriceInput = it)
                    },
                    onSave = { saved += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Coca Cola").assertIsDisplayed()
        composeRule.onNodeWithText("Prezzo automatico: 2,50 €").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.onNodeWithContentDescription("Quantità riga").performTextReplacement("3")
        composeRule.onNodeWithContentDescription("Nota riga").performTextReplacement("Senza ghiaccio")
        composeRule.onNodeWithText("MODIFICA PREZZO").performClick()
        composeRule.onNodeWithContentDescription("Prezzo manuale unitario")
            .performTextReplacement("2,00")
        composeRule.onNodeWithText("SALVA").performScrollTo().assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, saved)
            assertEquals(1, back)
        }
    }

    @Test
    fun invalidStateShowsErrorsAndDoesNotExposeAutomaticPriceReset() {
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Crocchè",
                        automaticUnitPrice = Money.ofCents(250),
                        quantityInput = "0",
                        manualPriceInput = "7,123",
                        validationErrors = OrderItemFormErrors(
                            quantity = "Quantità non valida.",
                            manualPrice = "Prezzo non valido.",
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onQuantityChanged = {},
                    onDecreaseQuantity = {},
                    onIncreaseQuantity = {},
                    onNoteChanged = {},
                    onStartManualPriceEdit = {},
                    onManualPriceChanged = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Quantità non valida.").assertIsDisplayed()
        composeRule.onNodeWithText("Prezzo non valido.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPRISTINA PREZZO AUTOMATICO").assertDoesNotExist()
    }

    @Test
    fun loadingAndUnavailableStatesAreExplicit() {
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = false,
                        errorMessage = "Riga ordine non disponibile.",
                    ),
                    onBack = {},
                    onRetry = {},
                    onQuantityChanged = {},
                    onDecreaseQuantity = {},
                    onIncreaseQuantity = {},
                    onNoteChanged = {},
                    onStartManualPriceEdit = {},
                    onManualPriceChanged = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Riga ordine non disponibile.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").assertIsDisplayed()
        composeRule.onNodeWithText("SALVA").assertDoesNotExist()
    }
}
