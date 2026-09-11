package it.krpng.cassa.domain.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    suspend fun getById(orderId: String): Order?

    fun observeById(orderId: String): Flow<Order?>

    fun observeActiveDraft(): Flow<Order?>

    suspend fun getActiveDraft(): Order?

    suspend fun createDraft(): CreateDraftResult

    suspend fun deleteDraft(orderId: String): DeleteDraftResult

    suspend fun replaceDraft(orderId: String): ReplaceDraftResult

    suspend fun quickAddStandard(orderId: String, productId: Long): QuickAddStandardResult

    suspend fun updateOrderItem(
        orderId: String,
        orderItemId: String,
        quantity: Int,
        note: String?,
        manualUnitPrice: Money?,
        selectedAdditionIds: List<Long>? = null,
        selectedRemovalIngredientIds: List<Long>? = null,
        customizationQuantityIntent: CustomizationQuantityIntent =
            CustomizationQuantityIntent.KEEP_CURRENT_SCOPE,
    ): UpdateOrderItemResult

    suspend fun splitStandardPizzaItem(
        orderId: String,
        orderItemId: String,
        note: String?,
        manualUnitPrice: Money?,
        selectedAdditionIds: List<Long> = emptyList(),
        selectedRemovalIngredientIds: List<Long> = emptyList(),
    ): SplitStandardPizzaItemResult
}

enum class CustomizationQuantityIntent {
    KEEP_CURRENT_SCOPE,
    APPLY_TO_ALL_UNITS_CONFIRMED,
}

sealed interface UpdateOrderItemResult {
    data object Updated : UpdateOrderItemResult

    data object OrderNotFound : UpdateOrderItemResult

    data object OrderNotEditable : UpdateOrderItemResult

    data object ItemNotFound : UpdateOrderItemResult

    data object ItemNotPizza : UpdateOrderItemResult

    data object AdditionUnavailable : UpdateOrderItemResult

    data object IngredientNotRemovable : UpdateOrderItemResult

    data object AmbiguousPizzaQuantity : UpdateOrderItemResult

    data object InvalidQuantity : UpdateOrderItemResult

    data object AmountOverflow : UpdateOrderItemResult

    data object PersistenceFailure : UpdateOrderItemResult
}

sealed interface SplitStandardPizzaItemResult {
    data class Split(
        val sourceOrderItemId: String,
        val sourceQuantity: Int,
        val newOrderItemId: String,
        val newCreatedSequence: Int,
    ) : SplitStandardPizzaItemResult

    data object OrderNotFound : SplitStandardPizzaItemResult

    data object OrderNotEditable : SplitStandardPizzaItemResult

    data object ItemNotFound : SplitStandardPizzaItemResult

    data object ItemNotPizza : SplitStandardPizzaItemResult

    data object ItemNotStandard : SplitStandardPizzaItemResult

    data object ItemNotAggregated : SplitStandardPizzaItemResult

    data object CustomizationRequired : SplitStandardPizzaItemResult

    data object AdditionUnavailable : SplitStandardPizzaItemResult

    data object IngredientNotRemovable : SplitStandardPizzaItemResult

    data object AmountOverflow : SplitStandardPizzaItemResult

    data object PersistenceFailure : SplitStandardPizzaItemResult
}

sealed interface QuickAddStandardResult {
    data class Added(val orderItemId: String) : QuickAddStandardResult

    data class Merged(
        val orderItemId: String,
        val quantity: Int,
    ) : QuickAddStandardResult

    data object OrderNotFound : QuickAddStandardResult

    data object OrderNotEditable : QuickAddStandardResult

    data object ProductUnavailable : QuickAddStandardResult

    data object LimitReached : QuickAddStandardResult

    data object PersistenceFailure : QuickAddStandardResult
}

sealed interface CreateDraftResult {
    data class Created(val draft: Order) : CreateDraftResult

    data object AlreadyExists : CreateDraftResult
}

sealed interface DeleteDraftResult {
    data object Deleted : DeleteDraftResult

    data object NotFoundOrNotDraft : DeleteDraftResult
}

sealed interface ReplaceDraftResult {
    data class Created(val draft: Order) : ReplaceDraftResult

    data object OriginalNotFoundOrNotDraft : ReplaceDraftResult

    data object Conflict : ReplaceDraftResult
}
