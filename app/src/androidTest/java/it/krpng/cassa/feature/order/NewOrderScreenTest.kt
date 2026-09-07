package it.krpng.cassa.feature.order

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                    state = readyState(),
                    onBack = { backClicks += 1 },
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
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
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
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
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Questo ordine non è una bozza modificabile.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").performClick()
        composeRule.runOnIdle { assertEquals(1, retryClicks) }
    }

    @Test
    fun searchFiltersAndIngredientMatchAreVisibleWithoutQuickAddBehavior() {
        var enteredQuery: String? = null
        var selectedFilter: OrderCatalogFilter? = null
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        catalogItems = listOf(
                            OrderCatalogItem(
                                productId = 1,
                                name = "Quattro formaggi",
                                price = it.krpng.cassa.core.money.Money.ofCents(900),
                                matchedIngredient = "Parmigiano Reggiano",
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = { enteredQuery = it },
                    onFilterSelected = { selectedFilter = it },
                )
            }
        }

        composeRule.onNodeWithText("TUTTI").assertIsDisplayed()
        composeRule.onNodeWithText("PIZZE").assertIsDisplayed()
        composeRule.onNodeWithText("FRITTURA").assertIsDisplayed()
        composeRule.onNodeWithText("BIBITE").performClick()
        composeRule.onNodeWithText("Quattro formaggi").assertIsDisplayed()
        composeRule.onNodeWithText("Contiene: Parmigiano Reggiano").assertIsDisplayed()
        composeRule.onNodeWithText("9,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("+").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .performTextInput("parm")

        composeRule.runOnIdle {
            assertEquals(OrderCatalogFilter.DRINKS, selectedFilter)
            assertEquals("parm", enteredQuery)
        }
    }

    private fun readyState(
        catalogItems: List<OrderCatalogItem> = emptyList(),
    ): NewOrderUiState.Ready = NewOrderUiState.Ready(
        draftId = "draft-id",
        isDraftEmpty = true,
        searchQuery = "",
        selectedFilter = OrderCatalogFilter.ALL,
        catalogItems = catalogItems,
    )
}
