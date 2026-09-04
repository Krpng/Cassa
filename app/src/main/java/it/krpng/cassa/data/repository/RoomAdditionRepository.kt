package it.krpng.cassa.data.repository

import it.krpng.cassa.core.normalization.TextNormalizer
import it.krpng.cassa.data.database.dao.AdditionDao
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.repository.AdditionRepository
import java.time.Instant

class RoomAdditionRepository(
    private val additionDao: AdditionDao,
) : AdditionRepository {
    override suspend fun getById(additionId: Long): Addition? =
        additionDao.getById(additionId)?.toDomain()

    override suspend fun create(addition: Addition): Long =
        additionDao.insert(addition.toWritableEntity(additionId = 0))

    override suspend fun update(addition: Addition): Boolean =
        additionDao.update(addition.toWritableEntity(additionId = addition.id)) == 1

    override suspend fun activate(additionId: Long, updatedAt: Instant): Boolean =
        updateActive(additionId = additionId, active = true, updatedAt = updatedAt)

    override suspend fun deactivate(additionId: Long, updatedAt: Instant): Boolean =
        updateActive(additionId = additionId, active = false, updatedAt = updatedAt)

    private suspend fun updateActive(
        additionId: Long,
        active: Boolean,
        updatedAt: Instant,
    ): Boolean = additionDao.updateActive(
        additionId = additionId,
        active = active,
        updatedAt = updatedAt.toEpochMilli(),
    ) == 1

    private fun Addition.toWritableEntity(additionId: Long): AdditionEntity =
        toEntity().copy(
            id = additionId,
            normalizedName = TextNormalizer.normalize(name),
        )
}
