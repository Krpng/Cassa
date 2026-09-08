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
import androidx.compose.ui.test.performTextReplacement
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
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
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
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
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
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("Questo ordine non è una bozza modificabile.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("RIPROVA").performClick()
        composeRule.runOnIdle { assertEquals(1, retryClicks) }
    }

    @Test
    fun searchFiltersAndIngredientMatchRemainVisibleWithProductActions() {
        var enteredQuery: String? = null
        var selectedFilter: OrderCatalogFilter? = null
        composeRule.setContent {
            val query = remember { mutableStateOf("") }
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
                    ).copy(searchQuery = query.value),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {
                        enteredQuery = it
                        query.value = it
                    },
                    onFilterSelected = { selectedFilter = it },
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
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
        composeRule.onNodeWithText("+").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .performTextReplacement("parm")

        composeRule.runOnIdle {
            assertEquals(OrderCatalogFilter.DRINKS, selectedFilter)
            assertEquals("parm", enteredQuery)
        }
    }

    @Test
    fun productRowTapAndQuickAddDispatchDistinctActionsForTheSameProduct() {
        val openedProductIds = mutableListOf<Long>()
        val quickAddedProductIds = mutableListOf<Long>()
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        catalogItems = listOf(
                            OrderCatalogItem(
                                productId = 42,
                                name = "Margherita",
                                price = it.krpng.cassa.core.money.Money.ofCents(700),
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = { openedProductIds += it },
                    onQuickAdd = { quickAddedProductIds += it },
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Apri dettagli Margherita").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(42L), openedProductIds)
            assertEquals(emptyList<Long>(), quickAddedProductIds)
        }

        composeRule.onNodeWithContentDescription("Aggiungi Margherita").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(42L), openedProductIds)
            assertEquals(listOf(42L), quickAddedProductIds)
        }
    }

    @Test
    fun quickAddProgressKeepsTheActionAvailableAndErrorCanBeDismissed() {
        var quickAddClicks = 0
        var dismissClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        catalogItems = listOf(
                            OrderCatalogItem(
                                productId = 42,
                                name = "Margherita",
                                price = it.krpng.cassa.core.money.Money.ofCents(700),
                            ),
                        ),
                    ).copy(
                        quickAddInProgressProductIds = setOf(42),
                        quickAddError = "Impossibile aggiungere il prodotto. Riprova.",
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = { quickAddClicks += 1 },
                    onDismissQuickAddError = { dismissClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Aggiunta in corso per Margherita")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aggiungi Margherita").performClick()
        composeRule.onNodeWithText("Impossibile aggiungere il prodotto. Riprova.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("CHIUDI").performClick()

        composeRule.runOnIdle {
            assertEquals(1, quickAddClicks)
            assertEquals(1, dismissClicks)
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
