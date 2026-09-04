package it.krpng.cassa.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "numbering_state")
data class NumberingStateEntity(
    @PrimaryKey
    val businessDate: String,
    @ColumnInfo(defaultValue = "1")
    val nextSequentialNumber: Long = 1,
    @ColumnInfo(defaultValue = "1")
    val randomCycle: Int = 1,
    val randomSeed: Long,
    @ColumnInfo(defaultValue = "0")
    val randomPosition: Int = 0,
    val updatedAt: Long,
) {
    init {
        require(nextSequentialNumber > 0) { "Next sequential number must be positive" }
        require(randomCycle > 0) { "Random cycle must be positive" }
        require(randomPosition >= 0) { "Random position must not be negative" }
    }
}
