package it.krpng.cassa.data.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.relation.ProductIngredientWithIngredient
import it.krpng.cassa.data.database.relation.ProductWithIngredients
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMappersTest {
    @Test
    fun `product relation maps database values to domain and orders ingredients`() {
        val databaseModel = productWithIngredients(
            ingredients = listOf(
                ingredientRelation(id = 12, displayOrder = 2, name = "Basilico"),
                ingredientRelation(id = 11, displayOrder = 1, name = "Mozzarella"),
            ),
        )

        val product = databaseModel.toDomain()

        assertEquals(10, product.id)
        assertEquals("Margherita", product.name)
        assertEquals("margherita", product.normalizedName)
        assertNull(product.printedName)
        assertEquals(ProductCategory.PIZZA, product.category)
        assertEquals(Money.ofCents(700), product.price)
        assertEquals(Instant.ofEpochMilli(1_000), product.createdAt)
        assertEquals(listOf(11L, 12L), product.ingredients.map { it.ingredient.id })
        assertEquals(listOf(1, 2), product.ingredients.map { it.displayOrder })
    }

    @Test
    fun `product domain maps back without losing persistence values`() {
        val product = productWithIngredients().toDomain()

        val databaseModel = product.toDatabaseModel()

        assertEquals(product.id, databaseModel.product.id)
        assertEquals(product.price.cents, databaseModel.product.priceCents)
        assertEquals(product.createdAt.toEpochMilli(), databaseModel.product.createdAt)
        assertEquals(product.updatedAt.toEpochMilli(), databaseModel.product.updatedAt)
        assertEquals(product.id, databaseModel.ingredients.single().link.productId)
        assertEquals(
            product.ingredients.single().ingredient.id,
            databaseModel.ingredients.single().link.ingredientId,
        )
        assertEquals(
            product.ingredients.single().displayOrder,
            databaseModel.ingredients.single().link.displayOrder,
        )
    }

    private fun productWithIngredients(
        ingredients: List<ProductIngredientWithIngredient> = listOf(ingredientRelation()),
    ): ProductWithIngredients = ProductWithIngredients(
        product = ProductEntity(
            id = 10,
            name = "Margherita",
            normalizedName = "margherita",
            printedName = null,
            category = ProductCategory.PIZZA,
            priceCents = 700,
            automaticExtrasPricing = true,
            active = true,
            createdAt = 1_000,
            updatedAt = 2_000,
        ),
        ingredients = ingredients,
    )

    private fun ingredientRelation(
        id: Long = 11,
        displayOrder: Int = 1,
        name: String = "Mozzarella",
    ): ProductIngredientWithIngredient = ProductIngredientWithIngredient(
        link = ProductIngredientEntity(
            productId = 10,
            ingredientId = id,
            displayOrder = displayOrder,
        ),
        ingredient = IngredientEntity(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            active = true,
        ),
    )
}
