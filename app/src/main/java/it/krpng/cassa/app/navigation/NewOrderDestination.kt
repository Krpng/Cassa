package it.krpng.cassa.app.navigation

import it.krpng.cassa.feature.order.NewOrderViewModel

object NewOrderDestination {
    private const val BASE_ROUTE = "new_order"

    val routePattern: String =
        "$BASE_ROUTE/{${NewOrderViewModel.DRAFT_ID_ARGUMENT}}"

    fun createRoute(draftId: String): String = "$BASE_ROUTE/$draftId"
}
