package it.krpng.cassa.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.repository.UpdateNumberingModeResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsModeDraftPreservationTest {
    private lateinit var database: CassaDatabase
    private lateinit var settingsRepository: RoomSettingsRepository
    private val fixedNow = Instant.parse("2026-09-14T17:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CassaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = RoomSettingsRepository(
            appSettingsDao = database.appSettingsDao(),
            clockProvider = object : ClockProvider {
                override fun now(): Instant = fixedNow
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun numT030DraftUnchangedByNumberingModeChange() = runBlocking {
        val draft = OrderEntity(
            id = "draft-1",
            status = OrderStatus.DRAFT,
            draftSlot = 1,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            acceptedAt = null,
            totalCents = 1_250L,
            generalNote = "nota",
            sourceOrderId = null,
            createdAt = 10L,
            updatedAt = 20L,
        )
        database.orderDao().insertDraft(draft)

        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.RANDOM),
        )
        assertSame(
            UpdateNumberingModeResult.Updated,
            settingsRepository.updateNumberingMode(NumberingMode.SEQUENTIAL),
        )

        val after = requireNotNull(database.orderDao().getFullOrder("draft-1"))
        assertEquals(draft.id, after.order.id)
        assertEquals(draft.status, after.order.status)
        assertEquals(draft.totalCents, after.order.totalCents)
        assertEquals(draft.generalNote, after.order.generalNote)
        assertEquals(draft.updatedAt, after.order.updatedAt)
        assertEquals(draft.displayNumber, after.order.displayNumber)
        assertEquals(0, after.items.size)
    }
}
