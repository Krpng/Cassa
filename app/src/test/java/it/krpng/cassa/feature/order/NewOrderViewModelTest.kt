package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import it.krpng.cassa.domain.usecase.AddProductToDraft
import it.krpng.cassa.domain.usecase.ChangeQuantity
import it.krpng.cassa.domain.usecase.RemoveOrderItem
import it.krpng.cassa.domain.usecase.UpdateGeneralNote
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewOrderViewModelTest {
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
    fun `ORDER-026 initial general note is null and editor starts empty`() = runTest(mainDispatcher) {
        val viewModel = viewModel(FakeOrderRepository(MutableStateFlow(draft())), "draft-id")
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertNull(ready.persistedGeneralNote)
        assertEquals("", ready.generalNoteEditor)
        assertFalse(ready.canSaveGeneralNote)
    }

    @Test
    fun `ORDER-040 empty draft live total is zero from persisted items`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            FakeOrderRepository(
                MutableStateFlow(
                    draft().copy(total = Money.ofCents(99_99)),
                ),
            ),
            "draft-id",
        )
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertTrue(ready.isDraftEmpty)
        assertEquals(OrderTotalResult.Success(Money.ZERO), ready.orderTotal)
    }

    @Test
    fun `ORDER-041 single persisted line drives live total ignoring orders totalCents`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            total = Money.ofCents(50_000),
                            items = listOf(orderItem(quantity = 2, unitPriceCents = 700)),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            val ready = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals(OrderTotalResult.Success(Money.ofCents(1_400)), ready.orderTotal)
            assertEquals(Money.ofCents(1_400), ready.orderLines.single().lineTotal)
        }

    @Test
    fun `ORDER-042 multiple persisted lines sum into live total`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            FakeOrderRepository(
                MutableStateFlow(
                    draft().copy(
                        items = listOf(
                            orderItem(id = "a", quantity = 2, unitPriceCents = 700, createdSequence = 1),
                            orderItem(
                                id = "b",
                                name = "Coca",
                                quantity = 3,
                                unitPriceCents = 250,
                                createdSequence = 2,
                            ),
                        ),
                    ),
                ),
            ),
            "draft-id",
        )
        advanceUntilIdle()

        assertEquals(
            OrderTotalResult.Success(Money.ofCents(2_150)),
            (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
        )
    }

    @Test
    fun `ORDER-043 quantity change on persisted items updates live total`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(
                draft().copy(items = listOf(orderItem(quantity = 1, unitPriceCents = 700))),
            )
            val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
            advanceUntilIdle()
            assertEquals(
                OrderTotalResult.Success(Money.ofCents(700)),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )

            orders.value = draft().copy(
                items = listOf(orderItem(quantity = 3, unitPriceCents = 700)),
            )
            advanceUntilIdle()

            val ready = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals(OrderTotalResult.Success(Money.ofCents(2_100)), ready.orderTotal)
            assertEquals(Money.ofCents(2_100), ready.orderLines.single().lineTotal)
        }

    @Test
    fun `ORDER-044 remove last line yields empty draft zero total`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(
            draft().copy(items = listOf(orderItem(quantity = 2, unitPriceCents = 700))),
        )
        val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
        advanceUntilIdle()

        orders.value = draft().copy(items = emptyList(), total = Money.ofCents(1_400))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertTrue(ready.isDraftEmpty)
        assertEquals(OrderTotalResult.Success(Money.ZERO), ready.orderTotal)
    }

    @Test
    fun `ORDER-045 quick add persisted emission updates live total`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(draft())
        val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
        advanceUntilIdle()
        assertEquals(
            OrderTotalResult.Success(Money.ZERO),
            (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
        )

        orders.value = draft().copy(items = listOf(orderItem(quantity = 1, unitPriceCents = 700)))
        advanceUntilIdle()

        assertEquals(
            OrderTotalResult.Success(Money.ofCents(700)),
            (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
        )
    }

    @Test
    fun `ORDER-046 addition with automatic extras true uses persisted final unit price`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            items = listOf(
                                orderItem(
                                    finalUnitPriceCents = 900,
                                    baseUnitPriceCents = 700,
                                    automaticExtrasTotalCents = 200,
                                    automaticExtrasPricing = true,
                                    additions = listOf(
                                        OrderItemAddition(
                                            id = "add-1",
                                            additionId = 1,
                                            nameSnapshot = "Bufala",
                                            printedNameSnapshot = "BUFALA",
                                            listedPrice = Money.ofCents(200),
                                            chargedPrice = Money.ofCents(200),
                                            displayOrder = 0,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            assertEquals(
                OrderTotalResult.Success(Money.ofCents(900)),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-047 addition with automatic extras false uses persisted final without surcharge`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            items = listOf(
                                orderItem(
                                    finalUnitPriceCents = 700,
                                    baseUnitPriceCents = 700,
                                    automaticExtrasTotalCents = 0,
                                    automaticExtrasPricing = false,
                                    additions = listOf(
                                        OrderItemAddition(
                                            id = "add-1",
                                            additionId = 1,
                                            nameSnapshot = "Bufala",
                                            printedNameSnapshot = "BUFALA",
                                            listedPrice = Money.ofCents(200),
                                            chargedPrice = Money.ZERO,
                                            displayOrder = 0,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            assertEquals(
                OrderTotalResult.Success(Money.ofCents(700)),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-048 manual price uses persisted final unit price`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            FakeOrderRepository(
                MutableStateFlow(
                    draft().copy(
                        items = listOf(
                            orderItem(
                                finalUnitPriceCents = 1_000,
                                baseUnitPriceCents = 700,
                                manualUnitPriceCents = 1_000,
                            ),
                        ),
                    ),
                ),
            ),
            "draft-id",
        )
        advanceUntilIdle()

        assertEquals(
            OrderTotalResult.Success(Money.ofCents(1_000)),
            (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
        )
    }

    @Test
    fun `ORDER-049 manual euro zero yields zero live total`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            FakeOrderRepository(
                MutableStateFlow(
                    draft().copy(
                        items = listOf(
                            orderItem(
                                quantity = 2,
                                finalUnitPriceCents = 0,
                                baseUnitPriceCents = 700,
                                manualUnitPriceCents = 0,
                            ),
                        ),
                    ),
                ),
            ),
            "draft-id",
        )
        advanceUntilIdle()

        assertEquals(
            OrderTotalResult.Success(Money.ZERO),
            (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
        )
    }

    @Test
    fun `ORDER-050 reset manual price uses restored persisted final unit price`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(
                draft().copy(
                    items = listOf(
                        orderItem(
                            finalUnitPriceCents = 1_000,
                            baseUnitPriceCents = 700,
                            manualUnitPriceCents = 1_000,
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
            advanceUntilIdle()

            orders.value = draft().copy(
                items = listOf(
                    orderItem(
                        finalUnitPriceCents = 700,
                        baseUnitPriceCents = 700,
                        manualUnitPriceCents = null,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                OrderTotalResult.Success(Money.ofCents(700)),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-051 atomic split sums both persisted rows without special split logic`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            items = listOf(
                                orderItem(
                                    id = "source",
                                    quantity = 2,
                                    unitPriceCents = 700,
                                    createdSequence = 1,
                                ),
                                orderItem(
                                    id = "custom",
                                    quantity = 1,
                                    finalUnitPriceCents = 900,
                                    baseUnitPriceCents = 700,
                                    createdSequence = 2,
                                    additions = listOf(
                                        OrderItemAddition(
                                            id = "add-1",
                                            additionId = 1,
                                            nameSnapshot = "Bufala",
                                            printedNameSnapshot = "BUFALA",
                                            listedPrice = Money.ofCents(200),
                                            chargedPrice = Money.ofCents(200),
                                            displayOrder = 0,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            // 2*700 + 1*900 = 2300 — plain sum of persisted line totals
            assertEquals(
                OrderTotalResult.Success(Money.ofCents(2_300)),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-052 item note alone does not change live total`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(
            draft().copy(items = listOf(orderItem(quantity = 2, unitPriceCents = 700))),
        )
        val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
        advanceUntilIdle()
        val before = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

        orders.value = draft().copy(
            items = listOf(
                orderItem(quantity = 2, unitPriceCents = 700, note = "Ben cotta"),
            ),
        )
        advanceUntilIdle()

        assertEquals(before, (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal)
        assertEquals(OrderTotalResult.Success(Money.ofCents(1_400)), before)
    }

    @Test
    fun `ORDER-053 general note save does not change live total`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(
            draft().copy(items = listOf(orderItem(quantity = 2, unitPriceCents = 700))),
        )
        val repository = FakeOrderRepository(orders)
        val viewModel = viewModel(repository, "draft-id")
        advanceUntilIdle()
        val before = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

        viewModel.updateGeneralNoteEditor("Consegna alle 21")
        viewModel.saveGeneralNote()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertEquals("Consegna alle 21", ready.persistedGeneralNote)
        assertEquals(before, ready.orderTotal)
    }

    @Test
    fun `ORDER-054 dirty unsaved editors do not change live total`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(
            draft().copy(items = listOf(orderItem(quantity = 1, unitPriceCents = 700))),
        )
        val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
        advanceUntilIdle()
        val before = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

        viewModel.updateGeneralNoteEditor("Nota non salvata")
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertEquals("Nota non salvata", ready.generalNoteEditor)
        assertNull(ready.persistedGeneralNote)
        assertEquals(before, ready.orderTotal)
        assertEquals(OrderTotalResult.Success(Money.ofCents(700)), ready.orderTotal)
    }

    @Test
    fun `ORDER-055 catalog live price change does not alter persisted draft total`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(
                draft().copy(
                    items = listOf(
                        orderItem(productId = 1, quantity = 2, unitPriceCents = 700),
                    ),
                ),
            )
            val products = MutableStateFlow(
                listOf(product(1, "Margherita", ProductCategory.PIZZA)),
            )
            val viewModel = viewModel(FakeOrderRepository(orders), "draft-id", products)
            advanceUntilIdle()
            val before = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

            products.value = listOf(
                product(1, "Margherita", ProductCategory.PIZZA).copy(price = Money.ofCents(9_999)),
            )
            advanceUntilIdle()

            assertEquals(before, (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal)
            assertEquals(OrderTotalResult.Success(Money.ofCents(1_400)), before)
        }

    @Test
    fun `ORDER-056 reopen derives the same total from persisted items not totalCents`() =
        runTest(mainDispatcher) {
            val persisted = draft().copy(
                total = Money.ofCents(1),
                items = listOf(orderItem(quantity = 2, unitPriceCents = 700)),
            )
            val orders = MutableStateFlow<Order?>(persisted)
            val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
            advanceUntilIdle()
            val first = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

            viewModel.retry()
            advanceUntilIdle()
            val second = (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal

            assertEquals(OrderTotalResult.Success(Money.ofCents(1_400)), first)
            assertEquals(first, second)
        }

    @Test
    fun `ORDER-057 view model surfaces multiplication overflow without corrupted total`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            items = listOf(
                                orderItem(
                                    finalUnitPriceCents = Long.MAX_VALUE,
                                    quantity = 2,
                                ),
                            ),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            assertEquals(
                OrderTotalResult.AmountOverflow,
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-058 view model surfaces sum overflow without corrupted total`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                FakeOrderRepository(
                    MutableStateFlow(
                        draft().copy(
                            items = listOf(
                                orderItem(id = "a", finalUnitPriceCents = Long.MAX_VALUE),
                                orderItem(id = "b", finalUnitPriceCents = 1, createdSequence = 2),
                            ),
                        ),
                    ),
                ),
                "draft-id",
            )
            advanceUntilIdle()

            assertEquals(
                OrderTotalResult.AmountOverflow,
                (viewModel.uiState.value as NewOrderUiState.Ready).orderTotal,
            )
        }

    @Test
    fun `ORDER-037 dirty general note editor is not overwritten by unrelated order emissions`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(draft())
            val viewModel = viewModel(FakeOrderRepository(orders), "draft-id")
            advanceUntilIdle()

            viewModel.updateGeneralNoteEditor("Consegna alle 21")
            advanceUntilIdle()
            assertEquals(
                "Consegna alle 21",
                (viewModel.uiState.value as NewOrderUiState.Ready).generalNoteEditor,
            )

            orders.value = draft().copy(items = listOf(orderItem()), generalNote = null)
            advanceUntilIdle()

            val ready = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals("Consegna alle 21", ready.generalNoteEditor)
            assertTrue(ready.canSaveGeneralNote)
            assertNull(ready.persistedGeneralNote)
        }

    @Test
    fun `ORDER-038 save failure keeps local general note text and remains dirty`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(draft())
            val repository = FakeOrderRepository(orders).apply {
                updateGeneralNoteResult = UpdateGeneralNoteResult.PersistenceFailure
            }
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            viewModel.updateGeneralNoteEditor("Testo locale")
            advanceUntilIdle()
            viewModel.saveGeneralNote()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals("Testo locale", ready.generalNoteEditor)
            assertTrue(ready.canSaveGeneralNote)
            assertEquals("Impossibile salvare la nota ordine. Riprova.", ready.generalNoteError)
            assertEquals(1, repository.updateGeneralNoteCalls.size)
            assertNull(orders.value?.generalNote)
        }

    @Test
    fun `ORDER-027 successful save persists and clears dirty editor`() = runTest(mainDispatcher) {
        val orders = MutableStateFlow<Order?>(draft())
        val repository = FakeOrderRepository(orders)
        val viewModel = viewModel(repository, "draft-id")
        advanceUntilIdle()

        viewModel.updateGeneralNoteEditor("  Consegna alle 21  ")
        advanceUntilIdle()
        viewModel.saveGeneralNote()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as NewOrderUiState.Ready
        assertEquals("Consegna alle 21", ready.generalNoteEditor)
        assertEquals("Consegna alle 21", ready.persistedGeneralNote)
        assertFalse(ready.canSaveGeneralNote)
        assertNull(ready.generalNoteError)
        assertEquals(
            listOf("draft-id" to "  Consegna alle 21  "),
            repository.updateGeneralNoteCalls,
        )
    }

    @Test
    fun `loads the requested draft id and reflects repository emissions without writes`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(draft())
            val repository = FakeOrderRepository(orders)
            val viewModel = viewModel(repository, "draft-id")

            advanceUntilIdle()

            val initial = viewModel.uiState.value as NewOrderUiState.Ready
            assertEquals("draft-id", initial.draftId)
            assertTrue(initial.isDraftEmpty)
            assertEquals(listOf("draft-id"), repository.observedIds)

            orders.value = draft().copy(items = listOf(orderItem()))
            advanceUntilIdle()

            val updated = viewModel.uiState.value as NewOrderUiState.Ready
            assertFalse(updated.isDraftEmpty)
            assertEquals(
                listOf(DraftOrderLine("item-id", 1, "Margherita", Money.ofCents(700))),
                updated.orderLines,
            )
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
            assertEquals(0, repository.replaceCalls)
        }

    @Test
    fun `persisted lines are ordered by creation sequence and expose quantities and line totals`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(
                draft().copy(
                    items = listOf(
                        orderItem(
                            id = "coca-id",
                            name = "Coca Cola",
                            quantity = 3,
                            unitPriceCents = 250,
                            createdSequence = 2,
                        ),
                        orderItem(
                            id = "pizza-id",
                            name = "Margherita",
                            quantity = 2,
                            unitPriceCents = 700,
                            createdSequence = 1,
                        ),
                    ),
                ),
            )
            val repository = FakeOrderRepository(orders)
            val viewModel = viewModel(repository, "draft-id")

            advanceUntilIdle()

            assertEquals(
                listOf(
                    DraftOrderLine("pizza-id", 2, "Margherita", Money.ofCents(1_400)),
                    DraftOrderLine("coca-id", 3, "Coca Cola", Money.ofCents(750)),
                ),
                (viewModel.uiState.value as NewOrderUiState.Ready).orderLines,
            )
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
            assertEquals(0, repository.replaceCalls)
            assertTrue(repository.quickAddCalls.isEmpty())
        }

    @Test
    fun `order lines react to persisted state and remain independent from live catalog changes`() =
        runTest(mainDispatcher) {
            val orders = MutableStateFlow<Order?>(
                draft().copy(
                    items = listOf(
                        orderItem(
                            productId = 1,
                            name = "Margherita snapshot",
                            quantity = 1,
                        ),
                    ),
                ),
            )
            val products = MutableStateFlow(
                listOf(product(1, "Margherita live", ProductCategory.PIZZA)),
            )
            val repository = FakeOrderRepository(orders)
            val viewModel = viewModel(repository, "draft-id", products)
            advanceUntilIdle()

            assertEquals(
                "Margherita snapshot",
                (viewModel.uiState.value as NewOrderUiState.Ready)
                    .orderLines.single().productName,
            )

            products.value = listOf(product(1, "Margherita rinominata", ProductCategory.PIZZA))
            advanceUntilIdle()
            assertEquals(
                "Margherita snapshot",
                (viewModel.uiState.value as NewOrderUiState.Ready)
                    .orderLines.single().productName,
            )

            orders.value = orders.value?.copy(
                items = listOf(
                    orderItem(
                        productId = 1,
                        name = "Margherita snapshot",
                        quantity = 2,
                    ),
                ),
            )
            advanceUntilIdle()

            val line = (viewModel.uiState.value as NewOrderUiState.Ready).orderLines.single()
            assertEquals(2, line.quantity)
            assertEquals(Money.ofCents(1_400), line.lineTotal)
            assertEquals(0, repository.createCalls)
            assertEquals(0, repository.deleteCalls)
            assertEquals(0, repository.replaceCalls)
            assertTrue(repository.quickAddCalls.isEmpty())
        }

    @Test
    fun `missing id and deleted draft are handled without creating another draft`() =
        runTest(mainDispatcher) {
            val missingIdRepository = FakeOrderRepository(MutableStateFlow(draft()))
            val missingIdViewModel = viewModel(missingIdRepository, "")
            advanceUntilIdle()

            assertEquals(NewOrderUiState.NotFound, missingIdViewModel.uiState.value)
            assertTrue(missingIdRepository.observedIds.isEmpty())

            val orders = MutableStateFlow<Order?>(draft())
            val deletedRepository = FakeOrderRepository(orders)
            val deletedViewModel = viewModel(deletedRepository, "draft-id")
            advanceUntilIdle()
            orders.value = null
            advanceUntilIdle()

            assertEquals(NewOrderUiState.NotFound, deletedViewModel.uiState.value)
            assertEquals(0, deletedRepository.createCalls)
        }

    @Test
    fun `accepted order is never exposed as an editable draft`() = runTest(mainDispatcher) {
        val accepted = draft().copy(status = OrderStatus.ACCEPTED)
        val repository = FakeOrderRepository(MutableStateFlow(accepted))

        val viewModel = viewModel(repository, accepted.id)
        advanceUntilIdle()

        assertEquals(NewOrderUiState.NotEditable, viewModel.uiState.value)
        assertEquals(0, repository.createCalls)
        assertEquals(0, repository.deleteCalls)
        assertEquals(0, repository.replaceCalls)
    }

    @Test
    fun `active catalog is reactive and category filters combine with blank query`() =
        runTest(mainDispatcher) {
            val products = MutableStateFlow(
                listOf(
                    product(1, "Margherita", ProductCategory.PIZZA),
                    product(2, "Crocchè", ProductCategory.FRITTURA),
                    product(3, "Acqua", ProductCategory.BIBITA),
                    product(4, "Pizza inattiva", ProductCategory.PIZZA, active = false),
                ),
            )
            val viewModel = viewModel(
                repository = FakeOrderRepository(MutableStateFlow(draft())),
                draftId = "draft-id",
                products = products,
            )
            advanceUntilIdle()

            assertEquals(
                listOf("Acqua", "Crocchè", "Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            viewModel.selectFilter(OrderCatalogFilter.PIZZAS)
            advanceUntilIdle()
            assertEquals(
                listOf("Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            products.value = products.value + product(5, "Marinara", ProductCategory.PIZZA)
            advanceUntilIdle()
            assertEquals(
                listOf("Margherita", "Marinara"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )
        }

    @Test
    fun `search reuses documented ranking and exposes ingredient match reason`() =
        runTest(mainDispatcher) {
            val parmigiano = ProductIngredient(
                ingredient = ingredient(10, "Parmigiano Reggiano"),
                displayOrder = 0,
            )
            val products = MutableStateFlow(
                listOf(
                    product(1, "Pizza Margherita", ProductCategory.PIZZA),
                    product(2, "Marinara", ProductCategory.PIZZA),
                    product(
                        3,
                        "Quattro formaggi",
                        ProductCategory.PIZZA,
                        ingredients = listOf(parmigiano),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = FakeOrderRepository(MutableStateFlow(draft())),
                draftId = "draft-id",
                products = products,
            )

            viewModel.updateSearchQuery("  MAR  ")
            advanceUntilIdle()
            assertEquals(
                listOf("Marinara", "Pizza Margherita"),
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.map { it.name },
            )

            viewModel.updateSearchQuery("PARMÌGIANO")
            advanceUntilIdle()
            val ingredientMatch =
                (viewModel.uiState.value as NewOrderUiState.Ready).catalogItems.single()
            assertEquals("Quattro formaggi", ingredientMatch.name)
            assertEquals("Parmigiano Reggiano", ingredientMatch.matchedIngredient)
        }

    @Test
    fun `repository failure is safe and retry re-subscribes to the same id`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(
                observedOrders = flow { error("database unavailable") },
            )
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            assertEquals(
                NewOrderUiState.Failure("Impossibile caricare l'ordine o il catalogo."),
                viewModel.uiState.value,
            )

            repository.observedOrders = MutableStateFlow(draft())
            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is NewOrderUiState.Ready)
            assertEquals(listOf("draft-id", "draft-id"), repository.observedIds)
        }

    @Test
    fun `quick add uses the route draft id and does not create another draft`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draft()))
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            viewModel.quickAdd(42)
            viewModel.quickAdd(42)
            advanceUntilIdle()

            assertEquals(
                listOf("draft-id" to 42L, "draft-id" to 42L),
                repository.quickAddCalls,
            )
            assertEquals(0, repository.createCalls)
            assertTrue(
                (viewModel.uiState.value as NewOrderUiState.Ready)
                    .quickAddInProgressProductIds.isEmpty(),
            )
        }

    @Test
    fun `quick add maps unavailable product to a dismissible user error`() =
        runTest(mainDispatcher) {
            val repository = FakeOrderRepository(MutableStateFlow(draft())).apply {
                quickAddResult = QuickAddStandardResult.ProductUnavailable
            }
            val viewModel = viewModel(repository, "draft-id")
            advanceUntilIdle()

            viewModel.quickAdd(42)
            advanceUntilIdle()

            assertEquals(
                "Il prodotto non è più disponibile.",
                (viewModel.uiState.value as NewOrderUiState.Ready).quickAddError,
            )

            viewModel.dismissQuickAddError()
            advanceUntilIdle()
            assertNull((viewModel.uiState.value as NewOrderUiState.Ready).quickAddError)
        }

    private fun viewModel(
        repository: OrderRepository,
        draftId: String,
        products: Flow<List<Product>> = MutableStateFlow(emptyList()),
    ): NewOrderViewModel = NewOrderViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(NewOrderViewModel.DRAFT_ID_ARGUMENT to draftId),
        ),
        orderRepository = repository,
        productRepository = FakeProductRepository(products),
        addProductToDraft = AddProductToDraft(repository),
        changeQuantity = ChangeQuantity(repository),
        removeOrderItem = RemoveOrderItem(repository),
        updateGeneralNote = UpdateGeneralNote(repository),
    )

    private class FakeOrderRepository(
        var observedOrders: Flow<Order?>,
    ) : OrderRepository {
        val observedIds = mutableListOf<String>()
        var createCalls = 0
        var deleteCalls = 0
        var replaceCalls = 0
        var quickAddResult: QuickAddStandardResult =
            QuickAddStandardResult.Added("item-id")
        val quickAddCalls = mutableListOf<Pair<String, Long>>()
        var updateGeneralNoteResult: UpdateGeneralNoteResult = UpdateGeneralNoteResult.Updated
        val updateGeneralNoteCalls = mutableListOf<Pair<String, String?>>()

        override suspend fun getById(orderId: String): Order? = null

        override fun observeById(orderId: String): Flow<Order?> {
            observedIds += orderId
            return observedOrders
        }

        override fun observeActiveDraft(): Flow<Order?> = MutableStateFlow(null)

        override suspend fun getActiveDraft(): Order? = null

        override suspend fun createDraft(): CreateDraftResult {
            createCalls += 1
            return CreateDraftResult.AlreadyExists
        }

        override suspend fun deleteDraft(orderId: String): DeleteDraftResult {
            deleteCalls += 1
            return DeleteDraftResult.NotFoundOrNotDraft
        }

        override suspend fun replaceDraft(orderId: String): ReplaceDraftResult {
            replaceCalls += 1
            return ReplaceDraftResult.OriginalNotFoundOrNotDraft
        }

        override suspend fun quickAddStandard(
            orderId: String,
            productId: Long,
        ): QuickAddStandardResult {
            quickAddCalls += orderId to productId
            return quickAddResult
        }

        override suspend fun updateOrderItem(
            orderId: String,
            orderItemId: String,
            quantity: Int,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>?,
            selectedRemovalIngredientIds: List<Long>?,
            customizationQuantityIntent: it.krpng.cassa.domain.repository.CustomizationQuantityIntent,
        ): UpdateOrderItemResult = error("Not used")

        override suspend fun splitStandardPizzaItem(
            orderId: String,
            orderItemId: String,
            note: String?,
            manualUnitPrice: Money?,
            selectedAdditionIds: List<Long>,
            selectedRemovalIngredientIds: List<Long>,
        ): it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult = error("Not used")

        override suspend fun changeQuantity(
            orderId: String,
            orderItemId: String,
            quantity: Int,
        ): it.krpng.cassa.domain.repository.ChangeQuantityResult = error("Not used")

        override suspend fun removeOrderItem(
            orderId: String,
            orderItemId: String,
        ): it.krpng.cassa.domain.repository.RemoveOrderItemResult = error("Not used")

        override suspend fun updateGeneralNote(
            orderId: String,
            generalNote: String?,
        ): UpdateGeneralNoteResult {
            updateGeneralNoteCalls += orderId to generalNote
            val result = updateGeneralNoteResult
            if (result is UpdateGeneralNoteResult.Updated) {
                val flow = observedOrders as? MutableStateFlow<Order?>
                val current = flow?.value
                if (flow != null && current != null) {
                    flow.value = current.copy(
                        generalNote = it.krpng.cassa.domain.order.GeneralNoteNormalizer
                            .normalize(generalNote),
                    )
                }
            }
            return result
        }
    }

    private class FakeProductRepository(
        private val products: Flow<List<Product>>,
    ) : ProductRepository {
        override fun observeAll(): Flow<List<Product>> = products

        override fun observeActive(): Flow<List<Product>> = products

        override suspend fun getById(productId: Long): Product? = null

        override suspend fun create(product: Product): Long = error("Not used")

        override suspend fun update(product: Product): Boolean = error("Not used")

        override suspend fun activate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")

        override suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean =
            error("Not used")
    }

    private fun draft(): Order = Order(
        id = "draft-id",
        status = OrderStatus.DRAFT,
        displayNumber = null,
        numberingMode = null,
        numberingCycle = null,
        businessDate = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        acceptedAt = null,
        total = Money.ZERO,
        generalNote = null,
        sourceOrderId = null,
        items = emptyList(),
    )

    private fun orderItem(
        id: String = "item-id",
        productId: Long? = null,
        name: String = "Margherita",
        quantity: Int = 1,
        unitPriceCents: Long = 700,
        baseUnitPriceCents: Long? = null,
        automaticExtrasTotalCents: Long = 0,
        finalUnitPriceCents: Long? = null,
        manualUnitPriceCents: Long? = null,
        automaticExtrasPricing: Boolean = true,
        note: String? = null,
        createdSequence: Int = 1,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem {
        val base = baseUnitPriceCents ?: unitPriceCents
        val finalPrice = finalUnitPriceCents ?: unitPriceCents
        return OrderItem(
            id = id,
            productId = productId,
            productNameSnapshot = name,
            productPrintedNameSnapshot = name.uppercase(),
            categorySnapshot = ProductCategory.PIZZA,
            quantity = quantity,
            baseUnitPrice = Money.ofCents(base),
            automaticExtrasTotal = Money.ofCents(automaticExtrasTotalCents),
            manualUnitPrice = manualUnitPriceCents?.let(Money::ofCents),
            finalUnitPrice = Money.ofCents(finalPrice),
            automaticExtrasPricingSnapshot = automaticExtrasPricing,
            note = note,
            createdSequence = createdSequence,
            additions = additions,
            removals = removals,
        )
    }

    private fun product(
        id: Long,
        name: String,
        category: ProductCategory,
        active: Boolean = true,
        ingredients: List<ProductIngredient> = emptyList(),
    ): Product = Product(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        printedName = null,
        category = category,
        price = Money.ofCents(700),
        automaticExtrasPricing = true,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ingredients = ingredients,
    )

    private fun ingredient(id: Long, name: String): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = TextNormalizer.normalize(name),
        active = true,
    )
}
