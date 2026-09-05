package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory

data class MenuImportPlan(
    val productsToCreate: List<ProductImportCreate>,
    val productsToUpdate: List<ProductImportUpdate>,
    val unchangedProducts: List<UnchangedProductImport>,
    val additionsToCreate: List<AdditionImportCreate>,
    val additionsToUpdate: List<AdditionImportUpdate>,
    val unchangedAdditions: List<UnchangedAdditionImport>,
)

data class ProductImportCreate(
    val sourceSheet: String?,
    val sourceRow: Int,
    val values: ProductImportValues,
)

data class ProductImportUpdate(
    val existingProductId: Long,
    val sourceSheet: String?,
    val sourceRow: Int,
    val values: ProductImportValues,
)

data class UnchangedProductImport(
    val existingProductId: Long,
    val sourceSheet: String?,
    val sourceRow: Int,
)

data class ProductImportValues(
    val name: String,
    val normalizedName: String,
    val price: Money,
    val category: ProductCategory,
    val printedName: ImportFieldUpdate<String?>,
    val ingredients: ImportFieldUpdate<List<ValidatedIngredientImport>>,
)

data class AdditionImportCreate(
    val sourceSheet: String?,
    val sourceRow: Int,
    val values: AdditionImportValues,
)

data class AdditionImportUpdate(
    val existingAdditionId: Long,
    val sourceSheet: String?,
    val sourceRow: Int,
    val values: AdditionImportValues,
)

data class UnchangedAdditionImport(
    val existingAdditionId: Long,
    val sourceSheet: String?,
    val sourceRow: Int,
)

data class AdditionImportValues(
    val name: String,
    val normalizedName: String,
    val price: Money,
    val printedName: ImportFieldUpdate<String?>,
)

sealed interface ImportFieldUpdate<out T> {
    data object PreserveExisting : ImportFieldUpdate<Nothing>

    data class Replace<T>(
        val value: T,
    ) : ImportFieldUpdate<T>
}

class MenuImportPlanner {
    fun createPlan(
        validatedImport: ValidatedMenuImport,
        existingProducts: List<Product>,
        existingAdditions: List<Addition>,
    ): MenuImportPlan {
        val productsByNormalizedName = existingProducts.associateBy(Product::normalizedName)
        val additionsByNormalizedName = existingAdditions.associateBy(Addition::normalizedName)

        val productsToCreate = mutableListOf<ProductImportCreate>()
        val productsToUpdate = mutableListOf<ProductImportUpdate>()
        val unchangedProducts = mutableListOf<UnchangedProductImport>()
        validatedImport.products.forEach { imported ->
            val existing = productsByNormalizedName[imported.normalizedName]
            if (existing == null) {
                productsToCreate += ProductImportCreate(
                    sourceSheet = imported.sourceSheet,
                    sourceRow = imported.sourceRow,
                    values = imported.toProductValues(isExisting = false),
                )
            } else {
                val values = imported.toProductValues(isExisting = true)
                if (existing.matches(values)) {
                    unchangedProducts += UnchangedProductImport(
                        existingProductId = existing.id,
                        sourceSheet = imported.sourceSheet,
                        sourceRow = imported.sourceRow,
                    )
                } else {
                    productsToUpdate += ProductImportUpdate(
                        existingProductId = existing.id,
                        sourceSheet = imported.sourceSheet,
                        sourceRow = imported.sourceRow,
                        values = values,
                    )
                }
            }
        }

        val additionsToCreate = mutableListOf<AdditionImportCreate>()
        val additionsToUpdate = mutableListOf<AdditionImportUpdate>()
        val unchangedAdditions = mutableListOf<UnchangedAdditionImport>()
        validatedImport.additions.forEach { imported ->
            val existing = additionsByNormalizedName[imported.normalizedName]
            if (existing == null) {
                additionsToCreate += AdditionImportCreate(
                    sourceSheet = imported.sourceSheet,
                    sourceRow = imported.sourceRow,
                    values = imported.toAdditionValues(isExisting = false),
                )
            } else {
                val values = imported.toAdditionValues(isExisting = true)
                if (existing.matches(values)) {
                    unchangedAdditions += UnchangedAdditionImport(
                        existingAdditionId = existing.id,
                        sourceSheet = imported.sourceSheet,
                        sourceRow = imported.sourceRow,
                    )
                } else {
                    additionsToUpdate += AdditionImportUpdate(
                        existingAdditionId = existing.id,
                        sourceSheet = imported.sourceSheet,
                        sourceRow = imported.sourceRow,
                        values = values,
                    )
                }
            }
        }

        return MenuImportPlan(
            productsToCreate = productsToCreate,
            productsToUpdate = productsToUpdate,
            unchangedProducts = unchangedProducts,
            additionsToCreate = additionsToCreate,
            additionsToUpdate = additionsToUpdate,
            unchangedAdditions = unchangedAdditions,
        )
    }

    private fun ValidatedProductImport.toProductValues(isExisting: Boolean): ProductImportValues =
        ProductImportValues(
            name = name,
            normalizedName = normalizedName,
            price = price,
            category = category,
            printedName = printedName.toPrintedNameUpdate(isExisting),
            ingredients = ingredients.toIngredientsUpdate(isExisting),
        )

    private fun ValidatedAdditionImport.toAdditionValues(isExisting: Boolean): AdditionImportValues =
        AdditionImportValues(
            name = name,
            normalizedName = normalizedName,
            price = price,
            printedName = printedName.toPrintedNameUpdate(isExisting),
        )

    private fun ValidatedOptionalField<String>.toPrintedNameUpdate(
        isExisting: Boolean,
    ): ImportFieldUpdate<String?> = when (this) {
        ValidatedOptionalField.ColumnAbsent -> if (isExisting) {
            ImportFieldUpdate.PreserveExisting
        } else {
            ImportFieldUpdate.Replace(null)
        }
        is ValidatedOptionalField.ColumnPresent -> ImportFieldUpdate.Replace(value)
    }

    private fun ValidatedOptionalField<List<ValidatedIngredientImport>>.toIngredientsUpdate(
        isExisting: Boolean,
    ): ImportFieldUpdate<List<ValidatedIngredientImport>> = when (this) {
        ValidatedOptionalField.ColumnAbsent -> if (isExisting) {
            ImportFieldUpdate.PreserveExisting
        } else {
            ImportFieldUpdate.Replace(emptyList())
        }
        is ValidatedOptionalField.ColumnPresent -> ImportFieldUpdate.Replace(value.orEmpty())
    }

    private fun Product.matches(values: ProductImportValues): Boolean =
        name == values.name &&
            normalizedName == values.normalizedName &&
            price == values.price &&
            category == values.category &&
            values.printedName.resolve(printedName) == printedName &&
            values.ingredients.resolve(validatedIngredients()) == validatedIngredients()

    private fun Addition.matches(values: AdditionImportValues): Boolean =
        name == values.name &&
            normalizedName == values.normalizedName &&
            price == values.price &&
            values.printedName.resolve(printedName) == printedName

    private fun Product.validatedIngredients(): List<ValidatedIngredientImport> =
        ingredients.sortedBy { it.displayOrder }.map { productIngredient ->
            ValidatedIngredientImport(
                name = productIngredient.ingredient.name,
                normalizedName = productIngredient.ingredient.normalizedName,
            )
        }

    private fun <T> ImportFieldUpdate<T>.resolve(existingValue: T): T = when (this) {
        ImportFieldUpdate.PreserveExisting -> existingValue
        is ImportFieldUpdate.Replace -> value
    }
}
