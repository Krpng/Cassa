package it.krpng.cassa.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.ProductRepository
import it.krpng.cassa.domain.search.ProductSearchEngine
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class MenuSection(
    val label: String,
    internal val productCategory: ProductCategory?,
) {
    PIZZAS("PIZZE", ProductCategory.PIZZA),
    FRIED("FRITTURA", ProductCategory.FRITTURA),
    DRINKS("BIBITE", ProductCategory.BIBITA),
    ADDITIONS("AGGIUNTE", null),
}

data class MenuListItem(
    val key: String,
    val productId: Long?,
    val name: String,
    val price: Money,
    val active: Boolean,
    val matchedIngredient: String? = null,
)

data class MenuUiState(
    val isLoading: Boolean = true,
    val selectedSection: MenuSection = MenuSection.PIZZAS,
    val searchQuery: String = "",
    val items: List<MenuListItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    productRepository: ProductRepository,
    additionRepository: AdditionRepository,
) : ViewModel() {
    private val selectedSection = MutableStateFlow(MenuSection.PIZZAS)
    private val searchQuery = MutableStateFlow("")

    val uiState = combine(
        productRepository.observeAll(),
        additionRepository.observeAll(),
        selectedSection,
        searchQuery,
    ) { products, additions, section, query ->
        MenuUiState(
            isLoading = false,
            selectedSection = section,
            searchQuery = query,
            items = when (section) {
                MenuSection.ADDITIONS -> additions.toMenuItems(query)
                else -> products.toMenuItems(section, query)
            },
        )
    }.catch {
        emit(
            MenuUiState(
                isLoading = false,
                selectedSection = selectedSection.value,
                searchQuery = searchQuery.value,
                errorMessage = "Impossibile caricare il menu.",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MenuUiState(),
    )

    fun selectSection(section: MenuSection) {
        selectedSection.value = section
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun List<Product>.toMenuItems(
        section: MenuSection,
        query: String,
    ): List<MenuListItem> {
        val productsInSection = filter { it.category == section.productCategory }
        if (TextNormalizer.normalize(query).isEmpty()) {
            return productsInSection.map { product -> product.toMenuItem() }
        }

        return ProductSearchEngine.search(productsInSection, query).map { result ->
            result.product.toMenuItem(matchedIngredient = result.matchedIngredient)
        }
    }

    private fun List<Addition>.toMenuItems(query: String): List<MenuListItem> {
        val normalizedQuery = TextNormalizer.normalize(query)
        return asSequence()
            .filter { addition ->
                normalizedQuery.isEmpty() || addition.normalizedName.contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<Addition>(
                    { addition ->
                        if (normalizedQuery.isEmpty() || addition.normalizedName.startsWith(normalizedQuery)) {
                            0
                        } else {
                            1
                        }
                    },
                    Addition::normalizedName,
                    Addition::id,
                ),
            )
            .map { addition -> addition.toMenuItem() }
            .toList()
    }

    private fun Product.toMenuItem(matchedIngredient: String? = null): MenuListItem =
        MenuListItem(
            key = "product-$id",
            productId = id,
            name = name,
            price = price,
            active = active,
            matchedIngredient = matchedIngredient,
        )

    private fun Addition.toMenuItem(): MenuListItem = MenuListItem(
        key = "addition-$id",
        productId = null,
        name = name,
        price = price,
        active = active,
    )
}
