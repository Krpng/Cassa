package it.krpng.cassa.domain.printer

import it.krpng.cassa.core.money.Money

/**
 * Deterministic money text for PrintableDocument lines (D-051).
 * Comma decimal separator, exactly 2 fraction digits, no grouping, no currency symbol.
 */
object PrintMoneyFormat {
    fun format(cents: Long): String {
        require(cents >= 0) { "Print money cents must not be negative" }
        val euros = cents / 100L
        val fraction = cents % 100L
        return "$euros,${fraction.toString().padStart(2, '0')}"
    }

    fun format(money: Money): String = format(money.cents)
}
