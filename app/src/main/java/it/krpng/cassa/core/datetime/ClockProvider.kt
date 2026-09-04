package it.krpng.cassa.core.datetime

import java.time.Instant

interface ClockProvider {
    fun now(): Instant
}
