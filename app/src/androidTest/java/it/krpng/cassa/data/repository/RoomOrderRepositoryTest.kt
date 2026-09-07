package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.dao.DraftReplacementConflictException
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOrderRepositoryTest {
    private lateinit var database: CassaDatabase
    private lateinit var repository: RoomOrderRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = FIXED_NOW
            },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createGetObserveAndDeleteDraftUseTheRealSingleSlot() = runBlocking {
        val firstResult = repository.createDraft()
        assertTrue(firstResult is CreateDraftResult.Created)
        val draft = (firstResult as CreateDraftResult.Created).draft

        assertEquals(draft.id, repository.getActiveDraft()?.id)
        assertEquals(draft.id, repository.observeActiveDraft().first()?.id)
        assertSame(CreateDraftResult.AlreadyExists, repository.createDraft())
        assertEquals(listOf(draft.id), allOrderIds())

        assertSame(DeleteDraftResult.Deleted, repository.deleteDraft(draft.id))
        assertNull(repository.getActiveDraft())
        assertNull(repository.observeActiveDraft().first())
    }

    @Test
    fun deleteDraftCannotDeleteAcceptedOrder() = runBlocking {
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)

        assertSame(
            DeleteDraftResult.NotFoundOrNotDraft,
            repository.deleteDraft(accepted.id),
        )
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
    }

    @Test
    fun replaceDraftIsAtomicAndLeavesAcceptedOrdersUntouched() = runBlocking {
        val original = (repository.createDraft() as CreateDraftResult.Created).draft
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)

        val result = repository.replaceDraft(original.id)

        assertTrue(result is ReplaceDraftResult.Created)
        val replacement = (result as ReplaceDraftResult.Created).draft
        assertNull(repository.getById(original.id))
        assertEquals(replacement.id, repository.getActiveDraft()?.id)
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
        assertEquals(listOf(accepted.id, replacement.id).sorted(), allOrderIds().sorted())
    }

    @Test
    fun failedReplacementInsertRollsBackTheDraftDeletion() = runBlocking {
        val original = (repository.createDraft() as CreateDraftResult.Created).draft
        val accepted = acceptedOrder()
        database.orderDao().insertDraft(accepted)
        val collidingReplacement = accepted.copy(
            status = OrderStatus.DRAFT,
            draftSlot = 1,
            displayNumber = null,
            businessDate = null,
            acceptedAt = null,
        )

        try {
            database.orderDao().replaceDraft(original.id, collidingReplacement)
            throw AssertionError("Expected replacement conflict")
        } catch (_: DraftReplacementConflictException) {
            // Expected: Room rolls the transaction back when the replacement insert fails.
        }

        assertEquals(original.id, repository.getActiveDraft()?.id)
        assertEquals(original.id, repository.getById(original.id)?.id)
        assertEquals(accepted.id, repository.getById(accepted.id)?.id)
    }

    private suspend fun allOrderIds(): List<String> = database.query(
        "SELECT id FROM orders ORDER BY id",
        emptyArray(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(0))
            }
        }
    }

    private fun acceptedOrder(): OrderEntity = OrderEntity(
        id = "accepted-id",
        status = OrderStatus.ACCEPTED,
        draftSlot = null,
        displayNumber = "001",
        numberingMode = null,
        numberingCycle = null,
        businessDate = "2026-09-07",
        createdAt = FIXED_NOW.toEpochMilli(),
        updatedAt = FIXED_NOW.toEpochMilli(),
        acceptedAt = FIXED_NOW.toEpochMilli(),
        totalCents = 0,
        generalNote = null,
        sourceOrderId = null,
    )

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
    }
}
