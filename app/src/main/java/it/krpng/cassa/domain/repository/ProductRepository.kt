package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Product

interface ProductRepository {
    suspend fun getById(productId: Long): Product?
}
