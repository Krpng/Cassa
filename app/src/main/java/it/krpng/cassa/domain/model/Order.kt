package it.krpng.cassa.domain.model

import it.krpng.cassa.core.money.Money
import java.time.Instant
import java.time.LocalDate

data class OrderItemAddition(
    val id: String,
    val additionId: Long?,
    val nameSnapshot: String,
    val printedNameSnapshot: String,
    val listedPrice: Money,
    val chargedPrice: Money,
    val displayOrder: Int,
)

data class OrderItemRemoval(
    val id: String,
    val ingredientId: Long?,
    val nameSnapshot: String,
    val displayOrder: Int,
)

data class OrderItem(
    val id: String,
    val productId: Long?,
    val productNameSnapshot: String,
    val productPrintedNameSnapshot: String,
    val categorySnapshot: ProductCategory,
    val quantity: Int,
    val baseUnitPrice: Money,
    val automaticExtrasTotal: Money,
    val manualUnitPrice: Money?,
    val finalUnitPrice: Money,
    val automaticExtrasPricingSnapshot: Boolean,
    val note: String?,
    val createdSequence: Int,
    val additions: List<OrderItemAddition>,
    val removals: List<OrderItemRemoval>,
)

data class Order(
    val id: String,
    val status: OrderStatus,
    val displayNumber: String?,
    val numberingMode: NumberingMode?,
    val numberingCycle: Int?,
    val businessDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val acceptedAt: Instant?,
    val total: Money,
    val generalNote: String?,
    val sourceOrderId: String?,
    val items: List<OrderItem>,
)
