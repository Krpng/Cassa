package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.dao.ProductDao
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderWithItems
import it.krpng.cassa.data.database.relation.ProductWithIngredients
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.ProductIngredient
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRepositoriesTest {
    @Test
    fun `menu product Flow includes inactive catalog rows`() = runTest {
        val inactive = productWithIngredients().copy(
            product = productWithIngredients().product.copy(active = false),
        )
        val repository = RoomProductRepository(
            FakeProductDao(activeResults = flowOf(listOf(inactive))),
        )

        val products = repository.observeAll().toList().single()

        assertEquals(1, products.size)
        assertFalse(products.single().active)
    }

    @Test
    fun `active products remain reactive and are mapped with ingredients`() = runTest {
        val first = productWithIngredients()
        val second = productWithIngredients().copy(
            product = productWithIngredients().product.copy(
                id = 43,
                name = "Marinara",
                normalizedName = "marinara",
            ),
        )
        val dao = FakeProductDao(
            activeResults = flowOf(listOf(first), listOf(first, second)),
        )

        val emissions = RoomProductRepository(dao).observeActive().toList()

        assertEquals(
            listOf(listOf("Margherita"), listOf("Margherita", "Marinara")),
            emissions.map { products -> products.map { it.name } },
        )
    }

    @Test
    fun `product repository delegates to DAO and never exposes Room model`() = runTest {
        val dao = FakeProductDao(productWithIngredients())
        val repository = RoomProductRepository(dao)

        val result = repository.getById(42)

        assertEquals(42L, dao.requestedId)
        assertEquals("Margherita", result?.name)
    }

    @Test
    fun `order repository delegates to full aggregate query`() = runTest {
        val dao = FakeOrderDao(fullOrder())
        val repository = RoomOrderRepository(dao)

        val result = repository.getById("order-id")

        assertEquals("order-id", dao.requestedFullOrderId)
        assertEquals("order-id", result?.id)
    }

    @Test
    fun `create product uses generated id and derives normalized identity from name`() = runTest {
        val dao = FakeProductDao(insertedId = 73)
        val repository = RoomProductRepository(dao)
        val product = productWithIngredients().toDomain().copy(
            id = 99,
            name = "  PÌZZA   Margherita  ",
            normalizedName = "stale-value",
        )

        val id = repository.create(product)

        assertEquals(73L, id)
        assertEquals(0L, dao.insertedProduct?.id)
        assertEquals("  PÌZZA   Margherita  ", dao.insertedProduct?.name)
        assertEquals("pizza margherita", dao.insertedProduct?.normalizedName)
    }

    @Test
    fun `create product persists ordered ingredient composition with generated id`() = runTest {
        val dao = FakeProductDao(insertedId = 73)
        val repository = RoomProductRepository(dao)
        val product = productWithIngredients().toDomain().copy(
            ingredients = listOf(
                ProductIngredient(ingredient(id = 8, name = "Basilico"), displayOrder = 0),
                ProductIngredient(ingredient(id = 5, name = "Pomodoro"), displayOrder = 1),
            ),
        )

        repository.create(product)

        assertEquals(listOf(73L), dao.deletedProductIngredientIds)
        assertEquals(
            listOf(
                ProductIngredientEntity(73, 8, 0),
                ProductIngredientEntity(73, 5, 1),
            ),
            dao.insertedProductIngredients,
        )
    }

    @Test
    fun `update product preserves id and refreshes normalized identity`() = runTest {
        val dao = FakeProductDao(updatedRows = 1)
        val repository = RoomProductRepository(dao)
        val product = productWithIngredients().toDomain().copy(
            id = 42,
            name = "Calzòne",
            normalizedName = "old-name",
        )

        val updated = repository.update(product)

        assertTrue(updated)
        assertEquals(42L, dao.updatedProduct?.id)
        assertEquals("calzone", dao.updatedProduct?.normalizedName)
        assertEquals(listOf(42L), dao.deletedProductIngredientIds)
    }

    @Test
    fun `activate and deactivate are logical updates with explicit timestamps`() = runTest {
        val dao = FakeProductDao(activeUpdatedRows = 1)
        val repository = RoomProductRepository(dao)
        val activatedAt = Instant.ofEpochMilli(3_000)
        val deactivatedAt = Instant.ofEpochMilli(4_000)

        assertTrue(repository.activate(productId = 42, updatedAt = activatedAt))
        assertTrue(repository.deactivate(productId = 42, updatedAt = deactivatedAt))

        assertEquals(
            listOf(
                ActiveUpdate(productId = 42, active = true, updatedAt = 3_000),
                ActiveUpdate(productId = 42, active = false, updatedAt = 4_000),
            ),
            dao.activeUpdates,
        )
    }

    @Test
    fun `update operations report missing product without inventing an insert`() = runTest {
        val dao = FakeProductDao(updatedRows = 0, activeUpdatedRows = 0)
        val repository = RoomProductRepository(dao)
        val product = productWithIngredients().toDomain()

        assertFalse(repository.update(product))
        assertFalse(repository.activate(product.id, Instant.EPOCH))
        assertFalse(repository.deactivate(product.id, Instant.EPOCH))
        assertNull(dao.insertedProduct)
        assertTrue(dao.deletedProductIngredientIds.isEmpty())
    }

    @Test
    fun `repositories preserve missing row as null`() = runTest {
        assertNull(RoomProductRepository(FakeProductDao(null)).getById(1))
        assertNull(RoomOrderRepository(FakeOrderDao(null)).getById("missing"))
    }

    private class FakeProductDao(
        private val result: ProductWithIngredients? = null,
        private val insertedId: Long = 1,
        private val updatedRows: Int = 1,
        private val activeUpdatedRows: Int = 1,
        private val activeResults: Flow<List<ProductWithIngredients>> = flowOf(emptyList()),
    ) : ProductDao {
        var requestedId: Long? = null
        var insertedProduct: ProductEntity? = null
        var updatedProduct: ProductEntity? = null
        val activeUpdates = mutableListOf<ActiveUpdate>()
        val deletedProductIngredientIds = mutableListOf<Long>()
        val insertedProductIngredients = mutableListOf<ProductIngredientEntity>()

        override fun observeAllWithIngredients(): Flow<List<ProductWithIngredients>> =
            activeResults

        override fun observeActiveWithIngredients(): Flow<List<ProductWithIngredients>> =
            activeResults

        override suspend fun getWithIngredients(productId: Long): ProductWithIngredients? {
            requestedId = productId
            return result
        }

        override suspend fun insert(product: ProductEntity): Long {
            insertedProduct = product
            return insertedId
        }

        override suspend fun update(product: ProductEntity): Int {
            updatedProduct = product
            return updatedRows
        }

        override suspend fun deleteProductIngredients(productId: Long) {
            deletedProductIngredientIds += productId
        }

        override suspend fun insertProductIngredients(
            ingredients: List<ProductIngredientEntity>,
        ) {
            insertedProductIngredients += ingredients
        }

        override suspend fun updateActive(
            productId: Long,
            active: Boolean,
            updatedAt: Long,
        ): Int {
            activeUpdates += ActiveUpdate(productId, active, updatedAt)
            return activeUpdatedRows
        }
    }

    private data class ActiveUpdate(
        val productId: Long,
        val active: Boolean,
        val updatedAt: Long,
    )

    private class FakeOrderDao(
        private val result: FullOrder?,
    ) : OrderDao {
        var requestedFullOrderId: String? = null

        override suspend fun getWithItems(orderId: String): OrderWithItems? = null

        override suspend fun getFullOrder(orderId: String): FullOrder? {
            requestedFullOrderId = orderId
            return result
        }
    }

    private fun productWithIngredients(): ProductWithIngredients = ProductWithIngredients(
        product = ProductEntity(
            id = 42,
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
        ingredients = emptyList(),
    )

    private fun ingredient(id: Long, name: String): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        active = true,
    )

    private fun fullOrder(): FullOrder = FullOrder(
        order = OrderEntity(
            id = "order-id",
            status = OrderStatus.DRAFT,
            draftSlot = 1,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = 1_000,
            updatedAt = 1_000,
            acceptedAt = null,
            totalCents = 0,
            generalNote = null,
            sourceOrderId = null,
        ),
        items = emptyList(),
    )
}
