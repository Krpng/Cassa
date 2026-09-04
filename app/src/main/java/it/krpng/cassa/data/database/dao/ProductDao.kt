package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import it.krpng.cassa.data.database.relation.ProductWithIngredients

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getWithIngredients(productId: Long): ProductWithIngredients?
}
