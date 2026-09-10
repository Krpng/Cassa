package it.krpng.cassa.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException
import it.krpng.cassa.core.datetime.ClockProvider
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.DatabaseTransactionRunner
import it.krpng.cassa.data.database.ImmediateDatabaseTransactionRunner
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.dao.DraftReplacementConflictException
import it.krpng.cassa.data.database.dao.ReplaceDraftDatabaseResult
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.pricing.OrderLineMergeCandidate
import it.krpng.cassa.domain.pricing.OrderLineMergePolicy
import it.krpng.cassa.domain.pricing.PricingCalculator
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.CustomizationQuantityIntent
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
import it.krpng.cassa.domain.repository.UpdateOrderItemResult
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOrderRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val clockProvider: ClockProvider,
    private val transactionRunner: DatabaseTransactionRunner,
) : OrderRepository {
    internal constructor(
        orderDao: OrderDao,
        clockProvider: ClockProvider,
    ) : this(orderDao, clockProvider, ImmediateDatabaseTransactionRunner)

    override suspend fun getById(orderId: String): Order? =
        orderDao.getFullOrder(orderId)?.toDomain()

    override fun observeById(orderId: String): Flow<Order?> =
        orderDao.observeFullOrder(orderId).map { order -> order?.toDomain() }

    override fun observeActiveDraft(): Flow<Order?> =
        orderDao.observeActiveDraft().map { draft -> draft?.toDomain() }

    override suspend fun getActiveDraft(): Order? =
        orderDao.getActiveDraft()?.toDomain()

    override suspend fun createDraft(): CreateDraftResult {
        val draft = newDraft()
        val insertResult = orderDao.insertDraft(draft.toDraftEntity())

        return if (insertResult == INSERT_CONFLICT) {
            CreateDraftResult.AlreadyExists
        } else {
            CreateDraftResult.Created(draft)
        }
    }

    override suspend fun deleteDraft(orderId: String): DeleteDraftResult =
        if (orderDao.deleteDraft(orderId) == 1) {
            DeleteDraftResult.Deleted
        } else {
            DeleteDraftResult.NotFoundOrNotDraft
        }

    override suspend fun replaceDraft(orderId: String): ReplaceDraftResult {
        val replacement = newDraft()
        return try {
            when (orderDao.replaceDraft(orderId, replacement.toDraftEntity())) {
                ReplaceDraftDatabaseResult.Replaced -> ReplaceDraftResult.Created(replacement)
                ReplaceDraftDatabaseResult.OriginalNotFoundOrNotDraft ->
                    ReplaceDraftResult.OriginalNotFoundOrNotDraft
            }
        } catch (_: DraftReplacementConflictException) {
            ReplaceDraftResult.Conflict
        }
    }

    override suspend fun quickAddStandard(
        orderId: String,
        productId: Long,
    ): QuickAddStandardResult = try {
        transactionRunner.runInTransaction {
            val order = orderDao.getFullOrder(orderId)
                ?: return@runInTransaction QuickAddStandardResult.OrderNotFound
            if (order.order.status != OrderStatus.DRAFT || order.order.draftSlot != DRAFT_SLOT) {
                return@runInTransaction QuickAddStandardResult.OrderNotEditable
            }

            val product = orderDao.getActiveProductForQuickAdd(productId)
                ?: return@runInTransaction QuickAddStandardResult.ProductUnavailable
            val incoming = OrderLineMergeCandidate(
                productId = product.id,
                category = product.category,
            )
            val mergeTarget = order.items
                .sortedWith(compareBy({ it.item.createdSequence }, { it.item.id }))
                .firstOrNull { existing ->
                    existing.toMergeCandidate()?.let { candidate ->
                        OrderLineMergePolicy.canMerge(candidate, incoming)
                    } == true
                }

            val result = if (mergeTarget != null) {
                if (mergeTarget.item.quantity == Int.MAX_VALUE) {
                    return@runInTransaction QuickAddStandardResult.LimitReached
                }
                val updatedItem = mergeTarget.item.copy(
                    quantity = mergeTarget.item.quantity + 1,
                )
                if (orderDao.updateOrderItem(updatedItem) != 1) {
                    throw QuickAddWriteConflictException()
                }
                QuickAddStandardResult.Merged(updatedItem.id, updatedItem.quantity)
            } else {
                val maxSequence = order.items.maxOfOrNull { item -> item.item.createdSequence } ?: 0
                if (maxSequence == Int.MAX_VALUE) {
                    return@runInTransaction QuickAddStandardResult.LimitReached
                }
                val item = product.toStandardOrderItem(
                    orderId = orderId,
                    createdSequence = maxSequence + 1,
                )
                orderDao.insertOrderItem(item)
                QuickAddStandardResult.Added(item.id)
            }

            val updatedAt = clockProvider.now().toEpochMilli()
            if (orderDao.updateDraftTimestamp(orderId, updatedAt) != 1) {
                throw QuickAddWriteConflictException()
            }
            result
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: SQLiteException) {
        QuickAddStandardResult.PersistenceFailure
    } catch (_: QuickAddWriteConflictException) {
        QuickAddStandardResult.PersistenceFailure
    }

    override suspend fun updateOrderItem(
        orderId: String,
        orderItemId: String,
        quantity: Int,
        note: String?,
        manualUnitPrice: Money?,
        selectedAdditionIds: List<Long>?,
        selectedRemovalIngredientIds: List<Long>?,
        customizationQuantityIntent: CustomizationQuantityIntent,
    ): UpdateOrderItemResult {
        if (quantity <= 0) return UpdateOrderItemResult.InvalidQuantity

        return try {
            transactionRunner.runInTransaction {
                val order = orderDao.getFullOrder(orderId)
                    ?: return@runInTransaction UpdateOrderItemResult.OrderNotFound
                if (order.order.status != OrderStatus.DRAFT || order.order.draftSlot != DRAFT_SLOT) {
                    return@runInTransaction UpdateOrderItemResult.OrderNotEditable
                }
                val existing = order.items.firstOrNull { item -> item.item.id == orderItemId }
                    ?: return@runInTransaction UpdateOrderItemResult.ItemNotFound

                validateCustomizationQuantityTransition(
                    existing = existing,
                    requestedQuantity = quantity,
                    requestedNote = note,
                    requestedManualUnitPrice = manualUnitPrice,
                    requestedAdditionIds = selectedAdditionIds,
                    requestedRemovalIngredientIds = selectedRemovalIngredientIds,
                    intent = customizationQuantityIntent,
                )?.let { rejection -> return@runInTransaction rejection }

                val additionUpdate = selectedAdditionIds?.let { requestedIds ->
                    prepareAdditionUpdate(
                        existing = existing,
                        requestedAdditionIds = requestedIds,
                    )
                }
                if (additionUpdate is AdditionUpdatePreparation.Rejected) {
                    return@runInTransaction additionUpdate.result
                }
                val preparedAdditions =
                    (additionUpdate as? AdditionUpdatePreparation.Ready)?.update

                val removalUpdate = selectedRemovalIngredientIds?.let { requestedIds ->
                    prepareRemovalUpdate(
                        existing = existing,
                        requestedIngredientIds = requestedIds,
                    )
                }
                if (removalUpdate is RemovalUpdatePreparation.Rejected) {
                    return@runInTransaction removalUpdate.result
                }
                val preparedRemovals =
                    (removalUpdate as? RemovalUpdatePreparation.Ready)?.update

                val pricing = calculateUpdatedPricing(
                    existing = existing,
                    preparedAdditions = preparedAdditions,
                    manualUnitPrice = manualUnitPrice,
                    quantity = quantity,
                )
                val updatedItem = existing.item.copy(
                    quantity = quantity,
                    automaticExtrasTotalCents = pricing.automaticExtrasTotal.cents,
                    manualUnitPriceCents = manualUnitPrice?.cents,
                    finalUnitPriceCents = pricing.finalUnitPrice.cents,
                    note = note?.trim()?.ifEmpty { null },
                )
                preparedAdditions?.let { update ->
                    if (update.relationIdsToDelete.isNotEmpty()) {
                        val deleted = orderDao.deleteOrderItemAdditions(
                            orderItemId = orderItemId,
                            relationIds = update.relationIdsToDelete,
                        )
                        if (deleted != update.relationIdsToDelete.size) {
                            throw OrderItemUpdateConflictException()
                        }
                    }
                    if (update.entitiesToInsert.isNotEmpty()) {
                        orderDao.insertOrderItemAdditions(update.entitiesToInsert)
                    }
                }
                preparedRemovals?.let { update ->
                    if (update.relationIdsToDelete.isNotEmpty()) {
                        val deleted = orderDao.deleteOrderItemRemovals(
                            orderItemId = orderItemId,
                            relationIds = update.relationIdsToDelete,
                        )
                        if (deleted != update.relationIdsToDelete.size) {
                            throw OrderItemUpdateConflictException()
                        }
                    }
                    if (update.entitiesToInsert.isNotEmpty()) {
                        orderDao.insertOrderItemRemovals(update.entitiesToInsert)
                    }
                }
                if (orderDao.updateOrderItem(updatedItem) != 1) {
                    throw OrderItemUpdateConflictException()
                }
                val updatedAt = clockProvider.now().toEpochMilli()
                if (orderDao.updateDraftTimestamp(orderId, updatedAt) != 1) {
                    throw OrderItemUpdateConflictException()
                }
                UpdateOrderItemResult.Updated
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ArithmeticException) {
            UpdateOrderItemResult.AmountOverflow
        } catch (_: SQLiteException) {
            UpdateOrderItemResult.PersistenceFailure
        } catch (_: OrderItemUpdateConflictException) {
            UpdateOrderItemResult.PersistenceFailure
        }
    }

    private suspend fun prepareAdditionUpdate(
        existing: OrderItemWithModifiers,
        requestedAdditionIds: List<Long>,
    ): AdditionUpdatePreparation {
        if (existing.item.categorySnapshot != ProductCategory.PIZZA) {
            return AdditionUpdatePreparation.Rejected(UpdateOrderItemResult.ItemNotPizza)
        }
        if (requestedAdditionIds.size != requestedAdditionIds.distinct().size) {
            return AdditionUpdatePreparation.Rejected(UpdateOrderItemResult.AdditionUnavailable)
        }

        val requestedIds = requestedAdditionIds.toSet()
        val sortedExisting = existing.additions.sortedWith(compareBy({ it.displayOrder }, { it.id }))
        val canonicalByAdditionId = linkedMapOf<Long, OrderItemAdditionEntity>()
        val duplicateRelationIds = mutableListOf<String>()
        sortedExisting.forEach { relation ->
            val additionId = relation.additionId ?: return@forEach
            if (canonicalByAdditionId.putIfAbsent(additionId, relation) != null) {
                duplicateRelationIds += relation.id
            }
        }
        val currentIds = canonicalByAdditionId.keys
        val idsToAdd = requestedAdditionIds.filterNot(currentIds::contains)
        val additionsToAdd = if (idsToAdd.isEmpty()) {
            emptyList()
        } else {
            val activeAdditions = orderDao.getActiveAdditionsByIds(idsToAdd)
            val byId = activeAdditions.associateBy(AdditionEntity::id)
            if (byId.keys != idsToAdd.toSet()) {
                return AdditionUpdatePreparation.Rejected(
                    UpdateOrderItemResult.AdditionUnavailable,
                )
            }
            idsToAdd.map { additionId -> checkNotNull(byId[additionId]) }
        }

        val removedRelationIds = canonicalByAdditionId
            .filterKeys { additionId -> additionId !in requestedIds }
            .values
            .map(OrderItemAdditionEntity::id)
        var nextDisplayOrder = sortedExisting.maxOfOrNull { it.displayOrder } ?: -1
        val entitiesToInsert = additionsToAdd.map { addition ->
            if (nextDisplayOrder == Int.MAX_VALUE) {
                return AdditionUpdatePreparation.Rejected(UpdateOrderItemResult.AmountOverflow)
            }
            nextDisplayOrder += 1
            addition.toOrderItemAddition(
                orderItemId = existing.item.id,
                displayOrder = nextDisplayOrder,
                automaticExtrasPricing = existing.item.automaticExtrasPricingSnapshot,
            )
        }
        val retained = sortedExisting.filter { relation ->
            relation.additionId == null ||
                (relation.additionId in requestedIds && relation.id !in duplicateRelationIds)
        }

        return AdditionUpdatePreparation.Ready(
            AdditionUpdate(
                resultingEntities = retained + entitiesToInsert,
                relationIdsToDelete = duplicateRelationIds + removedRelationIds,
                entitiesToInsert = entitiesToInsert,
            ),
        )
    }

    private suspend fun prepareRemovalUpdate(
        existing: OrderItemWithModifiers,
        requestedIngredientIds: List<Long>,
    ): RemovalUpdatePreparation {
        if (existing.item.categorySnapshot != ProductCategory.PIZZA) {
            return RemovalUpdatePreparation.Rejected(UpdateOrderItemResult.ItemNotPizza)
        }
        if (requestedIngredientIds.size != requestedIngredientIds.distinct().size) {
            return RemovalUpdatePreparation.Rejected(
                UpdateOrderItemResult.IngredientNotRemovable,
            )
        }

        val requestedIds = requestedIngredientIds.toSet()
        val sortedExisting = existing.removals.sortedWith(compareBy({ it.displayOrder }, { it.id }))
        val canonicalByIngredientId = linkedMapOf<Long, OrderItemRemovalEntity>()
        val duplicateRelationIds = mutableListOf<String>()
        sortedExisting.forEach { relation ->
            val ingredientId = relation.ingredientId ?: return@forEach
            if (canonicalByIngredientId.putIfAbsent(ingredientId, relation) != null) {
                duplicateRelationIds += relation.id
            }
        }
        val currentIds = canonicalByIngredientId.keys
        val selectionChanged = requestedIds != currentIds

        val idsToAdd = requestedIngredientIds.filterNot(currentIds::contains)
        val ingredientsToAdd = if (idsToAdd.isEmpty()) {
            emptyList()
        } else {
            val productId = existing.item.productId
                ?: return RemovalUpdatePreparation.Rejected(
                    UpdateOrderItemResult.IngredientNotRemovable,
                )
            val productIngredients = orderDao.getProductIngredientsByIds(productId, idsToAdd)
            val byId = productIngredients.associateBy(IngredientEntity::id)
            if (byId.keys != idsToAdd.toSet()) {
                return RemovalUpdatePreparation.Rejected(
                    UpdateOrderItemResult.IngredientNotRemovable,
                )
            }
            idsToAdd.map { ingredientId -> checkNotNull(byId[ingredientId]) }
        }

        val removedRelationIds = canonicalByIngredientId
            .filterKeys { ingredientId -> ingredientId !in requestedIds }
            .values
            .map(OrderItemRemovalEntity::id)
        var nextDisplayOrder = sortedExisting.maxOfOrNull { it.displayOrder } ?: -1
        val entitiesToInsert = ingredientsToAdd.map { ingredient ->
            if (nextDisplayOrder == Int.MAX_VALUE) {
                return RemovalUpdatePreparation.Rejected(UpdateOrderItemResult.AmountOverflow)
            }
            nextDisplayOrder += 1
            ingredient.toOrderItemRemoval(
                orderItemId = existing.item.id,
                displayOrder = nextDisplayOrder,
            )
        }

        return RemovalUpdatePreparation.Ready(
            RemovalUpdate(
                relationIdsToDelete = duplicateRelationIds + removedRelationIds,
                entitiesToInsert = entitiesToInsert,
            ),
        )
    }

    private fun calculateUpdatedPricing(
        existing: OrderItemWithModifiers,
        preparedAdditions: AdditionUpdate?,
        manualUnitPrice: Money?,
        quantity: Int,
    ) = if (preparedAdditions == null) {
        PricingCalculator.calculate(
            baseUnitPrice = Money.ofCents(existing.item.baseUnitPriceCents),
            additionPrices = listOf(Money.ofCents(existing.item.automaticExtrasTotalCents)),
            automaticExtrasPricing = true,
            manualUnitPrice = manualUnitPrice,
            quantity = quantity,
        )
    } else {
        PricingCalculator.calculate(
            baseUnitPrice = Money.ofCents(existing.item.baseUnitPriceCents),
            additionPrices = preparedAdditions.resultingEntities.map { relation ->
                Money.ofCents(relation.listedPriceCents)
            },
            automaticExtrasPricing = existing.item.automaticExtrasPricingSnapshot,
            manualUnitPrice = manualUnitPrice,
            quantity = quantity,
        )
    }

    private fun validateCustomizationQuantityTransition(
        existing: OrderItemWithModifiers,
        requestedQuantity: Int,
        requestedNote: String?,
        requestedManualUnitPrice: Money?,
        requestedAdditionIds: List<Long>?,
        requestedRemovalIngredientIds: List<Long>?,
        intent: CustomizationQuantityIntent,
    ): UpdateOrderItemResult? {
        if (existing.item.categorySnapshot != ProductCategory.PIZZA) return null

        val existingAdditionIds = existing.additions.mapNotNull { it.additionId }.toSet()
        val existingRemovalIds = existing.removals.mapNotNull { it.ingredientId }.toSet()
        val resultingAdditionIds = requestedAdditionIds?.toSet() ?: existingAdditionIds
        val resultingRemovalIds = requestedRemovalIngredientIds?.toSet() ?: existingRemovalIds
        val modifierSelectionChanged =
            resultingAdditionIds != existingAdditionIds || resultingRemovalIds != existingRemovalIds
        val existingCustomization =
            existingAdditionIds.isNotEmpty() ||
                existingRemovalIds.isNotEmpty() ||
                !existing.item.note.isNullOrBlank() ||
                existing.item.manualUnitPriceCents != null
        val resultingCustomization =
            resultingAdditionIds.isNotEmpty() ||
                resultingRemovalIds.isNotEmpty() ||
                !requestedNote.isNullOrBlank() ||
                requestedManualUnitPrice != null

        if (
            existing.item.quantity > 1 &&
            requestedQuantity > 1 &&
            (modifierSelectionChanged || (!existingCustomization && resultingCustomization))
        ) {
            return UpdateOrderItemResult.AmbiguousPizzaQuantity
        }
        if (
            requestedQuantity > existing.item.quantity &&
            resultingCustomization &&
            intent != CustomizationQuantityIntent.APPLY_TO_ALL_UNITS_CONFIRMED
        ) {
            return UpdateOrderItemResult.AmbiguousPizzaQuantity
        }
        return null
    }

    private fun AdditionEntity.toOrderItemAddition(
        orderItemId: String,
        displayOrder: Int,
        automaticExtrasPricing: Boolean,
    ): OrderItemAdditionEntity = OrderItemAdditionEntity(
        id = UUID.randomUUID().toString(),
        orderItemId = orderItemId,
        additionId = id,
        additionNameSnapshot = name,
        additionPrintedNameSnapshot = printedName ?: name,
        listedPriceCents = priceCents,
        chargedPriceCents = PricingCalculator.chargedAdditionPrice(
            listedPrice = Money.ofCents(priceCents),
            automaticExtrasPricing = automaticExtrasPricing,
        ).cents,
        displayOrder = displayOrder,
    )

    private fun IngredientEntity.toOrderItemRemoval(
        orderItemId: String,
        displayOrder: Int,
    ): OrderItemRemovalEntity = OrderItemRemovalEntity(
        id = UUID.randomUUID().toString(),
        orderItemId = orderItemId,
        ingredientId = id,
        ingredientNameSnapshot = name,
        displayOrder = displayOrder,
    )

    private fun newDraft(): Order {
        val now = clockProvider.now()
        return Order(
            id = UUID.randomUUID().toString(),
            status = OrderStatus.DRAFT,
            displayNumber = null,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = now,
            updatedAt = now,
            acceptedAt = null,
            total = Money.ZERO,
            generalNote = null,
            sourceOrderId = null,
            items = emptyList(),
        )
    }

    private fun Order.toDraftEntity(): OrderEntity = toDatabaseModel().order

    private fun OrderItemWithModifiers.toMergeCandidate(): OrderLineMergeCandidate? =
        item.productId?.let { productId ->
            OrderLineMergeCandidate(
                productId = productId,
                category = item.categorySnapshot,
                hasAdditions = additions.isNotEmpty(),
                hasRemovals = removals.isNotEmpty(),
                note = item.note,
                manualUnitPrice = item.manualUnitPriceCents?.let(Money::ofCents),
            )
        }

    private fun ProductEntity.toStandardOrderItem(
        orderId: String,
        createdSequence: Int,
    ): OrderItemEntity {
        val basePrice = Money.ofCents(priceCents)
        val pricing = PricingCalculator.calculate(
            baseUnitPrice = basePrice,
            additionPrices = emptyList(),
            automaticExtrasPricing = automaticExtrasPricing,
            manualUnitPrice = null,
            quantity = 1,
        )
        return OrderItemEntity(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            productId = id,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printedName ?: name,
            categorySnapshot = category,
            quantity = 1,
            baseUnitPriceCents = basePrice.cents,
            automaticExtrasTotalCents = pricing.automaticExtrasTotal.cents,
            manualUnitPriceCents = null,
            finalUnitPriceCents = pricing.finalUnitPrice.cents,
            automaticExtrasPricingSnapshot = automaticExtrasPricing,
            note = null,
            createdSequence = createdSequence,
        )
    }

    private companion object {
        const val DRAFT_SLOT = 1
        const val INSERT_CONFLICT = -1L
    }
}

private class QuickAddWriteConflictException : IllegalStateException()

private class OrderItemUpdateConflictException : IllegalStateException()

private sealed interface AdditionUpdatePreparation {
    data class Ready(val update: AdditionUpdate) : AdditionUpdatePreparation

    data class Rejected(val result: UpdateOrderItemResult) : AdditionUpdatePreparation
}

private data class AdditionUpdate(
    val resultingEntities: List<OrderItemAdditionEntity>,
    val relationIdsToDelete: List<String>,
    val entitiesToInsert: List<OrderItemAdditionEntity>,
)

private sealed interface RemovalUpdatePreparation {
    data class Ready(val update: RemovalUpdate) : RemovalUpdatePreparation

    data class Rejected(val result: UpdateOrderItemResult) : RemovalUpdatePreparation
}

private data class RemovalUpdate(
    val relationIdsToDelete: List<String>,
    val entitiesToInsert: List<OrderItemRemovalEntity>,
)
