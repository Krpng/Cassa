package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.domain.model.ProductCategory

data class ValidatedMenuImport(
    val products: List<ValidatedProductImport>,
    val additions: List<ValidatedAdditionImport>,
)

data class ValidatedProductImport(
    val sourceSheet: String?,
    val sourceRow: Int,
    val name: String,
    val normalizedName: String,
    val price: Money,
    val category: ProductCategory,
    val printedName: ValidatedOptionalField<String>,
    val ingredients: ValidatedOptionalField<List<ValidatedIngredientImport>>,
)

data class ValidatedAdditionImport(
    val sourceSheet: String?,
    val sourceRow: Int,
    val name: String,
    val normalizedName: String,
    val price: Money,
    val printedName: ValidatedOptionalField<String>,
)

data class ValidatedIngredientImport(
    val name: String,
    val normalizedName: String,
)

sealed interface ValidatedOptionalField<out T> {
    data object ColumnAbsent : ValidatedOptionalField<Nothing>

    data class ColumnPresent<T>(
        val value: T?,
    ) : ValidatedOptionalField<T>
}

data class MenuImportValidationResult(
    val data: ValidatedMenuImport,
    val errors: List<MenuImportValidationError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class MenuImportValidationError(
    val sourceSheet: String?,
    val sourceRow: Int,
    val field: MenuImportField,
    val rawValue: String?,
    val code: MenuImportValidationErrorCode,
)

enum class MenuImportField {
    PRODUCT_NAME,
    TAKEAWAY_PRICE,
    CATEGORY,
    ADDITION_NAME,
    ADDITION_PRICE,
}

enum class MenuImportValidationErrorCode {
    REQUIRED_VALUE_MISSING,
    INVALID_PRICE,
    NEGATIVE_PRICE,
    TOO_MANY_DECIMALS,
    PRICE_OVERFLOW,
    UNSUPPORTED_CELL_TYPE,
    UNKNOWN_CATEGORY,
    DUPLICATE_NORMALIZED_NAME,
}

class MenuImportValidator {
    fun validate(
        productRows: List<RawProductRow>,
        additionRows: List<RawAdditionRow>,
    ): MenuImportValidationResult {
        val errors = mutableListOf<MenuImportValidationError>()
        val duplicateProductNames = productRows.duplicateNormalizedNames { it.productName }
        val duplicateAdditionNames = additionRows.duplicateNormalizedNames { it.additionName }

        val products = productRows.mapNotNull { row ->
            validateProduct(row, duplicateProductNames, errors)
        }
        val additions = additionRows.mapNotNull { row ->
            validateAddition(row, duplicateAdditionNames, errors)
        }

        return MenuImportValidationResult(
            data = ValidatedMenuImport(products = products, additions = additions),
            errors = errors,
        )
    }

    private fun validateProduct(
        row: RawProductRow,
        duplicateNames: Set<String>,
        allErrors: MutableList<MenuImportValidationError>,
    ): ValidatedProductImport? {
        val rowErrors = mutableListOf<MenuImportValidationError>()
        val name = requiredText(
            cell = row.productName,
            sheetName = row.sheetName,
            rowNumber = row.rowNumber,
            field = MenuImportField.PRODUCT_NAME,
            errors = rowErrors,
        )
        val normalizedName = name?.let(TextNormalizer::normalize)
        if (normalizedName != null && normalizedName in duplicateNames) {
            rowErrors += row.error(
                field = MenuImportField.PRODUCT_NAME,
                cell = row.productName,
                code = MenuImportValidationErrorCode.DUPLICATE_NORMALIZED_NAME,
            )
        }

        val price = validatedPrice(
            cell = row.takeawayPrice,
            sheetName = row.sheetName,
            rowNumber = row.rowNumber,
            field = MenuImportField.TAKEAWAY_PRICE,
            errors = rowErrors,
        )
        val category = validatedCategory(row, rowErrors)
        allErrors += rowErrors

        if (rowErrors.isNotEmpty() || name == null || normalizedName == null ||
            price == null || category == null
        ) {
            return null
        }

        return ValidatedProductImport(
            sourceSheet = row.sheetName,
            sourceRow = row.rowNumber,
            name = name,
            normalizedName = normalizedName,
            price = price,
            category = category,
            printedName = row.printedName.toValidatedOptionalText(),
            ingredients = row.ingredients.toValidatedIngredients(),
        )
    }

    private fun validateAddition(
        row: RawAdditionRow,
        duplicateNames: Set<String>,
        allErrors: MutableList<MenuImportValidationError>,
    ): ValidatedAdditionImport? {
        val rowErrors = mutableListOf<MenuImportValidationError>()
        val name = requiredText(
            cell = row.additionName,
            sheetName = row.sheetName,
            rowNumber = row.rowNumber,
            field = MenuImportField.ADDITION_NAME,
            errors = rowErrors,
        )
        val normalizedName = name?.let(TextNormalizer::normalize)
        if (normalizedName != null && normalizedName in duplicateNames) {
            rowErrors += row.error(
                field = MenuImportField.ADDITION_NAME,
                cell = row.additionName,
                code = MenuImportValidationErrorCode.DUPLICATE_NORMALIZED_NAME,
            )
        }

        val price = validatedPrice(
            cell = row.price,
            sheetName = row.sheetName,
            rowNumber = row.rowNumber,
            field = MenuImportField.ADDITION_PRICE,
            errors = rowErrors,
        )
        allErrors += rowErrors

        if (rowErrors.isNotEmpty() || name == null || normalizedName == null || price == null) {
            return null
        }

        return ValidatedAdditionImport(
            sourceSheet = row.sheetName,
            sourceRow = row.rowNumber,
            name = name,
            normalizedName = normalizedName,
            price = price,
            printedName = row.printedName.toValidatedOptionalText(),
        )
    }

    private fun validatedCategory(
        row: RawProductRow,
        errors: MutableList<MenuImportValidationError>,
    ): ProductCategory? {
        val displayValue = row.category.displayText()
        if (displayValue.isEmpty()) {
            errors += row.error(
                field = MenuImportField.CATEGORY,
                cell = row.category,
                code = MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING,
            )
            return null
        }

        return when (TextNormalizer.normalize(displayValue)) {
            CATEGORY_PIZZAS -> ProductCategory.PIZZA
            CATEGORY_FRIED -> ProductCategory.FRITTURA
            CATEGORY_DRINKS -> ProductCategory.BIBITA
            else -> {
                errors += row.error(
                    field = MenuImportField.CATEGORY,
                    cell = row.category,
                    code = MenuImportValidationErrorCode.UNKNOWN_CATEGORY,
                )
                null
            }
        }
    }

    private fun requiredText(
        cell: RawOdsCell?,
        sheetName: String?,
        rowNumber: Int,
        field: MenuImportField,
        errors: MutableList<MenuImportValidationError>,
    ): String? {
        val value = cell.displayText()
        if (value.isEmpty()) {
            errors += MenuImportValidationError(
                sourceSheet = sheetName,
                sourceRow = rowNumber,
                field = field,
                rawValue = cell.errorValue(),
                code = MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING,
            )
            return null
        }
        return value
    }

    private fun validatedPrice(
        cell: RawOdsCell?,
        sheetName: String?,
        rowNumber: Int,
        field: MenuImportField,
        errors: MutableList<MenuImportValidationError>,
    ): Money? = when (val result = OdsPriceParser.parse(cell)) {
        is OdsPriceParseResult.Success -> result.money
        is OdsPriceParseResult.Failure -> {
            errors += MenuImportValidationError(
                sourceSheet = sheetName,
                sourceRow = rowNumber,
                field = field,
                rawValue = cell.errorValue(),
                code = result.error.toValidationCode(),
            )
            null
        }
    }

    private fun RawOptionalOdsCell.toValidatedOptionalText(): ValidatedOptionalField<String> =
        when (this) {
            RawOptionalOdsCell.ColumnAbsent -> ValidatedOptionalField.ColumnAbsent
            is RawOptionalOdsCell.ColumnPresent ->
                ValidatedOptionalField.ColumnPresent(cell.displayText().takeIf(String::isNotEmpty))
        }

    private fun RawOptionalOdsCell.toValidatedIngredients():
        ValidatedOptionalField<List<ValidatedIngredientImport>> = when (this) {
            RawOptionalOdsCell.ColumnAbsent -> ValidatedOptionalField.ColumnAbsent
            is RawOptionalOdsCell.ColumnPresent -> ValidatedOptionalField.ColumnPresent(
                value = cell.displayText()
                    .split(',')
                    .map { ingredient -> ingredient.normalizedDisplayText() }
                .filter(String::isNotEmpty)
                .distinctBy(TextNormalizer::normalize)
                .map { ingredient ->
                    ValidatedIngredientImport(
                        name = ingredient,
                        normalizedName = TextNormalizer.normalize(ingredient),
                    )
                },
        )
    }

    private fun <T> List<T>.duplicateNormalizedNames(cell: (T) -> RawOdsCell?): Set<String> =
        mapNotNull { item ->
            cell(item).displayText()
                .takeIf(String::isNotEmpty)
                ?.let(TextNormalizer::normalize)
        }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    private fun RawProductRow.error(
        field: MenuImportField,
        cell: RawOdsCell?,
        code: MenuImportValidationErrorCode,
    ): MenuImportValidationError = MenuImportValidationError(
        sourceSheet = sheetName,
        sourceRow = rowNumber,
        field = field,
        rawValue = cell.errorValue(),
        code = code,
    )

    private fun RawAdditionRow.error(
        field: MenuImportField,
        cell: RawOdsCell?,
        code: MenuImportValidationErrorCode,
    ): MenuImportValidationError = MenuImportValidationError(
        sourceSheet = sheetName,
        sourceRow = rowNumber,
        field = field,
        rawValue = cell.errorValue(),
        code = code,
    )

    private fun RawOdsCell?.displayText(): String {
        if (this == null || kind == RawOdsCellKind.EMPTY) return ""
        val source = text.takeIf(String::isNotBlank) ?: rawValue.orEmpty()
        return source.normalizedDisplayText()
    }

    private fun RawOdsCell?.errorValue(): String? {
        if (this == null) return null
        return text.takeIf(String::isNotEmpty) ?: rawValue
    }

    private fun String.normalizedDisplayText(): String =
        DISPLAY_WHITESPACE.replace(this, " ").trim()

    private fun OdsPriceParseError.toValidationCode(): MenuImportValidationErrorCode = when (this) {
        OdsPriceParseError.MISSING_VALUE -> MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING
        OdsPriceParseError.NON_NUMERIC -> MenuImportValidationErrorCode.INVALID_PRICE
        OdsPriceParseError.NEGATIVE -> MenuImportValidationErrorCode.NEGATIVE_PRICE
        OdsPriceParseError.TOO_MANY_DECIMALS ->
            MenuImportValidationErrorCode.TOO_MANY_DECIMALS
        OdsPriceParseError.OVERFLOW -> MenuImportValidationErrorCode.PRICE_OVERFLOW
        OdsPriceParseError.UNSUPPORTED_CELL_TYPE ->
            MenuImportValidationErrorCode.UNSUPPORTED_CELL_TYPE
    }

    private companion object {
        const val CATEGORY_PIZZAS = "pizze"
        const val CATEGORY_FRIED = "frittura"
        const val CATEGORY_DRINKS = "bibite"
        val DISPLAY_WHITESPACE = Regex("[\\p{Z}\\s]+")
    }
}
