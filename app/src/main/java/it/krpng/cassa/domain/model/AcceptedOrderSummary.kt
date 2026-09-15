package it.krpng.cassa.domain.model

import it.krpng.cassa.core.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Lightweight accepted-order row for Today/Archive lists (no items/modifiers).
 */
data class AcceptedOrderSummary(
    val id: String,
    val displayNumber: String,
    val acceptedAt: Instant,
    val total: Money,
    val businessDate: LocalDate,
)
