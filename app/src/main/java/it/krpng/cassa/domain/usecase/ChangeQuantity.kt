package it.krpng.cassa.domain.usecase

import it.krpng.cassa.domain.repository.ChangeQuantityResult
import it.krpng.cassa.domain.repository.OrderRepository
import javax.inject.Inject

class ChangeQuantity @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        orderId: String,
        orderItemId: String,
        quantity: Int,
    ): ChangeQuantityResult = orderRepository.changeQuantity(
        orderId = orderId,
        orderItemId = orderItemId,
        quantity = quantity,
    )
}
