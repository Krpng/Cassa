package it.krpng.cassa.data.numbering

import it.krpng.cassa.domain.numbering.NumberingSeedProvider
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Platform seed source for first-need RANDOM initialization.
 * Not used for permutation generation (FREEZE-A uses XorShift32).
 */
@Singleton
class SecureRandomNumberingSeedProvider @Inject constructor() : NumberingSeedProvider {
    private val secureRandom = SecureRandom()

    override fun nextSeed(): Long = secureRandom.nextLong()
}
