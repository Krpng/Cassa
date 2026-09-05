package it.krpng.cassa.data.ods

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RoomMenuImportCommitter @Inject constructor(
    private val database: CassaDatabase,
    private val clockProvider: ClockProvider,
) : MenuImportCommitter {
    override suspend fun commit(plan: MenuImportPlan) {
        try {
            database.withTransaction {
                val timestamp = clockProvider.now().toEpochMilli()
                applyProducts(plan, timestamp)
                applyAdditions(plan, timestamp)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: MenuImportPlanConflictException) {
            throw error
        } catch (error: SQLiteException) {
            throw MenuImportDatabaseException(error)
        } catch (error: Exception) {
            throw UnexpectedMenuImportException(error)
        }
    }

    private suspend fun applyProducts(plan: MenuImportPlan, timestamp: Long) {
        plan.productsToCreate.forEach { operation ->
            val values = operation.values
            val productId = database.productDao().insert(
                ProductEntity(
                    name = values.name,
                    normalizedName = values.normalizedName,
                    printedName = values.printedName.requiredCreateValue("printedName"),
                    category = values.category,
                    priceCents = values.price.cents,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            replaceIngredients(
                productId = productId,
                ingredients = values.ingredients.requiredCreateValue("ingredients"),
            )
        }

        plan.productsToUpdate.forEach { operation ->
            val existing = database.productDao()
                .getWithIngredients(operation.existingProductId)
                ?.product
                ?: throw MenuImportPlanConflictException(
                    "Il prodotto ${operation.existingProductId} non esiste più.",
                )
            val values = operation.values
            val updatedRows = database.productDao().update(
                existing.copy(
                    name = values.name,
                    normalizedName = values.normalizedName,
                    printedName = values.printedName.resolve(existing.printedName),
                    category = values.category,
                    priceCents = values.price.cents,
                    updatedAt = timestamp,
                ),
            )
            if (updatedRows != 1) {
                throw MenuImportPlanConflictException(
                    "Il prodotto ${operation.existingProductId} non è stato aggiornato.",
                )
            }
            values.ingredients.replaceValueOrNull()?.let { ingredients ->
                replaceIngredients(existing.id, ingredients)
            }
        }
    }

    private suspend fun applyAdditions(plan: MenuImportPlan, timestamp: Long) {
        plan.additionsToCreate.forEach { operation ->
            val values = operation.values
            database.additionDao().insert(
                AdditionEntity(
                    name = values.name,
                    normalizedName = values.normalizedName,
                    printedName = values.printedName.requiredCreateValue("printedName"),
                    priceCents = values.price.cents,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
        }

        plan.additionsToUpdate.forEach { operation ->
            val existing = database.additionDao().getById(operation.existingAdditionId)
                ?: throw MenuImportPlanConflictException(
                    "L'aggiunta ${operation.existingAdditionId} non esiste più.",
                )
            val values = operation.values
            val updatedRows = database.additionDao().update(
                existing.copy(
                    name = values.name,
                    normalizedName = values.normalizedName,
                    printedName = values.printedName.resolve(existing.printedName),
                    priceCents = values.price.cents,
                    updatedAt = timestamp,
                ),
            )
            if (updatedRows != 1) {
                throw MenuImportPlanConflictException(
                    "L'aggiunta ${operation.existingAdditionId} non è stata aggiornata.",
                )
            }
        }
    }

    private suspend fun replaceIngredients(
        productId: Long,
        ingredients: List<ValidatedIngredientImport>,
    ) {
        val links = ingredients.mapIndexed { displayOrder, ingredient ->
            val ingredientId = database.ingredientDao()
                .getByNormalizedName(ingredient.normalizedName)
                ?.id
                ?: database.ingredientDao().insert(
                    IngredientEntity(
                        name = ingredient.name,
                        normalizedName = ingredient.normalizedName,
                    ),
                )
            ProductIngredientEntity(
                productId = productId,
                ingredientId = ingredientId,
                displayOrder = displayOrder,
            )
        }
        database.productDao().replaceProductIngredients(productId, links)
    }

    private fun <T> ImportFieldUpdate<T>.resolve(existing: T): T = when (this) {
        ImportFieldUpdate.PreserveExisting -> existing
        is ImportFieldUpdate.Replace -> value
    }

    private fun <T> ImportFieldUpdate<T>.requiredCreateValue(field: String): T = when (this) {
        ImportFieldUpdate.PreserveExisting -> throw MenuImportPlanConflictException(
            "Il campo $field non può essere preservato per un nuovo record.",
        )
        is ImportFieldUpdate.Replace -> value
    }

    private fun <T> ImportFieldUpdate<T>.replaceValueOrNull(): T? = when (this) {
        ImportFieldUpdate.PreserveExisting -> null
        is ImportFieldUpdate.Replace -> value
    }
}
