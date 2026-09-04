package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query(
        """
        SELECT * FROM ingredients
        WHERE active = 1
        ORDER BY normalizedName ASC, id ASC
        """,
    )
    fun observeActive(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :ingredientId LIMIT 1")
    suspend fun getById(ingredientId: Long): IngredientEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: IngredientEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(ingredient: IngredientEntity): Int

    @Query("UPDATE ingredients SET active = :active WHERE id = :ingredientId")
    suspend fun updateActive(ingredientId: Long, active: Boolean): Int

    @Query("DELETE FROM product_ingredients WHERE productId = :productId")
    suspend fun deleteProductIngredients(productId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductIngredients(ingredients: List<ProductIngredientEntity>)

    @Transaction
    suspend fun replaceProductIngredients(
        productId: Long,
        ingredients: List<ProductIngredientEntity>,
    ) {
        deleteProductIngredients(productId)
        if (ingredients.isNotEmpty()) {
            insertProductIngredients(ingredients)
        }
    }
}
