package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.ProductWithIngredients

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getWithIngredients(productId: Long): ProductWithIngredients?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(product: ProductEntity): Int

    @Query(
        """
        UPDATE products
        SET active = :active, updatedAt = :updatedAt
        WHERE id = :productId
        """,
    )
    suspend fun updateActive(productId: Long, active: Boolean, updatedAt: Long): Int
}
