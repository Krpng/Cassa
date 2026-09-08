package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.money.DecimalMoneyParser

data class ProductFormErrors(
    val name: String? = null,
    val price: String? = null,
) {
    val hasErrors: Boolean
        get() = name != null || price != null
}

data class ValidatedProductFields(
    val name: String,
    val printedName: String?,
    val price: Money,
)

sealed interface ProductFormValidationResult {
    data class Valid(
        val fields: ValidatedProductFields,
    ) : ProductFormValidationResult

    data class Invalid(
        val errors: ProductFormErrors,
    ) : ProductFormValidationResult
}

object ProductFormValidator {
    fun validate(
        name: String,
        printedName: String,
        priceInput: String,
    ): ProductFormValidationResult {
        val trimmedName = name.trim()
        val parsedPrice = parsePrice(priceInput)
        val errors = ProductFormErrors(
            name = if (trimmedName.isEmpty()) "Inserisci il nome del prodotto." else null,
            price = if (parsedPrice == null) {
                "Inserisci un prezzo valido con massimo due decimali."
            } else {
                null
            },
        )

        if (errors.hasErrors) {
            return ProductFormValidationResult.Invalid(errors)
        }

        return ProductFormValidationResult.Valid(
            ValidatedProductFields(
                name = trimmedName,
                printedName = printedName.trim().ifEmpty { null },
                price = checkNotNull(parsedPrice),
            ),
        )
    }

    internal fun parsePrice(input: String): Money? = DecimalMoneyParser.parse(input)
}
