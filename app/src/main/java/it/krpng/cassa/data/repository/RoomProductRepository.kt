package it.krpng.cassa.data.repository

import it.krpng.cassa.data.database.dao.ProductDao
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.repository.ProductRepository

class RoomProductRepository(
    private val productDao: ProductDao,
) : ProductRepository {
    override suspend fun getById(productId: Long): Product? =
        productDao.getWithIngredients(productId)?.toDomain()
}
