package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.relation.ProductWithIngredients
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products ORDER BY normalizedName ASC, id ASC")
    fun observeAllWithIngredients(): Flow<List<ProductWithIngredients>>

    @Transaction
    @Query(
        """
        SELECT * FROM products
        WHERE active = 1
        ORDER BY normalizedName ASC, id ASC
        """,
    )
    fun observeActiveWithIngredients(): Flow<List<ProductWithIngredients>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getWithIngredients(productId: Long): ProductWithIngredients?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(product: ProductEntity): Int

    @Query("DELETE FROM product_ingredients WHERE productId = :productId")
    suspend fun deleteProductIngredients(productId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductIngredients(ingredients: List<ProductIngredientEntity>)

    @Transaction
    suspend fun insertWithIngredients(
        product: ProductEntity,
        ingredients: List<ProductIngredientEntity>,
    ): Long {
        val productId = insert(product)
        replaceProductIngredients(productId, ingredients)
        return productId
    }

    @Transaction
    suspend fun updateWithIngredients(
        product: ProductEntity,
        ingredients: List<ProductIngredientEntity>,
    ): Int {
        val updatedRows = update(product)
        if (updatedRows == 1) {
            replaceProductIngredients(product.id, ingredients)
        }
        return updatedRows
    }

    @Transaction
    suspend fun replaceProductIngredients(
        productId: Long,
        ingredients: List<ProductIngredientEntity>,
    ) {
        deleteProductIngredients(productId)
        if (ingredients.isNotEmpty()) {
            insertProductIngredients(
                ingredients.map { ingredient -> ingredient.copy(productId = productId) },
            )
        }
    }

    @Query(
        """
        UPDATE products
        SET active = :active, updatedAt = :updatedAt
        WHERE id = :productId
        """,
    )
    suspend fun updateActive(productId: Long, active: Boolean, updatedAt: Long): Int
}
