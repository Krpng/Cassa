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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `repositories preserve missing row as null`() = runTest {
        assertNull(RoomProductRepository(FakeProductDao(null)).getById(1))
        assertNull(RoomOrderRepository(FakeOrderDao(null)).getById("missing"))
    }

    private class FakeProductDao(
        private val result: ProductWithIngredients?,
    ) : ProductDao {
        var requestedId: Long? = null

        override suspend fun getWithIngredients(productId: Long): ProductWithIngredients? {
            requestedId = productId
            return result
        }
    }

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
