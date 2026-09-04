package it.krpng.cassa.domain.repository

import it.krpng.cassa.domain.model.Addition
import java.time.Instant

interface AdditionRepository {
    suspend fun getById(additionId: Long): Addition?

    suspend fun create(addition: Addition): Long

    suspend fun update(addition: Addition): Boolean

    suspend fun activate(additionId: Long, updatedAt: Instant): Boolean

    suspend fun deactivate(additionId: Long, updatedAt: Instant): Boolean
}
