package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Order

interface OrderRepository {
    suspend fun getById(orderId: String): Order?
}
