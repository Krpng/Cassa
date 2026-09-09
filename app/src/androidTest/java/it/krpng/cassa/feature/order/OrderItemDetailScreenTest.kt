package it.krpng.cassa.feature.order

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory
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
        composeRule.onNode(hasScrollAction()).performScrollToIndex(6)
        composeRule.onNodeWithText("MODIFICA PREZZO").performClick()
        composeRule.onNodeWithContentDescription("Prezzo manuale unitario")
            .performTextReplacement("2,00")
        composeRule.onNodeWithText("SALVA").assertIsDisplayed().performClick()

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

    @Test
    fun pizzaAdditionsShowSelectionAndAcceptZeroPriceWithAccessibleToggle() {
        var toggledId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Margherita",
                        automaticUnitPrice = Money.ofCents(700),
                        category = ProductCategory.PIZZA,
                        canEditAdditions = true,
                        additionOptions = listOf(
                            PizzaAdditionOption(10, "Provola", Money.ofCents(150), true),
                            PizzaAdditionOption(11, "Basilico", Money.ZERO, false),
                        ),
                        selectedAdditionIds = listOf(10),
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
                    onAdditionToggled = { toggledId = it },
                )
            }
        }

        composeRule.onNodeWithText("AGGIUNTE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0,00 €").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aggiunta Provola, selezionata")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aggiunta Basilico, non selezionata")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(11L, toggledId) }
    }

    @Test
    fun nonPizzaDoesNotRenderAdditionControls() {
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Crocchè",
                        automaticUnitPrice = Money.ofCents(250),
                        category = ProductCategory.FRITTURA,
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

        composeRule.onNodeWithText("AGGIUNTE").assertDoesNotExist()
        composeRule.onNodeWithText("RIMOZIONI").assertDoesNotExist()
    }

    @Test
    fun pizzaRemovalsShowOnlyProvidedCompositionAndExposeAccessibleToggle() {
        var toggledId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Margherita",
                        automaticUnitPrice = Money.ofCents(700),
                        category = ProductCategory.PIZZA,
                        canEditRemovals = true,
                        removalOptions = listOf(
                            PizzaRemovalOption(20, "Pomodoro", true),
                            PizzaRemovalOption(21, "Mozzarella", false),
                        ),
                        selectedRemovalIngredientIds = listOf(20),
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
                    onRemovalToggled = { toggledId = it },
                )
            }
        }

        composeRule.onNodeWithText("RIMOZIONI").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rimozione Pomodoro, selezionata")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rimozione Mozzarella, non selezionata")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(21L, toggledId) }
    }

    @Test
    fun stickySaveRemainsVisibleWhileLastAdditionCanBeReached() {
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Margherita",
                        automaticUnitPrice = Money.ofCents(700),
                        category = ProductCategory.PIZZA,
                        canEditAdditions = true,
                        additionOptions = (1L..30L).map { id ->
                            PizzaAdditionOption(id, "Aggiunta $id", Money.ofCents(100), false)
                        },
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

        composeRule.onNodeWithText("SALVA").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(36)
        composeRule.onNodeWithContentDescription("Aggiunta Aggiunta 30, non selezionata")
            .assertIsDisplayed()
        composeRule.onNodeWithText("SALVA").assertIsDisplayed()
    }

    @Test
    fun aggregatedPizzaExplainsDisabledAdditionsAndConfirmationActionsAreExplicit() {
        var cancelled = 0
        var continued = 0
        composeRule.setContent {
            MaterialTheme {
                OrderItemDetailScreen(
                    state = OrderItemDetailUiState(
                        isLoading = false,
                        canSave = true,
                        productName = "Margherita",
                        automaticUnitPrice = Money.ofCents(700),
                        quantityInput = "2",
                        category = ProductCategory.PIZZA,
                        canEditAdditions = false,
                        additionMessage = "Questa riga contiene 2 pizze. " +
                            "Le aggiunte possono essere modificate da questa schermata " +
                            "solo quando la quantità è 1.",
                        additionOptions = listOf(
                            PizzaAdditionOption(10, "Provola", Money.ofCents(150), false),
                        ),
                        showQuantityIncreaseConfirmation = true,
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
                    onCancelQuantityIncrease = { cancelled += 1 },
                    onConfirmQuantityIncrease = { continued += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Questa riga contiene 2 pizze.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Applica le personalizzazioni a tutte?").assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.runOnIdle { assertEquals(1, cancelled) }

        composeRule.onNodeWithText("CONTINUA").performClick()
        composeRule.runOnIdle { assertEquals(1, continued) }
    }
}
