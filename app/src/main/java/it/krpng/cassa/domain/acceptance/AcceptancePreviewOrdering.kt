package it.krpng.cassa.domain.acceptance

import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.ProductCategory

/**
 * FREEZE-B / ACCEPT-001: fixed category order for acceptance preview.
 * Empty categories are omitted; within each category: createdSequence ASC.
 */
data class AcceptancePreviewCategorySection(
    val category: ProductCategory,
    val title: String,
    val items: List<OrderItem>,
)

object AcceptancePreviewOrdering {
    private val CATEGORY_ORDER: List<ProductCategory> = listOf(
        ProductCategory.PIZZA,
        ProductCategory.FRITTURA,
        ProductCategory.BIBITA,
    )

    fun sectionTitle(category: ProductCategory): String =
        when (category) {
            ProductCategory.PIZZA -> "PIZZE"
            ProductCategory.FRITTURA -> "FRITTURA"
            ProductCategory.BIBITA -> "BIBITE"
        }

    fun groupByCategory(items: List<OrderItem>): List<AcceptancePreviewCategorySection> {
        val byCategory = items.groupBy { it.categorySnapshot }
        return CATEGORY_ORDER.mapNotNull { category ->
            val sectionItems = byCategory[category]
                ?.sortedWith(compareBy({ it.createdSequence }, { it.id }))
                .orEmpty()
            if (sectionItems.isEmpty()) {
                null
            } else {
                AcceptancePreviewCategorySection(
                    category = category,
                    title = sectionTitle(category),
                    items = sectionItems,
                )
            }
        }
    }
}
