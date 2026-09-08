package it.krpng.cassa.feature.order

import it.krpng.cassa.core.money.DecimalMoneyParser
import it.krpng.cassa.core.money.Money

data class OrderItemFormErrors(
    val quantity: String? = null,
    val manualPrice: String? = null,
) {
    val hasErrors: Boolean
        get() = quantity != null || manualPrice != null
}

data class ValidatedOrderItemFields(
    val quantity: Int,
    val note: String?,
    val manualUnitPrice: Money?,
)

sealed interface OrderItemFormValidationResult {
    data class Valid(val fields: ValidatedOrderItemFields) : OrderItemFormValidationResult

    data class Invalid(val errors: OrderItemFormErrors) : OrderItemFormValidationResult
}

object OrderItemFormValidator {
    fun validate(
        quantityInput: String,
        note: String,
        manualPriceInput: String?,
    ): OrderItemFormValidationResult {
        val quantity = quantityInput.trim().toIntOrNull()?.takeIf { it > 0 }
        val manualPrice = manualPriceInput?.let(DecimalMoneyParser::parse)
        val errors = OrderItemFormErrors(
            quantity = if (quantity == null) {
                "Inserisci una quantità valida maggiore di zero."
            } else {
                null
            },
            manualPrice = if (manualPriceInput != null && manualPrice == null) {
                "Inserisci un prezzo valido con massimo due decimali."
            } else {
                null
            },
        )
        if (errors.hasErrors) return OrderItemFormValidationResult.Invalid(errors)

        return OrderItemFormValidationResult.Valid(
            ValidatedOrderItemFields(
                quantity = checkNotNull(quantity),
                note = note.trim().ifEmpty { null },
                manualUnitPrice = manualPrice,
            ),
        )
    }
}
