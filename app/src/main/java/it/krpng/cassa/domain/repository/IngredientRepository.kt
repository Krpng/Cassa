package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.ProductIngredient

interface IngredientRepository {
    suspend fun getById(ingredientId: Long): Ingredient?

    suspend fun create(ingredient: Ingredient): Long

    suspend fun update(ingredient: Ingredient): Boolean

    suspend fun activate(ingredientId: Long): Boolean

    suspend fun deactivate(ingredientId: Long): Boolean

    suspend fun replaceProductIngredients(
        productId: Long,
        ingredients: List<ProductIngredient>,
    )
}
