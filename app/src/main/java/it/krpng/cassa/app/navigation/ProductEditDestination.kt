package it.krpng.cassa.app.navigation

import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.feature.menu.ProductEditViewModel

object ProductEditDestination {
    private const val BASE_ROUTE = "product_edit"

    const val NEW_PRODUCT_ID = -1L

    val routePattern: String =
        "$BASE_ROUTE?${ProductEditViewModel.PRODUCT_ID_ARGUMENT}={${ProductEditViewModel.PRODUCT_ID_ARGUMENT}}" +
            "&${ProductEditViewModel.CATEGORY_ARGUMENT}={${ProductEditViewModel.CATEGORY_ARGUMENT}}"

    fun createRoute(
        productId: Long = NEW_PRODUCT_ID,
        category: ProductCategory = ProductCategory.PIZZA,
    ): String = "$BASE_ROUTE?${ProductEditViewModel.PRODUCT_ID_ARGUMENT}=$productId" +
        "&${ProductEditViewModel.CATEGORY_ARGUMENT}=${category.name}"
}
