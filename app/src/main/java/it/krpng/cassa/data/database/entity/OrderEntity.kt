package it.krpng.cassa.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.OrderStatus

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["draftSlot"], unique = true),
        Index(value = ["status"]),
        Index(value = ["businessDate", "acceptedAt"]),
        Index(value = ["displayNumber"]),
        Index(value = ["businessDate", "displayNumber"]),
    ],
)
data class OrderEntity(
    @PrimaryKey
    val id: String,
    val status: OrderStatus,
    val draftSlot: Int?,
    val displayNumber: String?,
    val numberingMode: NumberingMode?,
    val numberingCycle: Int?,
    val businessDate: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val acceptedAt: Long?,
    val totalCents: Long,
    val generalNote: String?,
    val sourceOrderId: String?,
)
