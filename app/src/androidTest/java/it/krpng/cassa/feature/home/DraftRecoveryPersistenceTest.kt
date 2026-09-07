package it.krpng.cassa.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.repository.RoomOrderRepository
import it.krpng.cassa.domain.repository.CreateDraftResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRecoveryPersistenceTest {
    private lateinit var context: Context
    private var database: CassaDatabase? = null

    @Before
    fun prepareDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun cleanDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun reopeningDatabaseRecoversDraftChildrenAndManualPrice() = runBlocking {
        database = openDatabase()
        val firstRepository = repository(requireNotNull(database))
        val created = firstRepository.createDraft() as CreateDraftResult.Created
        insertPersistedOrderItem(requireNotNull(database), created.draft.id)
        requireNotNull(database).close()

        database = openDatabase()
        val recovered = repository(requireNotNull(database)).getActiveDraft()

        assertNotNull(recovered)
        assertEquals(created.draft.id, recovered?.id)
        assertEquals(1, recovered?.items?.size)
        assertEquals(Money.ofCents(850), recovered?.items?.single()?.manualUnitPrice)
        assertEquals("Ben cotta", recovered?.items?.single()?.note)
    }

    private fun openDatabase(): CassaDatabase = Room.databaseBuilder(
        context,
        CassaDatabase::class.java,
        DATABASE_NAME,
    ).allowMainThreadQueries().build()

    private fun repository(database: CassaDatabase): RoomOrderRepository =
        RoomOrderRepository(
            orderDao = database.orderDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = FIXED_NOW
            },
        )

    private fun insertPersistedOrderItem(database: CassaDatabase, orderId: String) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO order_items (
                id,
                orderId,
                productId,
                productNameSnapshot,
                productPrintedNameSnapshot,
                categorySnapshot,
                quantity,
                baseUnitPriceCents,
                automaticExtrasTotalCents,
                manualUnitPriceCents,
                finalUnitPriceCents,
                automaticExtrasPricingSnapshot,
                note,
                createdSequence
            ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                "item-id",
                orderId,
                "Margherita",
                "MARGHERITA",
                "PIZZA",
                1,
                700L,
                0L,
                850L,
                850L,
                1,
                "Ben cotta",
                1,
            ),
        )
    }

    private companion object {
        const val DATABASE_NAME = "ord-002-draft-recovery-test.db"
        val FIXED_NOW: Instant = Instant.parse("2026-09-07T10:15:30Z")
    }
}
