package it.krpng.cassa.domain.order

/**
 * ORD-021 general note persistence normalization.
 * Blank/whitespace-only → null; otherwise external trim only.
 */
object GeneralNoteNormalizer {
    fun normalize(raw: String?): String? = raw?.trim()?.takeUnless { it.isEmpty() }
}
