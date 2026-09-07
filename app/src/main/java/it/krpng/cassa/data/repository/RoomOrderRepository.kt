package it.krpng.cassa.data.repository

import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOrderRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val clockProvider: ClockProvider,
) : OrderRepository {
    override suspend fun getById(orderId: String): Order? =
        orderDao.getFullOrder(orderId)?.toDomain()

    override fun observeActiveDraft(): Flow<Order?> =
        orderDao.observeActiveDraft().map { draft -> draft?.toDomain() }

    override suspend fun getActiveDraft(): Order? =
        orderDao.getActiveDraft()?.toDomain()

    override suspend fun createDraft(): CreateDraftResult {
        val now = clockProvider.now()
        val draft = Order(
            id = UUID.randomUUID().toString(),
            status = OrderStatus.DRAFT,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = now,
            updatedAt = now,
            acceptedAt = null,
            total = Money.ZERO,
            generalNote = null,
            sourceOrderId = null,
            items = emptyList(),
        )
        val insertResult = orderDao.insertDraft(draft.toDraftEntity())

        return if (insertResult == INSERT_CONFLICT) {
            CreateDraftResult.AlreadyExists
        } else {
            CreateDraftResult.Created(draft)
        }
    }

    override suspend fun deleteDraft(orderId: String): DeleteDraftResult =
        if (orderDao.deleteDraft(orderId) == 1) {
            DeleteDraftResult.Deleted
        } else {
            DeleteDraftResult.NotFoundOrNotDraft
        }

    private fun Order.toDraftEntity(): OrderEntity = toDatabaseModel().order

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
