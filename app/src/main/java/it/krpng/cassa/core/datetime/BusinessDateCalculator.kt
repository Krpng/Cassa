package it.krpng.cassa.core.datetime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object BusinessDateCalculator {
    fun calculate(
        instant: Instant,
        zoneId: ZoneId,
        businessDayStartMinutes: Int,
    ): LocalDate {
        require(businessDayStartMinutes in MINUTES_PER_DAY_RANGE) {
            "Business day start minutes must be between 0 and 1439"
        }

        val localDateTime = instant.atZone(zoneId)
        val businessDayStart = LocalTime.ofSecondOfDay(
            businessDayStartMinutes.toLong() * SECONDS_PER_MINUTE,
        )

        return if (localDateTime.toLocalTime().isBefore(businessDayStart)) {
            localDateTime.toLocalDate().minusDays(1)
        } else {
            localDateTime.toLocalDate()
        }
    }

    private val MINUTES_PER_DAY_RANGE = 0 until 24 * 60
    private const val SECONDS_PER_MINUTE = 60L
}
