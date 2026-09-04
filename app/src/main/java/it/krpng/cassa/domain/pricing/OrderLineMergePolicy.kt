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
            existing.isStandard &&
            incoming.isStandard

    private val OrderLineMergeCandidate.isStandard: Boolean
        get() = !hasAdditions &&
            !hasRemovals &&
            note.isNullOrBlank() &&
            manualUnitPrice == null
}
