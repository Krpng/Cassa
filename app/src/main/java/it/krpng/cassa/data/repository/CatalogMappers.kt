package it.krpng.cassa.data.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.relation.ProductIngredientWithIngredient
import it.krpng.cassa.data.database.relation.ProductWithIngredients
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductIngredient
import java.time.Instant

internal fun AdditionEntity.toDomain(): Addition = Addition(
    id = id,
    name = name,
    normalizedName = normalizedName,
    printedName = printedName,
    price = Money.ofCents(priceCents),
    active = active,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Addition.toEntity(): AdditionEntity = AdditionEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    printedName = printedName,
    priceCents = price.cents,
    active = active,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    name = name,
    normalizedName = normalizedName,
    active = active,
)

internal fun Ingredient.toEntity(): IngredientEntity = IngredientEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    active = active,
)

internal fun ProductWithIngredients.toDomain(): Product = Product(
    id = product.id,
    name = product.name,
    normalizedName = product.normalizedName,
    printedName = product.printedName,
    category = product.category,
    price = Money.ofCents(product.priceCents),
    automaticExtrasPricing = product.automaticExtrasPricing,
    active = product.active,
    createdAt = Instant.ofEpochMilli(product.createdAt),
    updatedAt = Instant.ofEpochMilli(product.updatedAt),
    ingredients = ingredients
        .sortedWith(compareBy({ it.link.displayOrder }, { it.ingredient.id }))
        .map(ProductIngredientWithIngredient::toDomain),
)

internal fun Product.toDatabaseModel(): ProductWithIngredients = ProductWithIngredients(
    product = ProductEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        printedName = printedName,
        category = category,
        priceCents = price.cents,
        automaticExtrasPricing = automaticExtrasPricing,
        active = active,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    ),
    ingredients = ingredients.map { it.toDatabaseModel(productId = id) },
)

private fun ProductIngredientWithIngredient.toDomain(): ProductIngredient = ProductIngredient(
    ingredient = ingredient.toDomain(),
    displayOrder = link.displayOrder,
)

private fun ProductIngredient.toDatabaseModel(productId: Long): ProductIngredientWithIngredient =
    ProductIngredientWithIngredient(
        link = ProductIngredientEntity(
            productId = productId,
            ingredientId = ingredient.id,
            displayOrder = displayOrder,
        ),
        ingredient = ingredient.toEntity(),
    )
