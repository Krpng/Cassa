package it.krpng.cassa.data.ods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuImportFlagPolicyTest {
    @Test
    fun `new catalog records start active with automatic extras enabled for products`() {
        val productFlags = MenuImportFlagPolicy.forNewProduct()
        val additionFlags = MenuImportFlagPolicy.forNewAddition()

        assertTrue(productFlags.active)
        assertTrue(productFlags.automaticExtrasPricing)
        assertTrue(additionFlags.active)
    }

    @Test
    fun `existing manual false flags are preserved during import`() {
        val productFlags = MenuImportFlagPolicy.forExistingProduct(
            active = false,
            automaticExtrasPricing = false,
        )
        val additionFlags = MenuImportFlagPolicy.forExistingAddition(active = false)

        assertFalse(productFlags.active)
        assertFalse(productFlags.automaticExtrasPricing)
        assertFalse(additionFlags.active)
    }
}
