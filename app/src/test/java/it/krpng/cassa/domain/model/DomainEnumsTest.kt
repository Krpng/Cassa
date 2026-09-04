package it.krpng.cassa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainEnumsTest {
    @Test
    fun `product categories match the documented v1 domain`() {
        assertEquals(
            listOf("PIZZA", "FRITTURA", "BIBITA"),
            ProductCategory.entries.map(ProductCategory::name),
        )
    }

    @Test
    fun `order statuses contain only draft and accepted`() {
        assertEquals(
            listOf("DRAFT", "ACCEPTED"),
            OrderStatus.entries.map(OrderStatus::name),
        )
    }

    @Test
    fun `numbering modes match the documented settings values`() {
        assertEquals(
            listOf("SEQUENTIAL", "RANDOM"),
            NumberingMode.entries.map(NumberingMode::name),
        )
    }
}
