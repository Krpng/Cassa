package it.krpng.cassa.domain.usecase

import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.UpdateGeneralNoteResult
import javax.inject.Inject

class UpdateGeneralNote @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        orderId: String,
        generalNote: String?,
    ): UpdateGeneralNoteResult = orderRepository.updateGeneralNote(
        orderId = orderId,
        generalNote = generalNote,
    )
}
