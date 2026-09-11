package it.krpng.cassa.domain.usecase

import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.RemoveOrderItemResult
import javax.inject.Inject

class RemoveOrderItem @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        orderId: String,
        orderItemId: String,
    ): RemoveOrderItemResult = orderRepository.removeOrderItem(
        orderId = orderId,
        orderItemId = orderItemId,
    )
}
