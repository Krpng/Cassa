package it.krpng.cassa.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class DraftRecoveryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resumeReturnsTheExistingDraftId() {
        var resumedDraftId: String? = null
        composeRule.setContent {
            MaterialTheme {
                DraftRecoveryScreen(
                    state = readyState(),
                    onResumeDraft = { resumedDraftId = it },
                    onDeleteRequest = {},
                    onDeleteConfirm = {},
                    onDeleteCancel = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Hai un ordine non completato.").assertIsDisplayed()
        composeRule.onNodeWithText("RIPRENDI").performClick()

        composeRule.runOnIdle {
            assertEquals("draft-id", resumedDraftId)
        }
    }

    @Test
    fun deleteRequiresExplicitConfirmationAndCancelDoesNotDelete() {
        val state = mutableStateOf(readyState())
        var deleteConfirmed = false
        composeRule.setContent {
            MaterialTheme {
                DraftRecoveryScreen(
                    state = state.value,
                    onResumeDraft = {},
                    onDeleteRequest = {
                        state.value = state.value.copy(showDeleteConfirmation = true)
                    },
                    onDeleteConfirm = { deleteConfirmed = true },
                    onDeleteCancel = {
                        state.value = state.value.copy(showDeleteConfirmation = false)
                    },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("ELIMINA").performClick()
        composeRule.onNodeWithText("Eliminare l'ordine in corso?").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(deleteConfirmed) }

        composeRule.onNodeWithText("ANNULLA").performClick()

        composeRule.onNodeWithText("Eliminare l'ordine in corso?").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(deleteConfirmed) }

        composeRule.onNodeWithText("ELIMINA").performClick()
        composeRule.onAllNodesWithText("ELIMINA")[1].performClick()
        composeRule.runOnIdle { assertEquals(true, deleteConfirmed) }
    }

    private fun readyState(): DraftRecoveryUiState.DraftAvailable =
        DraftRecoveryUiState.DraftAvailable(
            draft = Order(
                id = "draft-id",
                status = OrderStatus.DRAFT,
                displayNumber = null,
                numberingMode = null,
                numberingCycle = null,
                businessDate = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                acceptedAt = null,
                total = Money.ofCents(850),
                generalNote = null,
                sourceOrderId = null,
                items = listOf(
                    OrderItem(
                        id = "item-id",
                        productId = null,
                        productNameSnapshot = "Margherita",
                        productPrintedNameSnapshot = "MARGHERITA",
                        categorySnapshot = ProductCategory.PIZZA,
                        quantity = 1,
                        baseUnitPrice = Money.ofCents(700),
                        automaticExtrasTotal = Money.ZERO,
                        manualUnitPrice = Money.ofCents(850),
                        finalUnitPrice = Money.ofCents(850),
                        automaticExtrasPricingSnapshot = true,
                        note = null,
                        createdSequence = 1,
                        additions = emptyList(),
                        removals = emptyList(),
                    ),
                ),
            ),
        )
}
