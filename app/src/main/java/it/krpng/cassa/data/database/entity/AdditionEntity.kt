package it.krpng.cassa.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "additions",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class AdditionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val printedName: String?,
    val priceCents: Long,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(priceCents >= 0) { "Addition price must not be negative" }
    }
}
