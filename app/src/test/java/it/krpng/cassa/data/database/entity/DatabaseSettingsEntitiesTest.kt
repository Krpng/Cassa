package it.krpng.cassa.data.database.entity

import it.krpng.cassa.domain.model.NumberingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DatabaseSettingsEntitiesTest {
    @Test
    fun `app settings use documented business defaults`() {
        val settings = AppSettingsEntity(updatedAt = 1_000)

        assertEquals(1, settings.id)
        assertEquals(NumberingMode.SEQUENTIAL, settings.numberingMode)
        assertEquals(300, settings.businessDayStartMinutes)
        assertEquals("Europe/Rome", settings.timezoneId)
    }

    @Test
    fun `app settings enforce singleton id`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppSettingsEntity(id = 2, updatedAt = 1_000)
        }
    }

    @Test
    fun `business day start accepts only minutes within a day`() {
        AppSettingsEntity(businessDayStartMinutes = 0, updatedAt = 1_000)
        AppSettingsEntity(businessDayStartMinutes = 1_439, updatedAt = 1_000)

        assertThrows(IllegalArgumentException::class.java) {
            AppSettingsEntity(businessDayStartMinutes = -1, updatedAt = 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppSettingsEntity(businessDayStartMinutes = 1_440, updatedAt = 1_000)
        }
    }

    @Test
    fun `numbering state is keyed by business date with documented defaults`() {
        val state = NumberingStateEntity(
            businessDate = "2026-09-04",
            randomSeed = 42,
            updatedAt = 1_000,
        )

        assertEquals("2026-09-04", state.businessDate)
        assertEquals(1, state.nextSequentialNumber)
        assertEquals(1, state.randomCycle)
        assertEquals(42, state.randomSeed)
        assertEquals(0, state.randomPosition)
    }

    @Test
    fun `sequential and random progress coexist independently`() {
        val state = NumberingStateEntity(
            businessDate = "2026-09-04",
            nextSequentialNumber = 17,
            randomCycle = 2,
            randomSeed = 98_765,
            randomPosition = 321,
            updatedAt = 1_000,
        )

        assertEquals(17, state.nextSequentialNumber)
        assertEquals(2, state.randomCycle)
        assertEquals(98_765, state.randomSeed)
        assertEquals(321, state.randomPosition)
    }

    @Test
    fun `invalid numbering progress is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            numberingState(nextSequentialNumber = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            numberingState(randomCycle = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            numberingState(randomPosition = -1)
        }
    }

    private fun numberingState(
        nextSequentialNumber: Long = 1,
        randomCycle: Int = 1,
        randomPosition: Int = 0,
    ): NumberingStateEntity = NumberingStateEntity(
        businessDate = "2026-09-04",
        nextSequentialNumber = nextSequentialNumber,
        randomCycle = randomCycle,
        randomSeed = 42,
        randomPosition = randomPosition,
        updatedAt = 1_000,
    )
}
