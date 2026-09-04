package it.krpng.cassa.domain.search

import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductIngredient

enum class ProductMatchType {
    NAME_STARTS_WITH,
    NAME_CONTAINS,
    INGREDIENT_STARTS_WITH,
    INGREDIENT_CONTAINS,
}

data class ProductSearchResult(
    val product: Product,
    val matchType: ProductMatchType,
    val matchedIngredient: String? = null,
)

object ProductSearchEngine {
    fun search(
        products: List<Product>,
        query: String,
    ): List<ProductSearchResult> {
        val normalizedQuery = TextNormalizer.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        return products
            .asSequence()
            .filter(Product::active)
            .mapNotNull { product -> match(product, normalizedQuery) }
            .sortedWith(RESULT_ORDER)
            .toList()
    }

    private fun match(
        product: Product,
        normalizedQuery: String,
    ): ProductSearchResult? {
        val nameMatch = classify(
            value = product.normalizedName,
            query = normalizedQuery,
            startsWith = ProductMatchType.NAME_STARTS_WITH,
            contains = ProductMatchType.NAME_CONTAINS,
        )
        if (nameMatch != null) {
            return ProductSearchResult(product = product, matchType = nameMatch)
        }

        val ingredientMatch = product.ingredients
            .asSequence()
            .mapNotNull { ingredient -> ingredient.match(normalizedQuery) }
            .minWithOrNull(INGREDIENT_ORDER)
            ?: return null

        return ProductSearchResult(
            product = product,
            matchType = ingredientMatch.matchType,
            matchedIngredient = ingredientMatch.productIngredient.ingredient.name,
        )
    }

    private fun ProductIngredient.match(normalizedQuery: String): IngredientMatch? {
        val matchType = classify(
            value = ingredient.normalizedName,
            query = normalizedQuery,
            startsWith = ProductMatchType.INGREDIENT_STARTS_WITH,
            contains = ProductMatchType.INGREDIENT_CONTAINS,
        ) ?: return null

        return IngredientMatch(productIngredient = this, matchType = matchType)
    }

    private fun classify(
        value: String,
        query: String,
        startsWith: ProductMatchType,
        contains: ProductMatchType,
    ): ProductMatchType? = when {
        value.startsWith(query) -> startsWith
        value.contains(query) -> contains
        else -> null
    }

    private data class IngredientMatch(
        val productIngredient: ProductIngredient,
        val matchType: ProductMatchType,
    )

    private val RESULT_ORDER = compareBy<ProductSearchResult>(
        { it.matchType.rank },
        { it.product.normalizedName },
        { it.product.id },
    )

    private val INGREDIENT_ORDER = compareBy<IngredientMatch>(
        { it.matchType.rank },
        { it.productIngredient.displayOrder },
        { it.productIngredient.ingredient.normalizedName },
        { it.productIngredient.ingredient.id },
    )

    private val ProductMatchType.rank: Int
        get() = when (this) {
            ProductMatchType.NAME_STARTS_WITH -> 0
            ProductMatchType.NAME_CONTAINS -> 1
            ProductMatchType.INGREDIENT_STARTS_WITH -> 2
            ProductMatchType.INGREDIENT_CONTAINS -> 3
        }
}
