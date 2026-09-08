package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import javax.inject.Inject

class UpdateOrderItem @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        orderId: String,
        orderItemId: String,
        quantity: Int,
        note: String?,
        manualUnitPrice: Money?,
    ): UpdateOrderItemResult = orderRepository.updateOrderItem(
        orderId = orderId,
        orderItemId = orderItemId,
        quantity = quantity,
        note = note,
        manualUnitPrice = manualUnitPrice,
    )
}
