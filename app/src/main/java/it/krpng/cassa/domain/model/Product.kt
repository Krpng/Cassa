package it.krpng.cassa.domain.model

import it.krpng.cassa.core.money.Money
import java.time.Instant

data class Ingredient(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val active: Boolean,
)

data class ProductIngredient(
    val ingredient: Ingredient,
    val displayOrder: Int,
)

data class Product(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val printedName: String?,
    val category: ProductCategory,
    val price: Money,
    val automaticExtrasPricing: Boolean,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ingredients: List<ProductIngredient>,
)
