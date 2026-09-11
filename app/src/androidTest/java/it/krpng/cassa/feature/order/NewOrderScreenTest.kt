package it.krpng.cassa.feature.order

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        composeRule.onNodeWithText("ORDINE CORRENTE").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi un prodotto per iniziare l'ordine.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun persistedOrderLineDispatchesItsStableItemIdForEditing() {
        val selectedItemIds = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        orderLines = listOf(
                            DraftOrderLine(
                                itemId = "pizza-id",
                                quantity = 2,
                                productName = "Margherita snapshot",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(1_400),
                            ),
                            DraftOrderLine(
                                itemId = "coca-id",
                                quantity = 3,
                                productName = "Coca Cola",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(750),
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onOrderItemSelected = { selectedItemIds += it },
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("Margherita snapshot").assertIsDisplayed()
        composeRule.onNodeWithText("14,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("Coca Cola").assertIsDisplayed()
        composeRule.onNodeWithText("7,50 €").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi un prodotto per iniziare l'ordine.")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "Apri dettaglio riga: 2x Margherita snapshot, 14,00 €",
        ).performClick()
        composeRule.runOnIdle { assertEquals(listOf("pizza-id"), selectedItemIds) }
    }

    @Test
    fun orderLineQuantityControlsDispatchImmediatelyAndDisableMinusAtOne() {
        val increases = mutableListOf<String>()
        val decreases = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        orderLines = listOf(
                            DraftOrderLine(
                                itemId = "pizza-id",
                                quantity = 2,
                                productName = "Margherita",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(1_400),
                            ),
                            DraftOrderLine(
                                itemId = "single-id",
                                quantity = 1,
                                productName = "Coca Cola",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(250),
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onIncreaseLineQuantity = { increases += it },
                    onDecreaseLineQuantity = { decreases += it },
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Aumenta quantità Margherita").performClick()
        composeRule.onNodeWithContentDescription("Diminuisci quantità Margherita").performClick()
        composeRule.onNodeWithContentDescription("Diminuisci quantità Coca Cola")
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf("pizza-id"), increases)
            assertEquals(listOf("pizza-id"), decreases)
        }
    }

    @Test
    fun removeLineRequiresConfirmationAndCancelWritesNothing() {
        val removals = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        orderLines = listOf(
                            DraftOrderLine(
                                itemId = "pizza-id",
                                quantity = 2,
                                productName = "Margherita",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(1_400),
                            ),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onRemoveLine = { removals += it },
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rimuovi Margherita").performClick()
        composeRule.onNodeWithText("Rimuovere questa riga?").assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), removals) }

        composeRule.onNodeWithContentDescription("Rimuovi Margherita").performClick()
        composeRule.onAllNodesWithText("RIMUOVI")[1].performClick()
        composeRule.runOnIdle { assertEquals(listOf("pizza-id"), removals) }
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

    @Test
    fun customizedPizzaRowExposesPersonalizzataSemanticsWithoutChangingStandardRows() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        orderLines = listOf(
                            DraftOrderLine(
                                itemId = "custom-id",
                                quantity = 2,
                                productName = "Diavola",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(1_600),
                                isCustomizedPizza = true,
                            ),
                            DraftOrderLine(
                                itemId = "standard-id",
                                quantity = 1,
                                productName = "Margherita",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(700),
                                isCustomizedPizza = false,
                            ),
                            DraftOrderLine(
                                itemId = "drink-id",
                                quantity = 1,
                                productName = "Coca Cola",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(250),
                                isCustomizedPizza = false,
                            ),
                        ),
                    ),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            "Apri dettaglio riga: 2x Diavola, 16,00 €, personalizzata",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Apri dettaglio riga: 1x Margherita, 7,00 €",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Apri dettaglio riga: 1x Coca Cola, 2,50 €",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Apri dettaglio riga: 1x Margherita, 7,00 €, personalizzata",
        ).assertDoesNotExist()
    }

    private fun readyState(
        catalogItems: List<OrderCatalogItem> = emptyList(),
        orderLines: List<DraftOrderLine> = emptyList(),
    ): NewOrderUiState.Ready = NewOrderUiState.Ready(
        draftId = "draft-id",
        orderLines = orderLines,
        searchQuery = "",
        selectedFilter = OrderCatalogFilter.ALL,
        catalogItems = catalogItems,
    )
}
