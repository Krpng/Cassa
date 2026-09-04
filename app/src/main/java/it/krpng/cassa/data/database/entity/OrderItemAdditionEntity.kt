package it.krpng.cassa.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_item_additions",
    foreignKeys = [
        ForeignKey(
            entity = OrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderItemId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AdditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["additionId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["orderItemId"]),
        Index(value = ["additionId"]),
    ],
)
data class OrderItemAdditionEntity(
    @PrimaryKey
    val id: String,
    val orderItemId: String,
    val additionId: Long?,
    val additionNameSnapshot: String,
    val additionPrintedNameSnapshot: String,
    val listedPriceCents: Long,
    val chargedPriceCents: Long,
    val displayOrder: Int,
) {
    init {
        require(listedPriceCents >= 0) { "Listed addition price must not be negative" }
        require(chargedPriceCents >= 0) { "Charged addition price must not be negative" }
    }
}
