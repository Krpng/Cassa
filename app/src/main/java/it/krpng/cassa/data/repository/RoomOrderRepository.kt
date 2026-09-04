package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.repository.OrderRepository

class RoomOrderRepository(
    private val orderDao: OrderDao,
) : OrderRepository {
    override suspend fun getById(orderId: String): Order? =
        orderDao.getFullOrder(orderId)?.toDomain()
}
