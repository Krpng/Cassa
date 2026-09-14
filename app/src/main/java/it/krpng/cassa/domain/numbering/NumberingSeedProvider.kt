package it.krpng.cassa.domain.numbering

/**
 * Injectable entropy for the first RANDOM seed only.
 * Domain must not depend on Android or concrete PRNG APIs.
 */
fun interface NumberingSeedProvider {
    fun nextSeed(): Long
}
