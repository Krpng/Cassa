package it.krpng.cassa.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity

data class ProductIngredientWithIngredient(
    @Embedded
    val link: ProductIngredientEntity,
    @Relation(
        parentColumn = "ingredientId",
        entityColumn = "id",
    )
    val ingredient: IngredientEntity,
)

data class ProductWithIngredients(
    @Embedded
    val product: ProductEntity,
    @Relation(
        entity = ProductIngredientEntity::class,
        parentColumn = "id",
        entityColumn = "productId",
    )
    val ingredients: List<ProductIngredientWithIngredient>,
)
