package it.krpng.cassa.feature.acceptance

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptancePreviewViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ACCEPT-T008 observe only — zero repository writes`() = runTest(mainDispatcher) {
        val repository = FakeOrderRepository(draft())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AcceptancePreviewUiState.Ready)
        assertEquals(0, repository.writeCount)
        assertEquals(listOf("observeById"), repository.readOps)
    }

    @Test
    fun `ACCEPT-T009 preview does not call numbering APIs`() {
        val parameterTypes = AcceptancePreviewViewModel::class.java.constructors
            .single()
            .parameterTypes
            .map { it.simpleName }

        assertEquals(listOf("SavedStateHandle", "OrderRepository"), parameterTypes)
    }

    @Test
    fun `ACCEPT-T012 preview total ignores stale orders totalCents`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1, unitCents = 700, qty = 2),
            ),
            staleTotalCents = 1L,
            generalNote = "senza cipolla",
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(
            OrderTotalResult.Success(Money.ofCents(1_400)),
            ready.orderTotal,
        )
        assertEquals("senza cipolla", ready.generalNote)
        assertFalse(ready.isAcceptEnabled)
    }

    @Test
    fun `ACCEPT-T010 and T011 sections keep category and sequence order`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(id = "b", category = ProductCategory.BIBITA, sequence = 1, name = "Acqua"),
                item(id = "p2", category = ProductCategory.PIZZA, sequence = 2, name = "AAA"),
                item(id = "p1", category = ProductCategory.PIZZA, sequence = 1, name = "ZZZ"),
                item(id = "f", category = ProductCategory.FRITTURA, sequence = 1, name = "Crochette"),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(listOf("PIZZE", "FRITTURA", "BIBITE"), ready.sections.map { it.title })
        assertEquals(listOf("p1", "p2"), ready.sections[0].lines.map { it.itemId })
        assertEquals("ZZZ", ready.sections[0].lines[0].productName)
    }

    @Test
    fun `snapshots additions removals and notes are shown`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(
                    id = "custom",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    name = "Margherita",
                    printed = "MARGHERITA",
                    note = "ben cotta",
                    additions = listOf(
                        OrderItemAddition(
                            id = "a1",
                            additionId = 1L,
                            nameSnapshot = "Funghi",
                            printedNameSnapshot = "FUNGHI",
                            listedPrice = Money.ofCents(100),
                            chargedPrice = Money.ofCents(100),
                            displayOrder = 0,
                        ),
                    ),
                    removals = listOf(
                        OrderItemRemoval(
                            id = "r1",
                            ingredientId = 2L,
                            nameSnapshot = "Basilico",
                            displayOrder = 0,
                        ),
                    ),
                    unitCents = 800,
                ),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val line = (viewModel.uiState.value as AcceptancePreviewUiState.Ready)
            .sections.single().lines.single()
        assertEquals("Margherita", line.productName)
        assertEquals("MARGHERITA", line.productPrintedName)
        assertEquals(listOf("Funghi"), line.additionNames)
        assertEquals(listOf("Basilico"), line.removalNames)
        assertEquals("ben cotta", line.note)
        assertEquals(Money.ofCents(800), line.finalUnitPrice)
        assertEquals(Money.ofCents(800), line.lineTotal)
    }

    @Test
    fun `overflow keeps accept disabled`() = runTest(mainDispatcher) {
        val draft = draft(
            items = listOf(
                item(
                    id = "overflow",
                    category = ProductCategory.PIZZA,
                    sequence = 1,
                    unitCents = Long.MAX_VALUE / 2,
                    qty = 3,
                ),
            ),
        )
        val viewModel = viewModel(FakeOrderRepository(draft))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as AcceptancePreviewUiState.Ready
        assertEquals(OrderTotalResult.AmountOverflow, ready.orderTotal)
        assertTrue(ready.hasTotalOverflow)
        assertFalse(ready.isAcceptEnabled)
    }

    @Test
    fun `empty draft maps to EmptyDraft state`() = runTest(mainDispatcher) {
        val viewModel = viewModel(FakeOrderRepository(draft(items = emptyList())))
        advanceUntilIdle()
        assertEquals(AcceptancePreviewUiState.EmptyDraft, viewModel.uiState.value)
    }

    private fun viewModel(repository: FakeOrderRepository): AcceptancePreviewViewModel =
        AcceptancePreviewViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(AcceptancePreviewViewModel.DRAFT_ID_ARGUMENT to DRAFT_ID),
            ),
            orderRepository = repository,
        )

    private class FakeOrderRepository(
        initial: Order?,
    ) : OrderRepository {
        private val order = MutableStateFlow(initial)
        var writeCount: Int = 0
            private set
        val readOps = mutableListOf<String>()

        private fun write(): Nothing {
            writeCount += 1
            error("ACCEPT-001 preview must not write")
        }

        override suspend fun getById(orderId: String): Order? {
            readOps += "getById"
            return order.value?.takeIf { it.id == orderId }
        }

        override fun observeById(orderId: String): Flow<Order?> {
            readOps += "observeById"
            return order
        }

        override fun observeActiveDraft(): Flow<Order?> = write()

        override suspend fun getActiveDraft(): Order? = write()

        override suspend fun createDraft(): CreateDraftResult = write()

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult = write()

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult = write()

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult = write()

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: CustomizationQuantityIntent,
        ): UpdateOrderItemResult = write()

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): SplitStandardPizzaItemResult = write()

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): ChangeQuantityResult = write()

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): RemoveOrderItemResult = write()

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult = write()
    }

    private companion object {
        const val DRAFT_ID = "draft-1"
        val NOW: Instant = Instant.parse("2026-09-14T18:00:00Z")

        fun draft(
            items: List<OrderItem> = listOf(
                item(id = "1", category = ProductCategory.PIZZA, sequence = 1),
            ),
            staleTotalCents: Long = 999_999L,
            generalNote: String? = null,
        ): Order = Order(
            id = DRAFT_ID,
            status = OrderStatus.DRAFT,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = NOW,
            updatedAt = NOW,
            acceptedAt = null,
            total = Money.ofCents(staleTotalCents),
            generalNote = generalNote,
            sourceOrderId = null,
            items = items,
        )

        fun item(
            id: String,
            category: ProductCategory,
            sequence: Int,
            name: String = "Prodotto $id",
            printed: String = name.uppercase(),
            note: String? = null,
            additions: List<OrderItemAddition> = emptyList(),
            removals: List<OrderItemRemoval> = emptyList(),
            unitCents: Long = 700,
            qty: Int = 1,
        ): OrderItem = OrderItem(
            id = id,
            productId = null,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printed,
            categorySnapshot = category,
            quantity = qty,
            baseUnitPrice = Money.ofCents(unitCents.coerceAtLeast(0)),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = null,
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = true,
            note = note,
            createdSequence = sequence,
            additions = additions,
            removals = removals,
        )
    }
}
