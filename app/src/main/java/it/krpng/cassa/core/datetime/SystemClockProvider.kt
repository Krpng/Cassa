package it.krpng.cassa.core.datetime

import java.time.Instant

object SystemClockProvider : ClockProvider {
    override fun now(): Instant = Instant.now()
}
