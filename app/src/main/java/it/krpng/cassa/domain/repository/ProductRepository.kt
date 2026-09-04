package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Product
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeActive(): Flow<List<Product>>

    suspend fun getById(productId: Long): Product?

    suspend fun create(product: Product): Long

    suspend fun update(product: Product): Boolean

    suspend fun activate(productId: Long, updatedAt: Instant): Boolean

    suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean
}
