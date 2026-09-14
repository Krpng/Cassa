package it.krpng.cassa.domain.usecase

import it.krpng.cassa.domain.repository.AcceptOrderResult
import it.krpng.cassa.domain.repository.OrderRepository
import javax.inject.Inject

class AcceptOrder @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(orderId: String): AcceptOrderResult =
        orderRepository.acceptOrder(orderId)
}
