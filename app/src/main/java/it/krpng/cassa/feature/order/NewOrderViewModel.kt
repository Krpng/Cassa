package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.order.GeneralNoteNormalizer
import it.krpng.cassa.domain.pricing.CalculateOrderTotal
import it.krpng.cassa.domain.pricing.OrderLineMergeCandidate
import it.krpng.cassa.domain.pricing.OrderLineMergePolicy
import it.krpng.cassa.domain.pricing.OrderTotalResult
import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import it.krpng.cassa.domain.search.ProductSearchEngine
import it.krpng.cassa.domain.usecase.AddProductToDraft
import it.krpng.cassa.domain.usecase.ChangeQuantity
import it.krpng.cassa.domain.usecase.RemoveOrderItem
import it.krpng.cassa.domain.usecase.UpdateGeneralNote
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrderCatalogFilter(
    val label: String,
    internal val category: ProductCategory?,
) {
    ALL("TUTTI", null),
    PIZZAS("PIZZE", ProductCategory.PIZZA),
    FRIED("FRITTURA", ProductCategory.FRITTURA),
    DRINKS("BIBITE", ProductCategory.BIBITA),
}

enum class NewOrderWorkspaceMode {
    MAIN,
    SEARCH,
}

data class OrderCatalogItem(
    val productId: Long,
    val name: String,
    val price: Money,
    val matchedIngredient: String? = null,
)

data class DraftOrderLine(
    val itemId: String,
    val quantity: Int,
    val productName: String,
    val lineTotal: Money?,
    val isCustomizedPizza: Boolean = false,
)

sealed interface NewOrderUiState {
    data object Loading : NewOrderUiState

    data class Ready(
        val draftId: String,
        val orderLines: List<DraftOrderLine>,
        val orderTotal: OrderTotalResult,
        val workspaceMode: NewOrderWorkspaceMode = NewOrderWorkspaceMode.MAIN,
        val noteOverlayOpen: Boolean = false,
        val searchQuery: String,
        val selectedFilter: OrderCatalogFilter,
        val searchSelectedFilter: OrderCatalogFilter = OrderCatalogFilter.ALL,
        val catalogItems: List<OrderCatalogItem>,
        val quickAddInProgressProductIds: Set<Long> = emptySet(),
        val quickAddError: String? = null,
        val lineMutationInProgressItemIds: Set<String> = emptySet(),
        val lineMutationError: String? = null,
        val generalNoteEditor: String = "",
        val persistedGeneralNote: String? = null,
        val canSaveGeneralNote: Boolean = false,
        val generalNoteSaveInProgress: Boolean = false,
        val generalNoteError: String? = null,
    ) : NewOrderUiState {
        val isDraftEmpty: Boolean
            get() = orderLines.isEmpty()
    }

    data object NotFound : NewOrderUiState

    data object NotEditable : NewOrderUiState

    data class Failure(val message: String) : NewOrderUiState
}

