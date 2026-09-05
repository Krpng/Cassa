package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money

data class AdditionFormErrors(
    val name: String? = null,
    val price: String? = null,
) {
    val hasErrors: Boolean
        get() = name != null || price != null
}

data class ValidatedAdditionFields(
    val name: String,
    val printedName: String?,
    val price: Money,
)

sealed interface AdditionFormValidationResult {
    data class Valid(
        val fields: ValidatedAdditionFields,
    ) : AdditionFormValidationResult

    data class Invalid(
        val errors: AdditionFormErrors,
    ) : AdditionFormValidationResult
}

object AdditionFormValidator {
    fun validate(
        name: String,
        printedName: String,
        priceInput: String,
    ): AdditionFormValidationResult {
        val trimmedName = name.trim()
        val parsedPrice = ProductFormValidator.parsePrice(priceInput)
        val errors = AdditionFormErrors(
            name = if (trimmedName.isEmpty()) "Inserisci il nome dell'aggiunta." else null,
            price = if (parsedPrice == null) {
                "Inserisci un prezzo valido con massimo due decimali."
            } else {
                null
            },
        )

        if (errors.hasErrors) {
            return AdditionFormValidationResult.Invalid(errors)
        }

        return AdditionFormValidationResult.Valid(
            ValidatedAdditionFields(
                name = trimmedName,
                printedName = printedName.trim().ifEmpty { null },
                price = checkNotNull(parsedPrice),
            ),
        )
    }
}
