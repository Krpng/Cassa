package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.IngredientDao
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.domain.model.Ingredient
import it.krpng.cassa.domain.model.ProductIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomIngredientRepositoryTest {
    @Test
    fun `active ingredients remain reactive and are mapped to domain`() = runTest {
        val second = ingredientEntity().copy(
            id = 13,
            name = "Basilico",
            normalizedName = "basilico",
        )
        val dao = FakeIngredientDao(
            activeResults = flowOf(listOf(ingredientEntity()), listOf(ingredientEntity(), second)),
        )

        val emissions = RoomIngredientRepository(dao).observeActive().toList()

        assertEquals(listOf(1, 2), emissions.map { it.size })
        assertEquals("Basilico", emissions.last().last().name)
    }

    @Test
    fun `get maps Room entity to domain`() = runTest {
        val dao = FakeIngredientDao(result = ingredientEntity())
        val repository = RoomIngredientRepository(dao)

        val result = repository.getById(12)

        assertEquals(12L, dao.requestedId)
        assertEquals(ingredient(), result)
    }

    @Test
    fun `create derives normalized identity and lets Room generate id`() = runTest {
        val dao = FakeIngredientDao(insertedId = 31)
        val repository = RoomIngredientRepository(dao)
        val ingredient = ingredient().copy(
            id = 99,
            name = "  Fior   di Lattè ",
            normalizedName = "stale-value",
        )

        val id = repository.create(ingredient)

        assertEquals(31L, id)
        assertEquals(0L, dao.insertedIngredient?.id)
        assertEquals("  Fior   di Lattè ", dao.insertedIngredient?.name)
        assertEquals("fior di latte", dao.insertedIngredient?.normalizedName)
    }

    @Test
    fun `update preserves id active state and refreshes normalized identity`() = runTest {
        val dao = FakeIngredientDao(updatedRows = 1)
        val repository = RoomIngredientRepository(dao)
        val ingredient = ingredient().copy(
            id = 18,
            name = "Olìve   Nere",
            normalizedName = "old-name",
            active = false,
        )

        val updated = repository.update(ingredient)

        assertTrue(updated)
        assertEquals(18L, dao.updatedIngredient?.id)
        assertEquals("olive nere", dao.updatedIngredient?.normalizedName)
        assertFalse(dao.updatedIngredient?.active ?: true)
    }

    @Test
    fun `activate and deactivate are logical updates`() = runTest {
        val dao = FakeIngredientDao(activeUpdatedRows = 1)
        val repository = RoomIngredientRepository(dao)

        assertTrue(repository.activate(12))
        assertTrue(repository.deactivate(12))
        assertEquals(
            listOf(
                ActiveUpdate(ingredientId = 12, active = true),
                ActiveUpdate(ingredientId = 12, active = false),
            ),
            dao.activeUpdates,
        )
    }

    @Test
    fun `missing ingredient is preserved as null or false without creating a row`() = runTest {
        val dao = FakeIngredientDao(updatedRows = 0, activeUpdatedRows = 0)
        val repository = RoomIngredientRepository(dao)

        assertNull(repository.getById(404))
        assertFalse(repository.update(ingredient()))
        assertFalse(repository.activate(404))
        assertFalse(repository.deactivate(404))
        assertNull(dao.insertedIngredient)
    }

    @Test
    fun `replace product ingredients preserves explicit display order`() = runTest {
        val dao = FakeIngredientDao()
        val repository = RoomIngredientRepository(dao)

        repository.replaceProductIngredients(
            productId = 7,
            ingredients = listOf(
                ProductIngredient(ingredient(id = 22, name = "Basilico"), displayOrder = 2),
                ProductIngredient(ingredient(id = 21, name = "Mozzarella"), displayOrder = 1),
            ),
        )

        assertEquals(listOf(7L), dao.deletedProductIds)
        assertEquals(
            listOf(
                ProductIngredientEntity(productId = 7, ingredientId = 22, displayOrder = 2),
                ProductIngredientEntity(productId = 7, ingredientId = 21, displayOrder = 1),
            ),
            dao.insertedProductIngredients,
        )
    }

    @Test
    fun `replace with empty composition only clears existing associations`() = runTest {
        val dao = FakeIngredientDao()
        val repository = RoomIngredientRepository(dao)

        repository.replaceProductIngredients(productId = 7, ingredients = emptyList())

        assertEquals(listOf(7L), dao.deletedProductIds)
        assertTrue(dao.insertedProductIngredients.isEmpty())
    }

    private class FakeIngredientDao(
        private val result: IngredientEntity? = null,
        private val insertedId: Long = 1,
        private val updatedRows: Int = 1,
        private val activeUpdatedRows: Int = 1,
        private val activeResults: Flow<List<IngredientEntity>> = flowOf(emptyList()),
    ) : IngredientDao {
        var requestedId: Long? = null
        var insertedIngredient: IngredientEntity? = null
        var updatedIngredient: IngredientEntity? = null
        val activeUpdates = mutableListOf<ActiveUpdate>()
        val deletedProductIds = mutableListOf<Long>()
        val insertedProductIngredients = mutableListOf<ProductIngredientEntity>()

        override fun observeActive(): Flow<List<IngredientEntity>> = activeResults

        override suspend fun getById(ingredientId: Long): IngredientEntity? {
            requestedId = ingredientId
            return result
        }

        override suspend fun insert(ingredient: IngredientEntity): Long {
            insertedIngredient = ingredient
            return insertedId
        }

        override suspend fun update(ingredient: IngredientEntity): Int {
            updatedIngredient = ingredient
            return updatedRows
        }

        override suspend fun updateActive(ingredientId: Long, active: Boolean): Int {
            activeUpdates += ActiveUpdate(ingredientId, active)
            return activeUpdatedRows
        }

        override suspend fun deleteProductIngredients(productId: Long) {
            deletedProductIds += productId
        }

        override suspend fun insertProductIngredients(
            ingredients: List<ProductIngredientEntity>,
        ) {
            insertedProductIngredients += ingredients
        }
    }

    private data class ActiveUpdate(
        val ingredientId: Long,
        val active: Boolean,
    )

    private fun ingredient(
        id: Long = 12,
        name: String = "Pomodoro",
    ): Ingredient = Ingredient(
        id = id,
        name = name,
        normalizedName = "pomodoro",
        active = true,
    )

    private fun ingredientEntity(): IngredientEntity = IngredientEntity(
        id = 12,
        name = "Pomodoro",
        normalizedName = "pomodoro",
        active = true,
    )
}
