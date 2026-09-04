package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.dao.ProductDao
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderWithItems
import it.krpng.cassa.data.database.relation.ProductWithIngredients
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRepositoriesTest {
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
    ) : ProductDao {
        var requestedId: Long? = null
        var insertedProduct: ProductEntity? = null
        var updatedProduct: ProductEntity? = null
        val activeUpdates = mutableListOf<ActiveUpdate>()

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
