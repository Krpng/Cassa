package it.krpng.cassa.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import it.krpng.cassa.domain.model.NumberingMode

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    @ColumnInfo(defaultValue = "1")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(defaultValue = "'SEQUENTIAL'")
    val numberingMode: NumberingMode = NumberingMode.SEQUENTIAL,
    @ColumnInfo(defaultValue = "300")
    val businessDayStartMinutes: Int = DEFAULT_BUSINESS_DAY_START_MINUTES,
    @ColumnInfo(defaultValue = "'Europe/Rome'")
    val timezoneId: String = DEFAULT_TIMEZONE_ID,
    val updatedAt: Long,
) {
    init {
        require(id == SINGLETON_ID) { "App settings id must be 1" }
        require(businessDayStartMinutes in MINUTES_PER_DAY) {
            "Business day start minutes must be between 0 and 1439"
        }
    }

    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_BUSINESS_DAY_START_MINUTES = 300
        const val DEFAULT_TIMEZONE_ID = "Europe/Rome"

        private val MINUTES_PER_DAY = 0 until 24 * 60
    }
}
