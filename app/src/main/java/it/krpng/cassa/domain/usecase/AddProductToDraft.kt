package it.krpng.cassa.domain.usecase

import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import javax.inject.Inject

class AddProductToDraft @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        productId: Long,
    ): QuickAddStandardResult = orderRepository.quickAddStandard(draftId, productId)
}
