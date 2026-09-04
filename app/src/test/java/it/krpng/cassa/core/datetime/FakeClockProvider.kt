package it.krpng.cassa.core.datetime

import java.time.Instant

class FakeClockProvider(
    var currentInstant: Instant,
) : ClockProvider {
    override fun now(): Instant = currentInstant
}
