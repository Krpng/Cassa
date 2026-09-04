package it.krpng.cassa.core.money

import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class Money private constructor(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money =
        ofCents(Math.addExact(cents, other.cents))

    operator fun minus(other: Money): Money {
        require(cents >= other.cents) { "A monetary amount cannot be negative" }
        return Money(cents - other.cents)
    }

    operator fun times(quantity: Int): Money {
        require(quantity > 0) { "Quantity must be greater than zero" }
        return ofCents(Math.multiplyExact(cents, quantity.toLong()))
    }

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun formatEur(): String {
        val euros = cents / CENTS_PER_EURO
        val remainingCents = cents % CENTS_PER_EURO
        val formattedEuros = NumberFormat.getIntegerInstance(ITALIAN_LOCALE).format(euros)
        return "$formattedEuros,${remainingCents.toString().padStart(2, '0')} €"
    }

    companion object {
        val ZERO: Money = Money(0)

        fun ofCents(cents: Long): Money {
            require(cents >= 0) { "A monetary amount cannot be negative" }
            return Money(cents)
        }

        private const val CENTS_PER_EURO = 100L
        private val ITALIAN_LOCALE = Locale.forLanguageTag("it-IT")
    }
}
