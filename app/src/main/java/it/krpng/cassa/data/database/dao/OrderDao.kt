package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getWithItems(orderId: String): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getFullOrder(orderId: String): FullOrder?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun observeFullOrder(orderId: String): Flow<FullOrder?>

    @Transaction
    @Query(
        """
        SELECT * FROM orders
        WHERE status = 'DRAFT' AND draftSlot = 1
        LIMIT 1
        """,
    )
    fun observeActiveDraft(): Flow<FullOrder?>

    @Transaction
    @Query(
        """
        SELECT * FROM orders
        WHERE status = 'DRAFT' AND draftSlot = 1
        LIMIT 1
        """,
    )
    suspend fun getActiveDraft(): FullOrder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraft(order: OrderEntity): Long

    @Query(
        """
        SELECT * FROM products
        WHERE id = :productId AND active = 1
        LIMIT 1
        """,
    )
    suspend fun getActiveProductForQuickAdd(productId: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateOrderItem(item: OrderItemEntity): Int

    @Query(
        """
        SELECT * FROM additions
        WHERE active = 1 AND id IN (:additionIds)
        """,
    )
    suspend fun getActiveAdditionsByIds(additionIds: List<Long>): List<AdditionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItemAdditions(additions: List<OrderItemAdditionEntity>)

    @Query(
        """
        DELETE FROM order_item_additions
        WHERE orderItemId = :orderItemId AND id IN (:relationIds)
        """,
    )
    suspend fun deleteOrderItemAdditions(
        orderItemId: String,
        relationIds: List<String>,
    ): Int

    @Query(
        """
        SELECT ingredients.* FROM ingredients
        INNER JOIN product_ingredients
            ON product_ingredients.ingredientId = ingredients.id
        WHERE product_ingredients.productId = :productId
            AND ingredients.id IN (:ingredientIds)
        ORDER BY product_ingredients.displayOrder ASC, ingredients.id ASC
        """,
    )
    suspend fun getProductIngredientsByIds(
        productId: Long,
        ingredientIds: List<Long>,
    ): List<IngredientEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItemRemovals(removals: List<OrderItemRemovalEntity>)

    @Query(
        """
        DELETE FROM order_item_removals
        WHERE orderItemId = :orderItemId AND id IN (:relationIds)
        """,
    )
    suspend fun deleteOrderItemRemovals(
        orderItemId: String,
        relationIds: List<String>,
    ): Int

    @Query("DELETE FROM order_item_removals WHERE orderItemId = :orderItemId")
    suspend fun deleteAllOrderItemRemovals(orderItemId: String): Int

    @Query("DELETE FROM order_item_additions WHERE orderItemId = :orderItemId")
    suspend fun deleteAllOrderItemAdditions(orderItemId: String): Int

    @Query(
        """
        DELETE FROM order_items
        WHERE id = :orderItemId AND orderId = :orderId
        """,
    )
    suspend fun deleteOrderItem(orderId: String, orderItemId: String): Int

    @Query(
        """
        UPDATE orders
        SET updatedAt = :updatedAt
        WHERE id = :orderId AND status = 'DRAFT' AND draftSlot = 1
        """,
    )
    suspend fun updateDraftTimestamp(orderId: String, updatedAt: Long): Int

    @Query(
        """
        DELETE FROM orders
        WHERE id = :orderId AND status = 'DRAFT' AND draftSlot = 1
        """,
    )
    suspend fun deleteDraft(orderId: String): Int

    @Transaction
    suspend fun replaceDraft(
        orderId: String,
        replacement: OrderEntity,
    ): ReplaceDraftDatabaseResult {
        if (deleteDraft(orderId) != 1) {
            return ReplaceDraftDatabaseResult.OriginalNotFoundOrNotDraft
        }
        if (insertDraft(replacement) == INSERT_CONFLICT) {
            throw DraftReplacementConflictException()
        }
        return ReplaceDraftDatabaseResult.Replaced
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}

sealed interface ReplaceDraftDatabaseResult {
    data object Replaced : ReplaceDraftDatabaseResult

    data object OriginalNotFoundOrNotDraft : ReplaceDraftDatabaseResult
}

class DraftReplacementConflictException : IllegalStateException()
