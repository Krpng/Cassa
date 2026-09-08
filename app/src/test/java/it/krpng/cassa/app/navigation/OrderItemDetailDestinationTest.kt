package it.krpng.cassa.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderItemDetailDestinationTest {
    @Test
    fun `route carries both stable order and item identities`() {
        assertEquals(
            "order_item_detail/draft-id/item-id",
            OrderItemDetailDestination.createRoute("draft-id", "item-id"),
        )
    }
}
