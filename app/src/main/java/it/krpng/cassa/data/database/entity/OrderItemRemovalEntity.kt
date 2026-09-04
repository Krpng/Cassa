package it.krpng.cassa.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_item_removals",
    foreignKeys = [
        ForeignKey(
            entity = OrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderItemId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["orderItemId"]),
        Index(value = ["ingredientId"]),
    ],
)
data class OrderItemRemovalEntity(
    @PrimaryKey
    val id: String,
    val orderItemId: String,
    val ingredientId: Long?,
    val ingredientNameSnapshot: String,
    val displayOrder: Int,
)
