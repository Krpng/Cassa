package it.krpng.cassa.data.ods

internal data class ImportedProductFlags(
    val active: Boolean,
    val automaticExtrasPricing: Boolean,
)

internal data class ImportedAdditionFlags(
    val active: Boolean,
)

internal object MenuImportFlagPolicy {
    fun forNewProduct(): ImportedProductFlags = ImportedProductFlags(
        active = true,
        automaticExtrasPricing = true,
    )

    fun forExistingProduct(
        active: Boolean,
        automaticExtrasPricing: Boolean,
    ): ImportedProductFlags = ImportedProductFlags(
        active = active,
        automaticExtrasPricing = automaticExtrasPricing,
    )

    fun forNewAddition(): ImportedAdditionFlags = ImportedAdditionFlags(active = true)

    fun forExistingAddition(active: Boolean): ImportedAdditionFlags =
        ImportedAdditionFlags(active = active)
}
