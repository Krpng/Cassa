package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.NumberingStateRow

@Dao
interface NumberingStateDao {
    @Query(
        """
        SELECT * FROM numbering_state
        WHERE businessDate = :businessDate
        LIMIT 1
        """,
    )
    suspend fun getRaw(businessDate: String): NumberingStateRow?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: NumberingStateEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(state: NumberingStateEntity): Int
}
