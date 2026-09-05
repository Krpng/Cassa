package it.krpng.cassa.data.ods

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMenuImportCommitterTest {
    private lateinit var database: CassaDatabase
    private lateinit var committer: RoomMenuImportCommitter

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        committer = RoomMenuImportCommitter(
            database = database,
            clockProvider = object : ClockProvider {
                override fun now(): Instant = IMPORT_TIME
            },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun commitAppliesPlanAndPreservesIdsFlagsAndAbsentRecords() = runBlocking {
        val oldIngredientId = database.ingredientDao().insert(ingredient("Vecchio", "vecchio"))
        val reusableIngredientId = database.ingredientDao().insert(
            ingredient("Mozzarella", "mozzarella", active = false),
        )
        val updatedProductId = insertProduct(
            product(
                name = "Prodotto da aggiornare",
                normalizedName = "prodotto da aggiornare",
                printedName = "STAMPA ESISTENTE",
                active = false,
                automaticExtrasPricing = false,
                updatedAt = 2_000,
            ),
            listOf(ProductIngredientEntity(0, oldIngredientId, 0)),
        )
        val clearedProductId = insertProduct(
            product(name = "Da svuotare", normalizedName = "da svuotare"),
            listOf(ProductIngredientEntity(0, oldIngredientId, 0)),
        )
        val unchangedProductId = insertProduct(
            product(name = "Invariato", normalizedName = "invariato", updatedAt = 3_000),
        )
        val absentProductId = insertProduct(
            product(name = "Assente ODS", normalizedName = "assente ods", updatedAt = 4_000),
        )
        val updatedAdditionId = database.additionDao().insert(
            addition(
                name = "Aggiunta esistente",
                normalizedName = "aggiunta esistente",
                printedName = "AGG ESISTENTE",
                active = false,
            ),
        )
        val absentAdditionId = database.additionDao().insert(
            addition(name = "Aggiunta assente", normalizedName = "aggiunta assente"),
        )
        val unchangedAdditionId = database.additionDao().insert(
            addition(
                name = "Aggiunta invariata",
                normalizedName = "aggiunta invariata",
                updatedAt = 3_000,
            ),
        )

        committer.commit(
            MenuImportPlan(
                productsToCreate = listOf(
                    ProductImportCreate(
                        sourceSheet = "Prodotti",
                        sourceRow = 2,
                        values = productValues(
                            name = "Nuovo prodotto",
                            normalizedName = "nuovo prodotto",
                            printedName = ImportFieldUpdate.Replace(null),
                            ingredients = ImportFieldUpdate.Replace(
                                listOf(
                                    ValidatedIngredientImport("Mozzarella", "mozzarella"),
                                    ValidatedIngredientImport("Basilico", "basilico"),
                                ),
                            ),
                        ),
                    ),
                ),
                productsToUpdate = listOf(
                    ProductImportUpdate(
                        existingProductId = updatedProductId,
                        sourceSheet = "Prodotti",
                        sourceRow = 3,
                        values = productValues(
                            name = "Prodotto aggiornato",
                            normalizedName = "prodotto da aggiornare",
                            category = ProductCategory.BIBITA,
                            printedName = ImportFieldUpdate.PreserveExisting,
                            ingredients = ImportFieldUpdate.PreserveExisting,
                        ),
                    ),
                    ProductImportUpdate(
                        existingProductId = clearedProductId,
                        sourceSheet = "Prodotti",
                        sourceRow = 4,
                        values = productValues(
                            name = "Da svuotare",
                            normalizedName = "da svuotare",
                            printedName = ImportFieldUpdate.Replace(null),
                            ingredients = ImportFieldUpdate.Replace(emptyList()),
                        ),
                    ),
                ),
                unchangedProducts = listOf(
                    UnchangedProductImport(unchangedProductId, "Prodotti", 5),
                ),
                additionsToCreate = listOf(
                    AdditionImportCreate(
                        sourceSheet = "Aggiunte",
                        sourceRow = 2,
                        values = additionValues(
                            name = "Aggiunta zero",
                            normalizedName = "aggiunta zero",
                            price = Money.ZERO,
                            printedName = ImportFieldUpdate.Replace(null),
                        ),
                    ),
                ),
                additionsToUpdate = listOf(
                    AdditionImportUpdate(
                        existingAdditionId = updatedAdditionId,
                        sourceSheet = "Aggiunte",
                        sourceRow = 3,
                        values = additionValues(
                            name = "Aggiunta aggiornata",
                            normalizedName = "aggiunta esistente",
                            printedName = ImportFieldUpdate.PreserveExisting,
                        ),
                    ),
                ),
                unchangedAdditions = listOf(
                    UnchangedAdditionImport(unchangedAdditionId, "Aggiunte", 4),
                ),
            ),
        )

        val updatedProduct = database.productDao().getWithIngredients(updatedProductId)!!
        assertEquals(updatedProductId, updatedProduct.product.id)
        assertEquals("Prodotto aggiornato", updatedProduct.product.name)
        assertEquals(ProductCategory.BIBITA, updatedProduct.product.category)
        assertEquals("STAMPA ESISTENTE", updatedProduct.product.printedName)
        assertFalse(updatedProduct.product.active)
        assertFalse(updatedProduct.product.automaticExtrasPricing)
        assertEquals(listOf(oldIngredientId), updatedProduct.ingredients.map { it.ingredient.id })

        val clearedProduct = database.productDao().getWithIngredients(clearedProductId)!!
        assertEquals(null, clearedProduct.product.printedName)
        assertEquals(emptyList<Long>(), clearedProduct.ingredients.map { it.ingredient.id })

        val products = database.productDao().observeAllWithIngredients().first()
        val createdProduct = products.single { it.product.normalizedName == "nuovo prodotto" }
        assertEquals(true, createdProduct.product.active)
        assertEquals(true, createdProduct.product.automaticExtrasPricing)
        assertEquals(
            listOf(reusableIngredientId, database.ingredientDao().getByNormalizedName("basilico")!!.id),
            createdProduct.ingredients.sortedBy { it.link.displayOrder }.map { it.ingredient.id },
        )
        assertFalse(database.ingredientDao().getByNormalizedName("mozzarella")!!.active)
        assertNotNull(database.ingredientDao().getById(oldIngredientId))
        assertEquals(3_000, database.productDao().getWithIngredients(unchangedProductId)!!.product.updatedAt)
        assertEquals(4_000, database.productDao().getWithIngredients(absentProductId)!!.product.updatedAt)

        val updatedAddition = database.additionDao().getById(updatedAdditionId)!!
        assertEquals(updatedAdditionId, updatedAddition.id)
        assertEquals("Aggiunta aggiornata", updatedAddition.name)
        assertEquals("AGG ESISTENTE", updatedAddition.printedName)
        assertFalse(updatedAddition.active)
        val additions = database.additionDao().observeAll().first()
        assertEquals(0, additions.single { it.normalizedName == "aggiunta zero" }.priceCents)
        assertNotNull(database.additionDao().getById(absentAdditionId))
        assertEquals(3_000, database.additionDao().getById(unchangedAdditionId)!!.updatedAt)
    }

    @Test
    fun failureMidImportRollsBackEveryWrite() = runBlocking {
        database.additionDao().insert(
            addition(name = "Duplicata", normalizedName = "duplicata"),
        )
        val plan = MenuImportPlan(
            productsToCreate = listOf(
                ProductImportCreate(
                    "Prodotti",
                    2,
                    productValues(
                        name = "Non deve restare",
                        normalizedName = "non deve restare",
                        ingredients = ImportFieldUpdate.Replace(
                            listOf(ValidatedIngredientImport("Rollback", "rollback")),
                        ),
                    ),
                ),
            ),
            productsToUpdate = emptyList(),
            unchangedProducts = emptyList(),
            additionsToCreate = listOf(
                AdditionImportCreate(
                    "Aggiunte",
                    2,
                    additionValues("Duplicata", "duplicata"),
                ),
            ),
            additionsToUpdate = emptyList(),
            unchangedAdditions = emptyList(),
        )

        assertThrows(MenuImportDatabaseException::class.java) {
            runBlocking { committer.commit(plan) }
        }

        assertEquals(emptyList<String>(), database.productDao().observeAllWithIngredients().first().map {
            it.product.normalizedName
        })
        assertEquals(listOf("duplicata"), database.additionDao().observeAll().first().map {
            it.normalizedName
        })
        assertEquals(null, database.ingredientDao().getByNormalizedName("rollback"))
    }

    private suspend fun insertProduct(
        product: ProductEntity,
        ingredients: List<ProductIngredientEntity> = emptyList(),
    ): Long = database.productDao().insertWithIngredients(product, ingredients)

    private fun product(
        name: String,
        normalizedName: String,
        printedName: String? = null,
        active: Boolean = true,
        automaticExtrasPricing: Boolean = true,
        updatedAt: Long = 1_000,
    ): ProductEntity = ProductEntity(
        name = name,
        normalizedName = normalizedName,
        printedName = printedName,
        category = ProductCategory.PIZZA,
        priceCents = 700,
        automaticExtrasPricing = automaticExtrasPricing,
        active = active,
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    private fun addition(
        name: String,
        normalizedName: String,
        printedName: String? = null,
        active: Boolean = true,
        updatedAt: Long = 1_000,
    ): AdditionEntity = AdditionEntity(
        name = name,
        normalizedName = normalizedName,
        printedName = printedName,
        priceCents = 100,
        active = active,
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    private fun ingredient(
        name: String,
        normalizedName: String,
        active: Boolean = true,
    ): IngredientEntity = IngredientEntity(
        name = name,
        normalizedName = normalizedName,
        active = active,
    )

    private fun productValues(
        name: String,
        normalizedName: String,
        category: ProductCategory = ProductCategory.PIZZA,
        printedName: ImportFieldUpdate<String?> = ImportFieldUpdate.Replace(null),
        ingredients: ImportFieldUpdate<List<ValidatedIngredientImport>> =
            ImportFieldUpdate.Replace(emptyList()),
    ): ProductImportValues = ProductImportValues(
        name = name,
        normalizedName = normalizedName,
        price = Money.ofCents(850),
        category = category,
        printedName = printedName,
        ingredients = ingredients,
    )

    private fun additionValues(
        name: String,
        normalizedName: String,
        price: Money = Money.ofCents(250),
        printedName: ImportFieldUpdate<String?> = ImportFieldUpdate.Replace(null),
    ): AdditionImportValues = AdditionImportValues(
        name = name,
        normalizedName = normalizedName,
        price = price,
        printedName = printedName,
    )

    private companion object {
        val IMPORT_TIME: Instant = Instant.parse("2026-09-05T10:00:00Z")
    }
}
