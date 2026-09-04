package it.krpng.cassa.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.krpng.cassa.domain.model.ProductCategory

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["productId"]),
    ],
)
data class OrderItemEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val productId: Long?,
    val productNameSnapshot: String,
    val productPrintedNameSnapshot: String,
    val categorySnapshot: ProductCategory,
    val quantity: Int,
    val baseUnitPriceCents: Long,
    val automaticExtrasTotalCents: Long,
    val manualUnitPriceCents: Long?,
    val finalUnitPriceCents: Long,
    val automaticExtrasPricingSnapshot: Boolean,
    val note: String?,
    val createdSequence: Int,
) {
    init {
        require(quantity > 0) { "Order item quantity must be positive" }
        require(baseUnitPriceCents >= 0) { "Base unit price must not be negative" }
        require(automaticExtrasTotalCents >= 0) { "Automatic extras total must not be negative" }
        require(manualUnitPriceCents == null || manualUnitPriceCents >= 0) {
            "Manual unit price must not be negative"
        }
        require(finalUnitPriceCents >= 0) { "Final unit price must not be negative" }
    }
}
