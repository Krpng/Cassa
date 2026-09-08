package it.krpng.cassa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.search.ProductSearchEngine
import it.krpng.cassa.domain.usecase.AddProductToDraft
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
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

data class OrderCatalogItem(
    val productId: Long,
    val name: String,
    val price: Money,
    val matchedIngredient: String? = null,
)

sealed interface NewOrderUiState {
    data object Loading : NewOrderUiState

    data class Ready(
        val draftId: String,
        val isDraftEmpty: Boolean,
        val searchQuery: String,
        val selectedFilter: OrderCatalogFilter,
        val catalogItems: List<OrderCatalogItem>,
        val quickAddInProgressProductIds: Set<Long> = emptySet(),
        val quickAddError: String? = null,
    ) : NewOrderUiState

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
) : ViewModel() {
    private val draftId: String = savedStateHandle.get<String>(DRAFT_ID_ARGUMENT).orEmpty()
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(OrderCatalogFilter.ALL)
    private val quickAddOperation = MutableStateFlow(QuickAddOperationState())

    private val _uiState = MutableStateFlow<NewOrderUiState>(NewOrderUiState.Loading)
    val uiState: StateFlow<NewOrderUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeOrderAndCatalog()
    }

    fun retry() {
        observeOrderAndCatalog()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: OrderCatalogFilter) {
        selectedFilter.value = filter
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
                    selectedFilter,
                    quickAddOperation,
                ) { order, products, query, filter, operation ->
                    when {
                        order == null -> NewOrderUiState.NotFound
                        order.status != OrderStatus.DRAFT -> NewOrderUiState.NotEditable
                        else -> NewOrderUiState.Ready(
                            draftId = order.id,
                            isDraftEmpty = order.items.isEmpty(),
                            searchQuery = query,
                            selectedFilter = filter,
                            catalogItems = products.toCatalogItems(query, filter),
                            quickAddInProgressProductIds = operation.pendingCounts.keys,
                            quickAddError = operation.error,
                        )
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

    companion object {
        const val DRAFT_ID_ARGUMENT = "draftId"
    }
}

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
