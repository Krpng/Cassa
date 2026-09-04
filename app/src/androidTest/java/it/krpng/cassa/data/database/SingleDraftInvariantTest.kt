package it.krpng.cassa.data.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.domain.model.OrderStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleDraftInvariantTest {
    private lateinit var database: DraftInvariantTestDatabase
    private lateinit var orderDao: DraftInvariantTestOrderDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            DraftInvariantTestDatabase::class.java,
        ).build()
        orderDao = database.orderDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun secondDraftIsBlockedByUniqueDraftSlot() {
        orderDao.insert(order(id = "draft-1", status = OrderStatus.DRAFT, draftSlot = 1))

        assertThrows(SQLiteConstraintException::class.java) {
            orderDao.insert(order(id = "draft-2", status = OrderStatus.DRAFT, draftSlot = 1))
        }

        assertEquals(listOf("draft-1"), orderDao.getAll().map(OrderEntity::id))
    }

    @Test
    fun acceptedOrdersCanShareNullDraftSlot() {
        orderDao.insert(order(id = "accepted-1", status = OrderStatus.ACCEPTED, draftSlot = null))
        orderDao.insert(order(id = "accepted-2", status = OrderStatus.ACCEPTED, draftSlot = null))

        assertEquals(listOf("accepted-1", "accepted-2"), orderDao.getAll().map(OrderEntity::id))
    }

    private fun order(
        id: String,
        status: OrderStatus,
        draftSlot: Int?,
    ): OrderEntity = OrderEntity(
        id = id,
        status = status,
        draftSlot = draftSlot,
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
    )
}

@Dao
internal interface DraftInvariantTestOrderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(order: OrderEntity)

    @Query("SELECT * FROM orders ORDER BY id")
    fun getAll(): List<OrderEntity>
}

@Database(
    entities = [OrderEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class DraftInvariantTestDatabase : RoomDatabase() {
    abstract fun orderDao(): DraftInvariantTestOrderDao
}
