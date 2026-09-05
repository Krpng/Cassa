package it.krpng.cassa.data.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.dao.AdditionDao
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.domain.model.Addition
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

class RoomAdditionRepositoryTest {
    @Test
    fun `menu addition Flow includes inactive catalog rows`() = runTest {
        val inactive = additionEntity().copy(active = false)
        val repository = RoomAdditionRepository(
            FakeAdditionDao(activeResults = flowOf(listOf(inactive))),
        )

        val additions = repository.observeAll().toList().single()

        assertEquals(1, additions.size)
        assertFalse(additions.single().active)
    }

    @Test
    fun `active additions remain reactive and are mapped to domain money`() = runTest {
        val second = additionEntity().copy(
            id = 13,
            name = "Olive",
            normalizedName = "olive",
            priceCents = 0,
        )
        val dao = FakeAdditionDao(
            activeResults = flowOf(listOf(additionEntity()), listOf(additionEntity(), second)),
        )

        val emissions = RoomAdditionRepository(dao).observeActive().toList()

        assertEquals(listOf(1, 2), emissions.map { it.size })
        assertEquals(Money.ZERO, emissions.last().last().price)
    }

    @Test
    fun `get maps Room entity to domain without exposing cents`() = runTest {
        val dao = FakeAdditionDao(result = additionEntity())
        val repository = RoomAdditionRepository(dao)

        val result = repository.getById(12)

        assertEquals(12L, dao.requestedId)
        assertEquals("Prosciutto", result?.name)
        assertEquals("PROSCIUTTO", result?.printedName)
        assertEquals(Money.ofCents(200), result?.price)
        assertEquals(Instant.ofEpochMilli(2_000), result?.updatedAt)
    }

    @Test
    fun `create supports free addition and derives normalized identity`() = runTest {
        val dao = FakeAdditionDao(insertedId = 31)
        val repository = RoomAdditionRepository(dao)
        val addition = addition().copy(
            id = 99,
            name = "  Olìve   Nere ",
            normalizedName = "stale-value",
            price = Money.ZERO,
        )

        val id = repository.create(addition)

        assertEquals(31L, id)
        assertEquals(0L, dao.insertedAddition?.id)
        assertEquals("  Olìve   Nere ", dao.insertedAddition?.name)
        assertEquals("olive nere", dao.insertedAddition?.normalizedName)
        assertEquals(0L, dao.insertedAddition?.priceCents)
    }

    @Test
    fun `update preserves id price and refreshes normalized identity`() = runTest {
        val dao = FakeAdditionDao(updatedRows = 1)
        val repository = RoomAdditionRepository(dao)
        val addition = addition().copy(
            id = 18,
            name = "Fior di Lattè",
            normalizedName = "old-name",
            price = Money.ofCents(250),
        )

        val updated = repository.update(addition)

        assertTrue(updated)
        assertEquals(18L, dao.updatedAddition?.id)
        assertEquals("fior di latte", dao.updatedAddition?.normalizedName)
        assertEquals(250L, dao.updatedAddition?.priceCents)
    }

    @Test
    fun `activate and deactivate are logical updates with explicit timestamps`() = runTest {
        val dao = FakeAdditionDao(activeUpdatedRows = 1)
        val repository = RoomAdditionRepository(dao)

        assertTrue(repository.activate(12, Instant.ofEpochMilli(3_000)))
        assertTrue(repository.deactivate(12, Instant.ofEpochMilli(4_000)))
        assertEquals(
            listOf(
                ActiveUpdate(additionId = 12, active = true, updatedAt = 3_000),
                ActiveUpdate(additionId = 12, active = false, updatedAt = 4_000),
            ),
            dao.activeUpdates,
        )
    }

    @Test
    fun `missing addition is preserved as null or false without creating a row`() = runTest {
        val dao = FakeAdditionDao(updatedRows = 0, activeUpdatedRows = 0)
        val repository = RoomAdditionRepository(dao)

        assertNull(repository.getById(404))
        assertFalse(repository.update(addition()))
        assertFalse(repository.activate(404, Instant.EPOCH))
        assertFalse(repository.deactivate(404, Instant.EPOCH))
        assertNull(dao.insertedAddition)
    }

    private class FakeAdditionDao(
        private val result: AdditionEntity? = null,
        private val insertedId: Long = 1,
        private val updatedRows: Int = 1,
        private val activeUpdatedRows: Int = 1,
        private val activeResults: Flow<List<AdditionEntity>> = flowOf(emptyList()),
    ) : AdditionDao {
        var requestedId: Long? = null
        var insertedAddition: AdditionEntity? = null
        var updatedAddition: AdditionEntity? = null
        val activeUpdates = mutableListOf<ActiveUpdate>()

        override fun observeAll(): Flow<List<AdditionEntity>> = activeResults

        override fun observeActive(): Flow<List<AdditionEntity>> = activeResults

        override suspend fun getById(additionId: Long): AdditionEntity? {
            requestedId = additionId
            return result
        }

        override suspend fun insert(addition: AdditionEntity): Long {
            insertedAddition = addition
            return insertedId
        }

        override suspend fun update(addition: AdditionEntity): Int {
            updatedAddition = addition
            return updatedRows
        }

        override suspend fun updateActive(
            additionId: Long,
            active: Boolean,
            updatedAt: Long,
        ): Int {
            activeUpdates += ActiveUpdate(additionId, active, updatedAt)
            return activeUpdatedRows
        }
    }

    private data class ActiveUpdate(
        val additionId: Long,
        val active: Boolean,
        val updatedAt: Long,
    )

    private fun addition(): Addition = additionEntity().toDomain()

    private fun additionEntity(): AdditionEntity = AdditionEntity(
        id = 12,
        name = "Prosciutto",
        normalizedName = "prosciutto",
        printedName = "PROSCIUTTO",
        priceCents = 200,
        active = true,
        createdAt = 1_000,
        updatedAt = 2_000,
    )
}
