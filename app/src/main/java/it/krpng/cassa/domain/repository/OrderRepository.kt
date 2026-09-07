package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Order
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    suspend fun getById(orderId: String): Order?

    fun observeActiveDraft(): Flow<Order?>

    suspend fun getActiveDraft(): Order?

    suspend fun createDraft(): CreateDraftResult

    suspend fun deleteDraft(orderId: String): DeleteDraftResult
}

sealed interface CreateDraftResult {
    data class Created(val draft: Order) : CreateDraftResult

    data object AlreadyExists : CreateDraftResult
}

sealed interface DeleteDraftResult {
    data object Deleted : DeleteDraftResult

    data object NotFoundOrNotDraft : DeleteDraftResult
}
