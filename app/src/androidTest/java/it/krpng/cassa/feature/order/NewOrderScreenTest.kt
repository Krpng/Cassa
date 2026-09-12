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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale ordine: 0,00 €").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun order040_emptyDraftShowsVisibleZeroTotal() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(),
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

        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale ordine: 0,00 €").assertIsDisplayed()
        composeRule.onNodeWithText("0,00 €").assertIsDisplayed()
    }

    @Test
    fun liveTotalFooterShowsSumOfPersistedLineTotals() {
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
                                itemId = "coca-id",
                                quantity = 3,
                                productName = "Coca Cola",
                                lineTotal = it.krpng.cassa.core.money.Money.ofCents(750),
                            ),
                        ),
                        orderTotal = it.krpng.cassa.domain.pricing.OrderTotalResult.Success(
                            it.krpng.cassa.core.money.Money.ofCents(2_150),
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

        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale ordine: 21,50 €").assertIsDisplayed()
    }

    @Test
    fun liveTotalFooterShowsOverflowWithoutNumericCorruption() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        orderTotal = it.krpng.cassa.domain.pricing.OrderTotalResult.AmountOverflow,
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

        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Totale ordine non calcolabile per overflow")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Totale non calcolabile").assertIsDisplayed()
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
            val mode = remember { mutableStateOf(NewOrderWorkspaceMode.MAIN) }
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        catalogItems = listOf(
                            OrderCatalogItem(
                                productId = 1,
                                name = "Quattro formaggi",
                                price = it.krpng.cassa.core.money.Money.ofCents(900),
                                matchedIngredient = if (mode.value == NewOrderWorkspaceMode.SEARCH) {
                                    "Parmigiano Reggiano"
                                } else {
                                    null
                                },
                            ),
                        ),
                    ).copy(
                        searchQuery = query.value,
                        workspaceMode = mode.value,
                    ),
                    onBack = {},
                    onRetry = {},
                    onOpenSearch = { mode.value = NewOrderWorkspaceMode.SEARCH },
                    onCloseSearch = { mode.value = NewOrderWorkspaceMode.MAIN },
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
        composeRule.onNodeWithContentDescription("Cerca prodotti").performClick()
        composeRule.onNodeWithText("Cerca prodotto").assertIsDisplayed()
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
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Impossibile aggiungere il prodotto. Riprova.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("CHIUDI").performClick()

        composeRule.runOnIdle {
            assertEquals(1, quickAddClicks)
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun order073_customizedPizzaRowExposesPersonalizzataSemanticsWithoutChangingStandardRows() {
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

    @Test
    fun order066_systemBackFromNoteOverlayCancelsWithoutSave() {
        var cancelClicks = 0
        var saveClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(
                        noteOverlayOpen = true,
                        generalNoteEditor = "Modifica locale",
                        canSaveGeneralNote = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onCancelNote = { cancelClicks += 1 },
                    onSaveGeneralNote = { saveClicks += 1 },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("NOTA ORDINE").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(1, cancelClicks)
            assertEquals(0, saveClicks)
        }
    }

    @Test
    fun order068_systemBackFromSearchReturnsToOrder() {
        var closeSearch = 0
        var backClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(workspaceMode = NewOrderWorkspaceMode.SEARCH),
                    onBack = { backClicks += 1 },
                    onRetry = {},
                    onCloseSearch = { closeSearch += 1 },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("Cerca prodotto").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(1, closeSearch)
            assertEquals(0, backClicks)
        }
    }

    @Test
    fun generalNoteSectionExposesMultilineEditorAndExplicitSave() {
        composeRule.setContent {
            val overlayOpen = remember { mutableStateOf(false) }
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(noteOverlayOpen = overlayOpen.value),
                    onBack = {},
                    onRetry = {},
                    onOpenNote = { overlayOpen.value = true },
                    onCancelNote = { overlayOpen.value = false },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("NOTA ORDINE").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Nota ordine").performClick()
        composeRule.onNodeWithText("NOTA ORDINE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nota generale dell'ordine").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Salva nota ordine").assertIsDisplayed()
        composeRule.onNodeWithText("SALVA").assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA").assertIsDisplayed()
    }

    @Test
    fun order059_mainScreenHasNoInlineNoteEditor() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(),
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

        composeRule.onNodeWithContentDescription("Nota generale dell'ordine").assertDoesNotExist()
        composeRule.onNodeWithText("SALVA NOTA").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Nota ordine").assertIsDisplayed()
    }

    @Test
    fun order060_mainScreenHasNoInlineSearchField() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(),
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

        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cerca prodotti").assertIsDisplayed()
        composeRule.onNodeWithText("TUTTI").assertIsDisplayed()
        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
    }

    @Test
    fun order061_062_063_noteOverlayOpenCancelAndPersistedValue() {
        var cancelClicks = 0
        var noteChanges = 0
        composeRule.setContent {
            val overlayOpen = remember { mutableStateOf(true) }
            val editor = remember { mutableStateOf("Consegna alle 21") }
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(
                        noteOverlayOpen = overlayOpen.value,
                        generalNoteEditor = editor.value,
                        persistedGeneralNote = "Consegna alle 21",
                    ),
                    onBack = {},
                    onRetry = {},
                    onCancelNote = {
                        cancelClicks += 1
                        overlayOpen.value = false
                    },
                    onGeneralNoteChanged = {
                        noteChanges += 1
                        editor.value = it
                    },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("NOTA ORDINE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nota generale dell'ordine")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.runOnIdle {
            assertEquals(1, cancelClicks)
            assertEquals(0, noteChanges)
        }
    }

    @Test
    fun order064_065_noteSaveAndFailureKeepOverlaySemantics() {
        var saveClicks = 0
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(
                        noteOverlayOpen = true,
                        generalNoteEditor = "Testo locale",
                        canSaveGeneralNote = true,
                        generalNoteError = "Impossibile salvare la nota ordine. Riprova.",
                    ),
                    onBack = {},
                    onRetry = {},
                    onSaveGeneralNote = { saveClicks += 1 },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("Testo locale").assertIsDisplayed()
        composeRule.onNodeWithText("Impossibile salvare la nota ordine. Riprova.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("NOTA ORDINE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Salva nota ordine").performClick()
        composeRule.runOnIdle { assertEquals(1, saveClicks) }
    }

    @Test
    fun order067_068_searchDedicatedViewAndBack() {
        var closeSearch = 0
        composeRule.setContent {
            val mode = remember { mutableStateOf(NewOrderWorkspaceMode.SEARCH) }
            MaterialTheme {
                NewOrderScreen(
                    state = readyState(
                        catalogItems = listOf(
                            OrderCatalogItem(
                                productId = 1,
                                name = "Margherita",
                                price = it.krpng.cassa.core.money.Money.ofCents(700),
                            ),
                        ),
                    ).copy(workspaceMode = mode.value),
                    onBack = {},
                    onRetry = {},
                    onCloseSearch = {
                        closeSearch += 1
                        mode.value = NewOrderWorkspaceMode.MAIN
                    },
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("Cerca prodotto").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ORDINE CORRENTE").assertDoesNotExist()
        composeRule.onNodeWithText("TOTALE").assertDoesNotExist()
        composeRule.onNodeWithText("TUTTI").assertIsDisplayed()
        composeRule.onNodeWithText("PIZZE").assertIsDisplayed()
        composeRule.onNodeWithText("FRITTURA").assertIsDisplayed()
        composeRule.onNodeWithText("BIBITE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").performClick()
        composeRule.runOnIdle { assertEquals(1, closeSearch) }
        composeRule.onNodeWithText("ORDINE CORRENTE").assertIsDisplayed()
        composeRule.onNodeWithText("TOTALE").assertIsDisplayed()
    }

    @Test
    fun order070_quickAddFromSearchKeepsSearchOpen() {
        var quickAdds = 0
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
                        workspaceMode = NewOrderWorkspaceMode.SEARCH,
                        searchQuery = "mar",
                        searchSelectedFilter = OrderCatalogFilter.PIZZAS,
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {},
                    onProductSelected = {},
                    onQuickAdd = { quickAdds += 1 },
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Aggiungi Margherita").performClick()
        composeRule.onNodeWithText("Cerca prodotto").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .assertIsDisplayed()
        composeRule.onNodeWithText("PIZZE").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, quickAdds) }
    }

    @Test
    fun order075_searchViewShowsCategoryFiltersUnderSearchField() {
        composeRule.setContent {
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(workspaceMode = NewOrderWorkspaceMode.SEARCH),
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

        composeRule.onNodeWithContentDescription("Ricerca prodotti e ingredienti")
            .assertIsDisplayed()
        composeRule.onNodeWithText("TUTTI").assertIsDisplayed()
        composeRule.onNodeWithText("PIZZE").assertIsDisplayed()
        composeRule.onNodeWithText("FRITTURA").assertIsDisplayed()
        composeRule.onNodeWithText("BIBITE").assertIsDisplayed()
        composeRule.onNodeWithText("ORDINE CORRENTE").assertDoesNotExist()
        composeRule.onNodeWithText("TOTALE").assertDoesNotExist()
    }

    @Test
    fun order076_searchFilterChipsDispatchIndependentlyFromMain() {
        var selected: OrderCatalogFilter? = null
        composeRule.setContent {
            val searchFilter = remember { mutableStateOf(OrderCatalogFilter.ALL) }
            MaterialTheme {
                NewOrderScreen(
                    state = readyState().copy(
                        workspaceMode = NewOrderWorkspaceMode.SEARCH,
                        selectedFilter = OrderCatalogFilter.FRIED,
                        searchSelectedFilter = searchFilter.value,
                    ),
                    onBack = {},
                    onRetry = {},
                    onSearchQueryChanged = {},
                    onFilterSelected = {
                        selected = it
                        searchFilter.value = it
                    },
                    onProductSelected = {},
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("PIZZE").performClick()
        composeRule.runOnIdle { assertEquals(OrderCatalogFilter.PIZZAS, selected) }
    }

    @Test
    fun order072_074_headerActionsAndOrd020ControlsRemain() {
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
                    onQuickAdd = {},
                    onDismissQuickAddError = {},
                )
            }
        }

        composeRule.onNodeWithText("NOTE").assertIsDisplayed()
        composeRule.onNodeWithText("CERCA").assertIsDisplayed()
        composeRule.onNodeWithText("← INDIETRO").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nota ordine").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cerca prodotti").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Indietro").assertIsDisplayed()
        listOf("Nota ordine", "Cerca prodotti", "Indietro").forEach { description ->
            val bounds = composeRule.onNodeWithContentDescription(description)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "Touch target for $description must be >= 48dp",
                bounds.width >= 48f && bounds.height >= 48f,
            )
        }
        composeRule.onNodeWithContentDescription("Aumenta quantità Margherita").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Diminuisci quantità Margherita")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rimuovi Margherita").assertIsDisplayed()
    }

    private fun readyState(
        catalogItems: List<OrderCatalogItem> = emptyList(),
        orderLines: List<DraftOrderLine> = emptyList(),
        orderTotal: it.krpng.cassa.domain.pricing.OrderTotalResult? = null,
    ): NewOrderUiState.Ready {
        val resolvedTotal = orderTotal ?: it.krpng.cassa.domain.pricing.OrderTotalResult.Success(
            orderLines.mapNotNull { it.lineTotal }.fold(
                it.krpng.cassa.core.money.Money.ZERO,
            ) { acc, line -> acc + line },
        )
        return NewOrderUiState.Ready(
            draftId = "draft-id",
            orderLines = orderLines,
            orderTotal = resolvedTotal,
            searchQuery = "",
            selectedFilter = OrderCatalogFilter.ALL,
            catalogItems = catalogItems,
        )
    }
}
