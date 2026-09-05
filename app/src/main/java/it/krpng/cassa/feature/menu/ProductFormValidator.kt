package it.krpng.cassa.feature.menu

import it.krpng.cassa.core.money.Money

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

    internal fun parsePrice(input: String): Money? {
        val value = input.trim()
        if (!PRICE_PATTERN.matches(value)) return null

        val separatorIndex = value.indexOfFirst { character ->
            character == ',' || character == '.'
        }
        val wholePart = if (separatorIndex == -1) value else value.substring(0, separatorIndex)
        val decimalPart = if (separatorIndex == -1) "" else value.substring(separatorIndex + 1)

        return try {
            val wholeCents = Math.multiplyExact(wholePart.toLong(), CENTS_PER_EURO)
            val decimalCents = when (decimalPart.length) {
                0 -> 0L
                1 -> decimalPart.toLong() * 10L
                else -> decimalPart.toLong()
            }
            Money.ofCents(Math.addExact(wholeCents, decimalCents))
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private val PRICE_PATTERN = Regex("^[0-9]+([,.][0-9]{1,2})?$")
    private const val CENTS_PER_EURO = 100L
}
