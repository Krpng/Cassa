package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory

data class OrderLineMergeCandidate(
    val productId: Long,
    val category: ProductCategory,
    val hasAdditions: Boolean = false,
    val hasRemovals: Boolean = false,
    val note: String? = null,
    val manualUnitPrice: Money? = null,
)

object OrderLineMergePolicy {
    fun canMerge(
        existing: OrderLineMergeCandidate,
        incoming: OrderLineMergeCandidate,
    ): Boolean =
        existing.productId == incoming.productId &&
            existing.category == incoming.category &&
            !isCustomized(existing) &&
            !isCustomized(incoming)

    /**
     * A line is customized when it has any addition, removal, non-blank note, or manual price
     * (including a manual override of EUR 0.00).
     */
    fun isCustomized(candidate: OrderLineMergeCandidate): Boolean =
        candidate.hasAdditions ||
            candidate.hasRemovals ||
            !candidate.note.isNullOrBlank() ||
            candidate.manualUnitPrice != null

    /**
     * Visual/business highlight for ORD customized pizza rows: pizza category and customized.
     */
    fun isCustomizedPizza(candidate: OrderLineMergeCandidate): Boolean =
        candidate.category == ProductCategory.PIZZA && isCustomized(candidate)
}
