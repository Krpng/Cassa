package it.krpng.cassa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.krpng.cassa.data.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query(
        """
        SELECT * FROM app_settings
        WHERE id = :id
        LIMIT 1
        """,
    )
    suspend fun get(id: Int = AppSettingsEntity.SINGLETON_ID): AppSettingsEntity?

    @Query(
        """
        SELECT * FROM app_settings
        WHERE id = :id
        LIMIT 1
        """,
    )
    fun observe(id: Int = AppSettingsEntity.SINGLETON_ID): Flow<AppSettingsEntity?>

    @Query(
        """
        SELECT numberingMode FROM app_settings
        WHERE id = :id
        LIMIT 1
        """,
    )
    suspend fun getNumberingModeName(id: Int = AppSettingsEntity.SINGLETON_ID): String?

    @Query(
        """
        SELECT numberingMode FROM app_settings
        WHERE id = :id
        LIMIT 1
        """,
    )
    fun observeNumberingModeName(id: Int = AppSettingsEntity.SINGLETON_ID): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(settings: AppSettingsEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(settings: AppSettingsEntity): Int
}
