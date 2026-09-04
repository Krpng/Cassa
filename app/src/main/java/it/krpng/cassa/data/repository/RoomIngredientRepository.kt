package it.krpng.cassa.data.repository

import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.data.database.dao.IngredientDao
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.ProductIngredient
import it.krpng.cassa.domain.repository.IngredientRepository

class RoomIngredientRepository(
    private val ingredientDao: IngredientDao,
) : IngredientRepository {
    override suspend fun getById(ingredientId: Long): Ingredient? =
        ingredientDao.getById(ingredientId)?.toDomain()

    override suspend fun create(ingredient: Ingredient): Long =
        ingredientDao.insert(ingredient.toWritableEntity(ingredientId = 0))

    override suspend fun update(ingredient: Ingredient): Boolean =
        ingredientDao.update(ingredient.toWritableEntity(ingredientId = ingredient.id)) == 1

    override suspend fun activate(ingredientId: Long): Boolean =
        ingredientDao.updateActive(ingredientId = ingredientId, active = true) == 1

    override suspend fun deactivate(ingredientId: Long): Boolean =
        ingredientDao.updateActive(ingredientId = ingredientId, active = false) == 1

    override suspend fun replaceProductIngredients(
        productId: Long,
        ingredients: List<ProductIngredient>,
    ) {
        ingredientDao.replaceProductIngredients(
            productId = productId,
            ingredients = ingredients.map { productIngredient ->
                ProductIngredientEntity(
                    productId = productId,
                    ingredientId = productIngredient.ingredient.id,
                    displayOrder = productIngredient.displayOrder,
                )
            },
        )
    }

    private fun Ingredient.toWritableEntity(ingredientId: Long): IngredientEntity =
        toEntity().copy(
            id = ingredientId,
            normalizedName = TextNormalizer.normalize(name),
        )
}
