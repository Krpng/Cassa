package it.krpng.cassa.domain.usecase

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.SplitStandardPizzaItemResult
import javax.inject.Inject

class SplitStandardPizzaItem @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        orderId: String,
        orderItemId: String,
        note: String?,
        manualUnitPrice: Money?,
        selectedAdditionIds: List<Long> = emptyList(),
        selectedRemovalIngredientIds: List<Long> = emptyList(),
    ): SplitStandardPizzaItemResult = orderRepository.splitStandardPizzaItem(
        orderId = orderId,
        orderItemId = orderItemId,
        note = note,
        manualUnitPrice = manualUnitPrice,
        selectedAdditionIds = selectedAdditionIds,
        selectedRemovalIngredientIds = selectedRemovalIngredientIds,
    )
}
