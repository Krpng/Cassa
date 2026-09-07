package it.krpng.cassa.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nonEmptyDraftShowsDocumentedBannerAndResumesSameDraft() {
        var resumedDraftId: String? = null
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        activeDraft = HomeDraftSummary(
                            draftId = "draft-id",
                            itemCount = 3,
                            total = Money.ofCents(2_750),
                        ),
                    ),
                    onNewOrder = {},
                    onResumeDraft = { resumedDraftId = it },
                    onConflictResume = {},
                    onConflictReplaceRequest = {},
                    onConflictCancel = {},
                    onReplaceConfirm = {},
                    onReplaceCancel = {},
                    onTodayOrders = {},
                    onArchive = {},
                    onMenu = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("ORDINE IN CORSO").assertIsDisplayed()
        composeRule.onNodeWithText("3 articoli · 27,50 €").assertIsDisplayed()
        composeRule.onNodeWithText("RIPRENDI ORDINE").performClick()
        composeRule.runOnIdle { assertEquals("draft-id", resumedDraftId) }
    }

    @Test
    fun emptyDraftStateDoesNotShowBannerAndKeepsHomeActionsAvailable() {
        var newOrderClicks = 0
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false),
                    onNewOrder = { newOrderClicks += 1 },
                    onResumeDraft = {},
                    onConflictResume = {},
                    onConflictReplaceRequest = {},
                    onConflictCancel = {},
                    onReplaceConfirm = {},
                    onReplaceCancel = {},
                    onTodayOrders = {},
                    onArchive = {},
                    onMenu = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("ORDINE IN CORSO").assertDoesNotExist()
        composeRule.onNodeWithText("RIPRENDI ORDINE").assertDoesNotExist()
        composeRule.onNodeWithText("NUOVO ORDINE").performClick()
        composeRule.runOnIdle { assertEquals(1, newOrderClicks) }
    }

    @Test
    fun existingDraftConflictOffersResumeReplaceAndCancel() {
        var resumed = false
        var cancelled = false
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        newOrderConflict = draftSummary(),
                    ),
                    onNewOrder = {},
                    onResumeDraft = {},
                    onConflictResume = { resumed = true },
                    onConflictReplaceRequest = {},
                    onConflictCancel = { cancelled = true },
                    onReplaceConfirm = {},
                    onReplaceCancel = {},
                    onTodayOrders = {},
                    onArchive = {},
                    onMenu = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Esiste già un ordine in corso.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPRENDI").performClick()
        composeRule.runOnIdle {
            assertEquals(true, resumed)
            assertFalse(cancelled)
        }
    }

    @Test
    fun replaceDraftRequiresSecondaryConfirmationAndCanBeCancelled() {
        val state = mutableStateOf(
            HomeUiState(
                isLoading = false,
                newOrderConflict = draftSummary(),
            ),
        )
        var replacementConfirmed = false
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    state = state.value,
                    onNewOrder = {},
                    onResumeDraft = {},
                    onConflictResume = {},
                    onConflictReplaceRequest = {
                        state.value = state.value.copy(showReplaceConfirmation = true)
                    },
                    onConflictCancel = {},
                    onReplaceConfirm = { replacementConfirmed = true },
                    onReplaceCancel = {
                        state.value = state.value.copy(showReplaceConfirmation = false)
                    },
                    onTodayOrders = {},
                    onArchive = {},
                    onMenu = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("ELIMINA E CREA NUOVO").performClick()
        composeRule.onNodeWithText("Eliminare l'ordine in corso?").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(replacementConfirmed) }

        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.onNodeWithText("Eliminare l'ordine in corso?").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(replacementConfirmed) }
    }

    private fun draftSummary(): HomeDraftSummary = HomeDraftSummary(
        draftId = "draft-id",
        itemCount = 1,
        total = Money.ofCents(750),
    )
}
