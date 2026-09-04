package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.krpng.cassa.data.database.entity.AdditionEntity

@Dao
interface AdditionDao {
    @Query("SELECT * FROM additions WHERE id = :additionId LIMIT 1")
    suspend fun getById(additionId: Long): AdditionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(addition: AdditionEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(addition: AdditionEntity): Int

    @Query(
        """
        UPDATE additions
        SET active = :active, updatedAt = :updatedAt
        WHERE id = :additionId
        """,
    )
    suspend fun updateActive(additionId: Long, active: Boolean, updatedAt: Long): Int
}
