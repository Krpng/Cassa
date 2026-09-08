package it.krpng.cassa.app.navigation

import it.krpng.cassa.feature.order.OrderItemDetailViewModel

object OrderItemDetailDestination {
    private const val BASE_ROUTE = "order_item_detail"

    val routePattern: String =
        "$BASE_ROUTE/{${OrderItemDetailViewModel.ORDER_ID_ARGUMENT}}/" +
            "{${OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT}}"

    fun createRoute(orderId: String, orderItemId: String): String =
        "$BASE_ROUTE/$orderId/$orderItemId"
}
