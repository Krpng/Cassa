package it.krpng.cassa.domain.pricing

import it.krpng.cassa.core.money.Money

data class PricingResult(
    val automaticExtrasTotal: Money,
    val automaticUnitPrice: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money,
)

object PricingCalculator {
    fun calculate(
        baseUnitPrice: Money,
        additionPrices: List<Money>,
        automaticExtrasPricing: Boolean,
        manualUnitPrice: Money?,
        quantity: Int,
    ): PricingResult {
        val automaticExtrasTotal = if (automaticExtrasPricing) {
            additionPrices.fold(Money.ZERO, Money::plus)
        } else {
            Money.ZERO
        }
        val automaticUnitPrice = baseUnitPrice + automaticExtrasTotal
        val finalUnitPrice = manualUnitPrice ?: automaticUnitPrice

        return PricingResult(
            automaticExtrasTotal = automaticExtrasTotal,
            automaticUnitPrice = automaticUnitPrice,
            finalUnitPrice = finalUnitPrice,
            lineTotal = finalUnitPrice * quantity,
        )
    }
}
