package it.krpng.cassa.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.krpng.cassa.domain.model.ProductCategory

@Entity(
    tableName = "products",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val printedName: String?,
    val category: ProductCategory,
    val priceCents: Long,
    @ColumnInfo(defaultValue = "1")
    val automaticExtrasPricing: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(priceCents >= 0) { "Product price must not be negative" }
    }
}
