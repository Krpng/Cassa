package it.krpng.cassa.data.repository

import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.data.database.dao.ProductDao
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity
import it.krpng.cassa.domain.model.Product
import it.krpng.cassa.domain.repository.ProductRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProductRepository @Inject constructor(
    private val productDao: ProductDao,
) : ProductRepository {
    override fun observeAll(): Flow<List<Product>> =
        productDao.observeAllWithIngredients().map { products ->
            products.map { it.toDomain() }
        }

    override fun observeActive(): Flow<List<Product>> =
        productDao.observeActiveWithIngredients().map { products ->
            products.map { it.toDomain() }
        }

    override suspend fun getById(productId: Long): Product? =
        productDao.getWithIngredients(productId)?.toDomain()

    override suspend fun create(product: Product): Long = productDao.insertWithIngredients(
        product = product.toWritableEntity(productId = 0),
        ingredients = product.toWritableIngredients(productId = 0),
    )

    override suspend fun update(product: Product): Boolean =
        productDao.updateWithIngredients(
            product = product.toWritableEntity(productId = product.id),
            ingredients = product.toWritableIngredients(productId = product.id),
        ) == 1

    override suspend fun activate(productId: Long, updatedAt: Instant): Boolean =
        updateActive(productId = productId, active = true, updatedAt = updatedAt)

    override suspend fun deactivate(productId: Long, updatedAt: Instant): Boolean =
        updateActive(productId = productId, active = false, updatedAt = updatedAt)

    private suspend fun updateActive(
        productId: Long,
        active: Boolean,
        updatedAt: Instant,
    ): Boolean = productDao.updateActive(
        productId = productId,
        active = active,
        updatedAt = updatedAt.toEpochMilli(),
    ) == 1

    private fun Product.toWritableEntity(productId: Long): ProductEntity =
        toDatabaseModel().product.copy(
            id = productId,
            normalizedName = TextNormalizer.normalize(name),
        )

    private fun Product.toWritableIngredients(productId: Long): List<ProductIngredientEntity> =
        ingredients.map { productIngredient ->
            ProductIngredientEntity(
                productId = productId,
                ingredientId = productIngredient.ingredient.id,
                displayOrder = productIngredient.displayOrder,
            )
        }
}
