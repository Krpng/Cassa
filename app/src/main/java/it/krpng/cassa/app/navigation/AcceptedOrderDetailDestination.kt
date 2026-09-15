package it.krpng.cassa.app.navigation

import it.krpng.cassa.feature.accepteddetail.AcceptedOrderDetailViewModel

object AcceptedOrderDetailDestination {
    private const val BASE_ROUTE = "accepted_order_detail"

    val routePattern: String =
        "$BASE_ROUTE/{${AcceptedOrderDetailViewModel.ORDER_ID_ARGUMENT}}"

    fun createRoute(orderId: String): String = "$BASE_ROUTE/$orderId"
}
