package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuImportPlannerTest {
    private val planner = MenuImportPlanner()

    @Test
    fun `ODS-008 same normalized product updates existing record including category change`() {
        val existing = product(
            id = 41,
            name = "Pizza al forno",
            category = ProductCategory.PIZZA,
            priceCents = 700,
        )
        val imported = validatedProduct(
            name = "Pizza al forno",
            category = ProductCategory.FRITTURA,
            priceCents = 750,
        )

        val plan = plan(products = listOf(imported), existingProducts = listOf(existing))

        assertTrue(plan.productsToCreate.isEmpty())
        assertTrue(plan.unchangedProducts.isEmpty())
        assertEquals(41, plan.productsToUpdate.single().existingProductId)
        assertEquals(ProductCategory.FRITTURA, plan.productsToUpdate.single().values.category)
    }

    @Test
    fun `new normalized product produces create with new-record optional defaults`() {
        val imported = validatedProduct(
            name = "Marinara",
            printedName = ValidatedOptionalField.ColumnAbsent,
            ingredients = ValidatedOptionalField.ColumnAbsent,
        )

        val plan = plan(products = listOf(imported))

        val create = plan.productsToCreate.single()
        assertEquals("marinara", create.values.normalizedName)
        assertEquals(ImportFieldUpdate.Replace<String?>(null), create.values.printedName)
        assertEquals(
            ImportFieldUpdate.Replace(emptyList<ValidatedIngredientImport>()),
            create.values.ingredients,
        )
        assertTrue(plan.productsToUpdate.isEmpty())
    }

    @Test
    fun `equal imported product is classified unchanged`() {
        val existing = product(
            id = 7,
            name = "Capricciosa",
            printedName = "CAPR",
            ingredients = listOf("Pomodoro", "Mozzarella"),
        )
        val imported = validatedProduct(
            name = "Capricciosa",
            printedName = presentText("CAPR"),
            ingredients = presentIngredients("Pomodoro", "Mozzarella"),
        )

        val plan = plan(products = listOf(imported), existingProducts = listOf(existing))

        assertEquals(7, plan.unchangedProducts.single().existingProductId)
        assertTrue(plan.productsToCreate.isEmpty())
        assertTrue(plan.productsToUpdate.isEmpty())
    }

    @Test
    fun `ODS-009 existing product absent from ODS produces no operation`() {
        val existing = product(id = 9, name = "Prodotto locale", active = false)

        val plan = plan(existingProducts = listOf(existing))

        assertTrue(plan.productsToCreate.isEmpty())
        assertTrue(plan.productsToUpdate.isEmpty())
        assertTrue(plan.unchangedProducts.isEmpty())
    }

    @Test
    fun `printedName column absent preserves existing value during another update`() {
        val existing = product(id = 10, name = "Margherita", printedName = "MARG", priceCents = 700)
        val imported = validatedProduct(
            name = "Margherita",
            priceCents = 800,
            printedName = ValidatedOptionalField.ColumnAbsent,
        )

        val update = plan(
            products = listOf(imported),
            existingProducts = listOf(existing),
        ).productsToUpdate.single()

        assertEquals(ImportFieldUpdate.PreserveExisting, update.values.printedName)
    }

    @Test
    fun `ODS-015 printedName present blank clears stored value for runtime fallback`() {
        val existing = product(id = 11, name = "Margherita", printedName = "MARG")
        val imported = validatedProduct(
            name = "Margherita",
            printedName = ValidatedOptionalField.ColumnPresent(null),
        )

        val update = plan(
            products = listOf(imported),
            existingProducts = listOf(existing),
        ).productsToUpdate.single()

        assertEquals(ImportFieldUpdate.Replace<String?>(null), update.values.printedName)
    }

    @Test
    fun `ODS-018 ingredients column absent preserves existing composition`() {
        val existing = product(
            id = 12,
            name = "Ortolana",
            priceCents = 800,
            ingredients = listOf("Zucchine", "Melanzane"),
        )
        val imported = validatedProduct(
            name = "Ortolana",
            priceCents = 850,
            ingredients = ValidatedOptionalField.ColumnAbsent,
        )

        val update = plan(
            products = listOf(imported),
            existingProducts = listOf(existing),
        ).productsToUpdate.single()

        assertEquals(ImportFieldUpdate.PreserveExisting, update.values.ingredients)
    }

    @Test
    fun `ODS-017 ingredients column present blank clears existing composition`() {
        val existing = product(
            id = 13,
            name = "Marinara",
            ingredients = listOf("Pomodoro", "Aglio"),
        )
        val imported = validatedProduct(
            name = "Marinara",
            ingredients = ValidatedOptionalField.ColumnPresent(emptyList()),
        )

        val update = plan(
            products = listOf(imported),
            existingProducts = listOf(existing),
        ).productsToUpdate.single()

        assertEquals(
            ImportFieldUpdate.Replace(emptyList<ValidatedIngredientImport>()),
            update.values.ingredients,
        )
    }

    @Test
    fun `ingredient display and order differences produce an update`() {
        val existing = product(
            id = 14,
            name = "Speciale",
            ingredients = listOf("Pomodoro", "Prorcini"),
        )
        val imported = validatedProduct(
            name = "Speciale",
            ingredients = presentIngredients("Prorcini", "Pomodoro"),
        )

        val update = plan(
            products = listOf(imported),
            existingProducts = listOf(existing),
        ).productsToUpdate.single()

        assertEquals(
            listOf("Prorcini", "Pomodoro"),
            (update.values.ingredients as ImportFieldUpdate.Replace).value.map { it.name },
        )
    }

    @Test
    fun `addition same normalized name updates while new name creates`() {
        val existing = addition(id = 21, name = "Mozzarella", priceCents = 100)
        val update = validatedAddition(name = "Mozzarella", priceCents = 150)
        val create = validatedAddition(name = "Prosciutto", priceCents = 200, row = 3)

        val plan = plan(
            additions = listOf(update, create),
            existingAdditions = listOf(existing),
        )

        assertEquals(21, plan.additionsToUpdate.single().existingAdditionId)
        assertEquals(Money.ofCents(150), plan.additionsToUpdate.single().values.price)
        assertEquals("prosciutto", plan.additionsToCreate.single().values.normalizedName)
    }

    @Test
    fun `product and addition with same normalized name remain separate namespaces`() {
        val existingProduct = product(id = 31, name = "Mozzarella")
        val importedProduct = validatedProduct(name = "Mozzarella")
        val importedAddition = validatedAddition(name = "Mozzarella")

        val plan = plan(
            products = listOf(importedProduct),
            additions = listOf(importedAddition),
            existingProducts = listOf(existingProduct),
        )

        assertEquals(31, plan.unchangedProducts.single().existingProductId)
        assertEquals("mozzarella", plan.additionsToCreate.single().values.normalizedName)
    }

    @Test
    fun `addition optional printedName preserves absent and clears present blank`() {
        val preserveExisting = addition(id = 32, name = "Olive", printedName = "OLV", priceCents = 100)
        val clearExisting = addition(id = 33, name = "Capperi", printedName = "CAP", priceCents = 100)

        val plan = plan(
            additions = listOf(
                validatedAddition(
                    name = "Olive",
                    priceCents = 150,
                    printedName = ValidatedOptionalField.ColumnAbsent,
                ),
                validatedAddition(
                    name = "Capperi",
                    printedName = ValidatedOptionalField.ColumnPresent(null),
                    row = 3,
                ),
            ),
            existingAdditions = listOf(preserveExisting, clearExisting),
        )

        assertEquals(
            ImportFieldUpdate.PreserveExisting,
            plan.additionsToUpdate[0].values.printedName,
        )
        assertEquals(
            ImportFieldUpdate.Replace<String?>(null),
            plan.additionsToUpdate[1].values.printedName,
        )
    }

    @Test
    fun `plan is deterministic regardless of existing catalog order`() {
        val existingProducts = listOf(
            product(id = 1, name = "Marinara"),
            product(id = 2, name = "Diavola"),
        )
        val existingAdditions = listOf(
            addition(id = 1, name = "Olive"),
            addition(id = 2, name = "Capperi"),
        )
        val imported = ValidatedMenuImport(
            products = listOf(
                validatedProduct(name = "Marinara"),
                validatedProduct(name = "Diavola", row = 3),
            ),
            additions = listOf(
                validatedAddition(name = "Olive"),
                validatedAddition(name = "Capperi", row = 3),
            ),
        )

        val first = planner.createPlan(imported, existingProducts, existingAdditions)
        val second = planner.createPlan(
            imported,
            existingProducts.reversed(),
            existingAdditions.reversed(),
        )

        assertEquals(first, second)
    }

    @Test
    fun `special product names do not change compare behavior`() {
        val imported = listOf(
            validatedProduct(name = "Pizza Fritta", row = 2),
            validatedProduct(name = "Ripieno", row = 3),
            validatedProduct(name = "Qualsiasi", row = 4),
        )

        val creates = plan(products = imported).productsToCreate

        assertEquals(3, creates.size)
        assertTrue(creates.all { it.values.printedName == ImportFieldUpdate.Replace<String?>(null) })
        assertTrue(creates.all {
            it.values.ingredients ==
                ImportFieldUpdate.Replace(emptyList<ValidatedIngredientImport>())
        })
    }

    private fun plan(
        products: List<ValidatedProductImport> = emptyList(),
        additions: List<ValidatedAdditionImport> = emptyList(),
        existingProducts: List<Product> = emptyList(),
        existingAdditions: List<Addition> = emptyList(),
    ): MenuImportPlan = planner.createPlan(
        validatedImport = ValidatedMenuImport(products, additions),
        existingProducts = existingProducts,
        existingAdditions = existingAdditions,
    )

    private fun validatedProduct(
        name: String,
        category: ProductCategory = ProductCategory.PIZZA,
        priceCents: Long = 700,
        printedName: ValidatedOptionalField<String> = ValidatedOptionalField.ColumnAbsent,
        ingredients: ValidatedOptionalField<List<ValidatedIngredientImport>> =
            ValidatedOptionalField.ColumnAbsent,
        row: Int = 2,
    ): ValidatedProductImport = ValidatedProductImport(
        sourceSheet = "Prodotti",
        sourceRow = row,
        name = name,
        normalizedName = it.krpng.cassa.core.normalization.TextNormalizer.normalize(name),
        price = Money.ofCents(priceCents),
        category = category,
        printedName = printedName,
        ingredients = ingredients,
    )

    private fun validatedAddition(
        name: String,
        priceCents: Long = 100,
        printedName: ValidatedOptionalField<String> = ValidatedOptionalField.ColumnAbsent,
        row: Int = 2,
    ): ValidatedAdditionImport = ValidatedAdditionImport(
        sourceSheet = "Aggiunte",
        sourceRow = row,
        name = name,
        normalizedName = it.krpng.cassa.core.normalization.TextNormalizer.normalize(name),
        price = Money.ofCents(priceCents),
        printedName = printedName,
    )

    private fun presentText(value: String?): ValidatedOptionalField<String> =
        ValidatedOptionalField.ColumnPresent(value)

    private fun presentIngredients(
        vararg names: String,
    ): ValidatedOptionalField<List<ValidatedIngredientImport>> =
        ValidatedOptionalField.ColumnPresent(
            names.map { name ->
                ValidatedIngredientImport(
                    name = name,
                    normalizedName = it.krpng.cassa.core.normalization.TextNormalizer.normalize(name),
                )
            },
        )

    private fun product(
        id: Long,
        name: String,
        category: ProductCategory = ProductCategory.PIZZA,
        priceCents: Long = 700,
        printedName: String? = null,
        ingredients: List<String> = emptyList(),
        active: Boolean = true,
        automaticExtrasPricing: Boolean = true,
    ): Product = Product(
        id = id,
        name = name,
        normalizedName = it.krpng.cassa.core.normalization.TextNormalizer.normalize(name),
        printedName = printedName,
        category = category,
        price = Money.ofCents(priceCents),
        automaticExtrasPricing = automaticExtrasPricing,
        active = active,
        createdAt = NOW,
        updatedAt = NOW,
        ingredients = ingredients.mapIndexed { index, ingredientName ->
            ProductIngredient(
                ingredient = Ingredient(
                    id = index.toLong() + 1,
                    name = ingredientName,
                    normalizedName =
                        it.krpng.cassa.core.normalization.TextNormalizer.normalize(ingredientName),
                    active = true,
                ),
                displayOrder = index,
            )
        },
    )

    private fun addition(
        id: Long,
        name: String,
        priceCents: Long = 100,
        printedName: String? = null,
        active: Boolean = true,
    ): Addition = Addition(
        id = id,
        name = name,
        normalizedName = it.krpng.cassa.core.normalization.TextNormalizer.normalize(name),
        printedName = printedName,
        price = Money.ofCents(priceCents),
        active = active,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-05T12:00:00Z")
    }
}
