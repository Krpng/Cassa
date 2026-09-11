package it.krpng.cassa.feature.order

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.repository.RoomAdditionRepository
import it.krpng.cassa.data.repository.RoomOrderRepository
import it.krpng.cassa.data.repository.RoomProductRepository
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.usecase.SplitStandardPizzaItem
import it.krpng.cassa.domain.usecase.UpdateOrderItem
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-004: the aggregated standard pizza editor must split a single unit only when the user saves
 * a valid customization, driving the real ViewModel over a real Room database.
 */
@RunWith(AndroidJUnit4::class)
class OrderItemDetailSplitFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: CassaDatabase
    private lateinit var repository: RoomOrderRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = FIXED_NOW
            },
            transactionRunner = RoomDatabaseTransactionRunner(database),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun modifyOneSplitsASinglePizzaOnlyWhenTheCustomizationIsSaved() {
        val (orderId, sourceItemId) = seedAggregatedStandardPizza()
        showEditor(orderId, sourceItemId)

        composeRule.onNodeWithText("Vuoi modificare una pizza o tutte?").assertIsDisplayed()
        composeRule.onNodeWithText("MODIFICA UNA").performClick()
        composeRule.waitForIdle()

        // Choosing the scope alone must not write anything.
        val afterScope = loadOrder(orderId)
        assertEquals(1, afterScope.items.size)
        assertEquals(3, afterScope.items.single().quantity)

        composeRule.onNodeWithContentDescription("Aggiunta Acciughe, non selezionata")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Selecting an addition is still only editor state.
        assertEquals(1, loadOrder(orderId).items.size)

        // SALVA is sticky: it must be reachable without scrolling the modifier list.
        composeRule.onNodeWithText("SALVA").performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { loadOrder(orderId).items.size == 2 }

        val after = loadOrder(orderId)
        val kept = after.items.single { it.id == sourceItemId }
        val created = after.items.single { it.id != sourceItemId }

        assertEquals(3, after.items.sumOf { it.quantity })
        assertEquals(2, kept.quantity)
        assertEquals(1, created.quantity)
        assertEquals(1, kept.createdSequence)
        assertEquals(2, created.createdSequence)

        assertTrue(kept.additions.isEmpty())
        assertNull(kept.note)
        assertEquals(700L, kept.finalUnitPrice.cents)

        assertEquals(listOf("Acciughe"), created.additions.map { it.nameSnapshot })
        assertEquals(850L, created.finalUnitPrice.cents)
        assertEquals("Margherita", created.productNameSnapshot)
        assertEquals(ProductCategory.PIZZA, created.categorySnapshot)
    }

    @Test
    fun cancellingTheAggregatedPromptLeavesTheRowUntouched() {
        val (orderId, sourceItemId) = seedAggregatedStandardPizza()
        showEditor(orderId, sourceItemId)

        composeRule.onNodeWithText("Vuoi modificare una pizza o tutte?").assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA").performClick()
        composeRule.waitForIdle()

        val after = loadOrder(orderId)
        assertEquals(1, after.items.size)
        assertEquals(3, after.items.single().quantity)
        assertTrue(after.items.single().additions.isEmpty())
    }

    private fun seedAggregatedStandardPizza(): Pair<String, String> = runBlocking {
        val draft = (repository.createDraft() as CreateDraftResult.Created).draft
        database.productDao().insert(
            ProductEntity(
                id = 41,
                name = "Margherita",
                normalizedName = "margherita",
                printedName = "MARGHERITA",
                category = ProductCategory.PIZZA,
                priceCents = 700,
                automaticExtrasPricing = true,
                active = true,
                createdAt = FIXED_NOW.toEpochMilli(),
                updatedAt = FIXED_NOW.toEpochMilli(),
            ),
        )
        database.additionDao().insert(
            AdditionEntity(
                name = "Acciughe",
                normalizedName = "acciughe",
                printedName = null,
                priceCents = 150,
                active = true,
                createdAt = FIXED_NOW.toEpochMilli(),
                updatedAt = FIXED_NOW.toEpochMilli(),
            ),
        )
        repeat(3) { repository.quickAddStandard(draft.id, 41) }
        draft.id to requireNotNull(repository.getById(draft.id)).items.single().id
    }

    private fun showEditor(orderId: String, orderItemId: String) {
        val viewModel = OrderItemDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    OrderItemDetailViewModel.ORDER_ID_ARGUMENT to orderId,
                    OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT to orderItemId,
                ),
            ),
            orderRepository = repository,
            additionRepository = RoomAdditionRepository(database.additionDao()),
            productRepository = RoomProductRepository(database.productDao()),
            updateOrderItem = UpdateOrderItem(repository),
            splitStandardPizzaItem = SplitStandardPizzaItem(repository),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            MaterialTheme {
                OrderItemDetailScreen(
                    state = state,
                    onBack = {},
                    onRetry = viewModel::retry,
                    onQuantityChanged = viewModel::updateQuantity,
                    onDecreaseQuantity = viewModel::decreaseQuantity,
                    onIncreaseQuantity = viewModel::increaseQuantity,
                    onNoteChanged = viewModel::updateNote,
                    onStartManualPriceEdit = viewModel::startManualPriceEdit,
                    onManualPriceChanged = viewModel::updateManualPrice,
                    onResetManualPrice = viewModel::resetManualPrice,
                    onSave = viewModel::save,
                    onAdditionToggled = viewModel::toggleAddition,
                    onRemovalToggled = viewModel::toggleRemoval,
                    onCancelQuantityIncrease = viewModel::cancelQuantityIncrease,
                    onConfirmQuantityIncrease = viewModel::confirmQuantityIncrease,
                    onCancelAggregatedPizzaEdit = viewModel::cancelAggregatedPizzaEdit,
                    onModifyOne = viewModel::selectModifyOne,
                    onModifyAll = viewModel::selectModifyAll,
                )
            }
        }
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Margherita").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun loadOrder(orderId: String): Order = runBlocking {
        requireNotNull(repository.getById(orderId))
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
    }
}
