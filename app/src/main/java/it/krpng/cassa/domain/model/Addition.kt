package it.krpng.cassa.domain.model

import it.krpng.cassa.core.money.Money
import java.time.Instant

data class Addition(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val printedName: String?,
    val price: Money,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
