package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import it.krpng.cassa.data.database.entity.OrderEntity
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
