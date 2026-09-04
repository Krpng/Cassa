package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderWithItems

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getWithItems(orderId: String): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getFullOrder(orderId: String): FullOrder?
}