@HiltViewModel
class NewOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val addProductToDraft: AddProductToDraft,
    private val changeQuantity: ChangeQuantity,
    private val removeOrderItem: RemoveOrderItem,
    private val updateGeneralNote: UpdateGeneralNote,
) : ViewModel() {
    private val draftId: String = savedStateHandle.get<String>(DRAFT_ID_ARGUMENT).orEmpty()
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(OrderCatalogFilter.ALL)
    private val searchSelectedFilter = MutableStateFlow(OrderCatalogFilter.ALL)
    private val workspaceMode = MutableStateFlow(NewOrderWorkspaceMode.MAIN)
    private val noteOverlayOpen = MutableStateFlow(false)
    private val quickAddOperation = MutableStateFlow(QuickAddOperationState())
    private val lineMutationOperation = MutableStateFlow(LineMutationOperationState())
    private val generalNoteLocal = MutableStateFlow(GeneralNoteLocalState())

    private val _uiState = MutableStateFlow<NewOrderUiState>(NewOrderUiState.Loading)
    val uiState: StateFlow<NewOrderUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeOrderAndCatalog()
    }

    fun retry() {
        generalNoteLocal.value = GeneralNoteLocalState()
        workspaceMode.value = NewOrderWorkspaceMode.MAIN
        searchSelectedFilter.value = OrderCatalogFilter.ALL
        noteOverlayOpen.value = false
        observeOrderAndCatalog()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: OrderCatalogFilter) {
        when (workspaceMode.value) {
            NewOrderWorkspaceMode.MAIN -> selectedFilter.value = filter
            NewOrderWorkspaceMode.SEARCH -> searchSelectedFilter.value = filter
        }
    }

    fun openSearch() {
        if (_uiState.value !is NewOrderUiState.Ready) return
        searchSelectedFilter.value = OrderCatalogFilter.ALL
        workspaceMode.value = NewOrderWorkspaceMode.SEARCH
    }

    fun closeSearch() {
        workspaceMode.value = NewOrderWorkspaceMode.MAIN
    }

    fun openNoteOverlay() {
        if (_uiState.value !is NewOrderUiState.Ready) return
        generalNoteLocal.update { state ->
            state.copy(editor = null, error = null)
        }
        noteOverlayOpen.value = true
    }

    fun cancelNoteOverlay() {
        generalNoteLocal.value = GeneralNoteLocalState()
        noteOverlayOpen.value = false
    }

    fun quickAdd(productId: Long) {
        if (_uiState.value !is NewOrderUiState.Ready) return

        quickAddOperation.update { state -> state.start(productId) }
        viewModelScope.launch {
            val result = try {
                addProductToDraft(draftId, productId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                QuickAddStandardResult.PersistenceFailure
            }
            quickAddOperation.update { state ->
                state.finish(productId, result.toUserMessage())
            }
        }
    }

    fun dismissQuickAddError() {
        quickAddOperation.update { state -> state.copy(error = null) }
    }

    fun increaseLineQuantity(itemId: String) {
        val ready = _uiState.value as? NewOrderUiState.Ready ?: return
        val line = ready.orderLines.firstOrNull { it.itemId == itemId } ?: return
        if (line.quantity == Int.MAX_VALUE) {
            lineMutationOperation.update { state ->
                state.copy(error = "Non è possibile aumentare ulteriormente la quantità.")
            }
            return
        }
        mutateLineQuantity(itemId, line.quantity + 1)
    }

    fun decreaseLineQuantity(itemId: String) {
        val ready = _uiState.value as? NewOrderUiState.Ready ?: return
        val line = ready.orderLines.firstOrNull { it.itemId == itemId } ?: return
        if (line.quantity <= 1) return
        mutateLineQuantity(itemId, line.quantity - 1)
    }

    fun removeLine(itemId: String) {
        val ready = _uiState.value as? NewOrderUiState.Ready ?: return
        if (ready.orderLines.none { it.itemId == itemId }) return
        if (itemId in lineMutationOperation.value.pendingCounts) return

        lineMutationOperation.update { state -> state.start(itemId) }
        viewModelScope.launch {
            val result = try {
                removeOrderItem(draftId, itemId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RemoveOrderItemResult.PersistenceFailure
            }
            lineMutationOperation.update { state ->
                state.finish(itemId, result.toUserMessage())
            }
        }
    }

    fun dismissLineMutationError() {
        lineMutationOperation.update { state -> state.copy(error = null) }
    }

    fun updateGeneralNoteEditor(value: String) {
        generalNoteLocal.update { state ->
            state.copy(editor = value, error = null)
        }
    }

    fun saveGeneralNote() {
        val ready = _uiState.value as? NewOrderUiState.Ready ?: return
        val local = generalNoteLocal.value
        if (local.saveInProgress) return
        val editorSnapshot = local.editor ?: ready.generalNoteEditor
        if (GeneralNoteNormalizer.normalize(editorSnapshot) == ready.persistedGeneralNote) return

        generalNoteLocal.update { state ->
            state.copy(saveInProgress = true, error = null)
        }
        viewModelScope.launch {
            val result = try {
                updateGeneralNote(draftId, editorSnapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateGeneralNoteResult.PersistenceFailure
            }
            generalNoteLocal.update { state ->
                when (result) {
                    UpdateGeneralNoteResult.Updated -> {
                        noteOverlayOpen.value = false
                        state.copy(
                            editor = null,
                            saveInProgress = false,
                            error = null,
                        )
                    }
                    else -> state.copy(
                        editor = editorSnapshot,
                        saveInProgress = false,
                        error = result.toUserMessage(),
                    )
                }
            }
        }
    }

    fun dismissGeneralNoteError() {
        generalNoteLocal.update { state -> state.copy(error = null) }
    }

    private fun mutateLineQuantity(itemId: String, quantity: Int) {
        if (itemId in lineMutationOperation.value.pendingCounts) return

        lineMutationOperation.update { state -> state.start(itemId) }
        viewModelScope.launch {
            val result = try {
                changeQuantity(draftId, itemId, quantity)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ChangeQuantityResult.PersistenceFailure
            }
            lineMutationOperation.update { state ->
                state.finish(itemId, result.toUserMessage())
            }
        }
    }

    private fun observeOrderAndCatalog() {
        observationJob?.cancel()
        if (draftId.isBlank()) {
            _uiState.value = NewOrderUiState.NotFound
            return
        }

        _uiState.value = NewOrderUiState.Loading
        observationJob = viewModelScope.launch {
            try {
                combine(
                    orderRepository.observeById(draftId),
                    productRepository.observeActive(),
                    searchQuery,
                    combine(selectedFilter, searchSelectedFilter) { mainFilter, searchFilter ->
                        CatalogFilterState(
                            mainFilter = mainFilter,
                            searchFilter = searchFilter,
                        )
                    },
                    combine(
                        quickAddOperation,
                        lineMutationOperation,
                        generalNoteLocal,
                        workspaceMode,
                        noteOverlayOpen,
                    ) { quickAdd, lineMutation, generalNote, mode, noteOpen ->
                        WorkspaceObservation(
                            quickAdd = quickAdd,
                            lineMutation = lineMutation,
                            generalNote = generalNote,
                            mode = mode,
                            noteOverlayOpen = noteOpen,
                        )
                    },
                ) { order, products, query, filters, workspace ->
                    when {
                        order == null -> NewOrderUiState.NotFound
                        order.status != OrderStatus.DRAFT -> NewOrderUiState.NotEditable
                        else -> {
                            val editor = workspace.generalNote.editor
                                ?: order.generalNote.orEmpty()
                            val sortedItems = order.items
                                .sortedWith(compareBy({ it.createdSequence }, { it.id }))
                            // Live total from persisted items only — never order.total / catalog.
                            val totals = CalculateOrderTotal.fromPersistedItems(sortedItems)
                            val lineTotalById = totals.lineTotals.associate { line ->
                                line.itemId to line.lineTotal
                            }
                            val catalogItems = when (workspace.mode) {
                                NewOrderWorkspaceMode.MAIN ->
                                    products.toCatalogItems("", filters.mainFilter)
                                NewOrderWorkspaceMode.SEARCH ->
                                    products.toCatalogItems(query, filters.searchFilter)
                            }
                            NewOrderUiState.Ready(
                                draftId = order.id,
                                orderLines = sortedItems.map { item ->
                                    DraftOrderLine(
                                        itemId = item.id,
                                        quantity = item.quantity,
                                        productName = item.productNameSnapshot,
                                        lineTotal = lineTotalById[item.id],
                                        isCustomizedPizza = item.isCustomizedPizzaRow(),
                                    )
                                },
                                orderTotal = totals.orderTotal,
                                workspaceMode = workspace.mode,
                                noteOverlayOpen = workspace.noteOverlayOpen,
                                searchQuery = query,
                                selectedFilter = filters.mainFilter,
                                searchSelectedFilter = filters.searchFilter,
                                catalogItems = catalogItems,
                                quickAddInProgressProductIds =
                                    workspace.quickAdd.pendingCounts.keys,
                                quickAddError = workspace.quickAdd.error,
                                lineMutationInProgressItemIds =
                                    workspace.lineMutation.pendingCounts.keys,
                                lineMutationError = workspace.lineMutation.error,
                                generalNoteEditor = editor,
                                persistedGeneralNote = order.generalNote,
                                canSaveGeneralNote = !workspace.generalNote.saveInProgress &&
                                    GeneralNoteNormalizer.normalize(editor) != order.generalNote,
                                generalNoteSaveInProgress =
                                    workspace.generalNote.saveInProgress,
                                generalNoteError = workspace.generalNote.error,
                            )
                        }
                    }
                }
                    .catch {
                        _uiState.value = NewOrderUiState.Failure(
                            "Impossibile caricare l'ordine o il catalogo.",
                        )
                    }
                    .collect { state ->
                        _uiState.value = state
                    }
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    private fun List<Product>.toCatalogItems(
        query: String,
        filter: OrderCatalogFilter,
    ): List<OrderCatalogItem> {
        val filteredProducts = asSequence()
            .filter(Product::active)
            .filter { product -> filter.category == null || product.category == filter.category }
            .toList()

        if (TextNormalizer.normalize(query).isEmpty()) {
            return filteredProducts
                .sortedWith(compareBy(Product::normalizedName, Product::id))
                .map { product -> product.toCatalogItem() }
        }

        return ProductSearchEngine.search(filteredProducts, query).map { result ->
            result.product.toCatalogItem(result.matchedIngredient)
        }
    }

    private fun Product.toCatalogItem(matchedIngredient: String? = null): OrderCatalogItem =
        OrderCatalogItem(
            productId = id,
            name = name,
            price = price,
            matchedIngredient = matchedIngredient,
        )

    private fun QuickAddStandardResult.toUserMessage(): String? = when (this) {
        is QuickAddStandardResult.Added,
        is QuickAddStandardResult.Merged,
        -> null

        QuickAddStandardResult.OrderNotFound -> "L'ordine non è più disponibile."
        QuickAddStandardResult.OrderNotEditable ->
            "Questo ordine non è una bozza modificabile."
        QuickAddStandardResult.ProductUnavailable ->
            "Il prodotto non è più disponibile."
        QuickAddStandardResult.LimitReached ->
            "Non è possibile aumentare ulteriormente la quantità."
        QuickAddStandardResult.PersistenceFailure ->
            "Impossibile aggiungere il prodotto. Riprova."
    }

    private fun ChangeQuantityResult.toUserMessage(): String? = when (this) {
        ChangeQuantityResult.Updated -> null
        ChangeQuantityResult.OrderNotFound,
        ChangeQuantityResult.ItemNotFound,
        -> "La riga ordine non è più disponibile."
        ChangeQuantityResult.OrderNotEditable ->
            "Questo ordine non è una bozza modificabile."
        ChangeQuantityResult.InvalidQuantity ->
            "Quantità non valida."
        ChangeQuantityResult.PersistenceFailure ->
            "Impossibile aggiornare la quantità. Riprova."
    }

    private fun RemoveOrderItemResult.toUserMessage(): String? = when (this) {
        RemoveOrderItemResult.Removed -> null
        RemoveOrderItemResult.OrderNotFound,
        RemoveOrderItemResult.ItemNotFound,
        -> "La riga ordine non è più disponibile."
        RemoveOrderItemResult.OrderNotEditable ->
            "Questo ordine non è una bozza modificabile."
        RemoveOrderItemResult.PersistenceFailure ->
            "Impossibile rimuovere la riga. Riprova."
    }

    private fun UpdateGeneralNoteResult.toUserMessage(): String? = when (this) {
        UpdateGeneralNoteResult.Updated -> null
        UpdateGeneralNoteResult.OrderNotFound,
        UpdateGeneralNoteResult.OrderNotEditable,
        UpdateGeneralNoteResult.PersistenceFailure,
        ->
            "Impossibile salvare la nota ordine. Riprova."
    }

    companion object {
        const val DRAFT_ID_ARGUMENT = "draftId"
    }
}

internal fun OrderItem.isCustomizedPizzaRow(): Boolean =
    OrderLineMergePolicy.isCustomizedPizza(
        OrderLineMergeCandidate(
            productId = productId ?: 0L,
            category = categorySnapshot,
            hasAdditions = additions.isNotEmpty(),
            hasRemovals = removals.isNotEmpty(),
            note = note,
            manualUnitPrice = manualUnitPrice,
        ),
    )

private data class QuickAddOperationState(
    val pendingCounts: Map<Long, Int> = emptyMap(),
    val error: String? = null,
) {
    fun start(productId: Long): QuickAddOperationState = copy(
        pendingCounts = pendingCounts + (productId to ((pendingCounts[productId] ?: 0) + 1)),
        error = null,
    )

    fun finish(productId: Long, message: String?): QuickAddOperationState {
        val remaining = (pendingCounts[productId] ?: 1) - 1
        val updatedCounts = if (remaining > 0) {
            pendingCounts + (productId to remaining)
        } else {
            pendingCounts - productId
        }
        return copy(pendingCounts = updatedCounts, error = message ?: error)
    }
}

private data class LineMutationOperationState(
    val pendingCounts: Map<String, Int> = emptyMap(),
    val error: String? = null,
) {
    fun start(itemId: String): LineMutationOperationState = copy(
        pendingCounts = pendingCounts + (itemId to ((pendingCounts[itemId] ?: 0) + 1)),
        error = null,
    )

    fun finish(itemId: String, message: String?): LineMutationOperationState {
        val remaining = (pendingCounts[itemId] ?: 1) - 1
        val updatedCounts = if (remaining > 0) {
            pendingCounts + (itemId to remaining)
        } else {
            pendingCounts - itemId
        }
        return copy(pendingCounts = updatedCounts, error = message ?: error)
    }
}

/**
 * [editor] null means follow the persisted Room value (clean).
 * Non-null means the user has local unsaved text that Flow must not overwrite.
 */
private data class GeneralNoteLocalState(
    val editor: String? = null,
    val saveInProgress: Boolean = false,
    val error: String? = null,
)

private data class CatalogFilterState(
    val mainFilter: OrderCatalogFilter,
    val searchFilter: OrderCatalogFilter,
)

private data class WorkspaceObservation(
    val quickAdd: QuickAddOperationState,
    val lineMutation: LineMutationOperationState,
    val generalNote: GeneralNoteLocalState,
    val mode: NewOrderWorkspaceMode,
    val noteOverlayOpen: Boolean,
)
