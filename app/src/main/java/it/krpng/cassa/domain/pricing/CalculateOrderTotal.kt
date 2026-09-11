package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.OrderItem

/**
 * Pure DRAFT live-total derivation from persisted order lines.
 * Does not read `orders.totalCents`, catalog prices, or unsaved editor state.
 */
sealed interface OrderTotalResult {
    data class Success(val total: Money) : OrderTotalResult

    data object AmountOverflow : OrderTotalResult
}

data class OrderLineTotalResult(
    val itemId: String,
    val lineTotal: Money?,
)

data class OrderTotalsComputation(
    val lineTotals: List<OrderLineTotalResult>,
    val orderTotal: OrderTotalResult,
)

object CalculateOrderTotal {
    fun lineTotalCents(
        finalUnitPrice: Money,
        quantity: Int,
    ): Money = finalUnitPrice * quantity

    fun fromPersistedItems(items: List<OrderItem>): OrderTotalsComputation {
        var lineOverflow = false
        val lineTotals = items.map { item ->
            try {
                OrderLineTotalResult(
                    itemId = item.id,
                    lineTotal = lineTotalCents(item.finalUnitPrice, item.quantity),
                )
            } catch (_: ArithmeticException) {
                lineOverflow = true
                OrderLineTotalResult(itemId = item.id, lineTotal = null)
            }
        }
        if (lineOverflow) {
            return OrderTotalsComputation(
                lineTotals = lineTotals,
                orderTotal = OrderTotalResult.AmountOverflow,
            )
        }
        return try {
            val orderTotal = lineTotals.fold(Money.ZERO) { acc, line ->
                acc + requireNotNull(line.lineTotal)
            }
            OrderTotalsComputation(
                lineTotals = lineTotals,
                orderTotal = OrderTotalResult.Success(orderTotal),
            )
        } catch (_: ArithmeticException) {
            OrderTotalsComputation(
                lineTotals = lineTotals,
                orderTotal = OrderTotalResult.AmountOverflow,
            )
        }
    }

    fun orderTotalFromPersistedItems(items: List<OrderItem>): OrderTotalResult =
        fromPersistedItems(items).orderTotal
}
