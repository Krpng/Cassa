package it.krpng.cassa.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity

data class OrderWithItems(
    @Embedded
    val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId",
    )
    val items: List<OrderItemEntity>,
)

data class OrderItemWithModifiers(
    @Embedded
    val item: OrderItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderItemId",
    )
    val additions: List<OrderItemAdditionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderItemId",
    )
    val removals: List<OrderItemRemovalEntity>,
)

data class FullOrder(
    @Embedded
    val order: OrderEntity,
    @Relation(
        entity = OrderItemEntity::class,
        parentColumn = "id",
        entityColumn = "orderId",
    )
    val items: List<OrderItemWithModifiers>,
)
