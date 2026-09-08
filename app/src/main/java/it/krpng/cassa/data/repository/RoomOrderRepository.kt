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
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.pricing.OrderLineMergeCandidate
import it.krpng.cassa.domain.pricing.OrderLineMergePolicy
import it.krpng.cassa.domain.pricing.PricingCalculator
import it.krpng.cassa.domain.repository.CreateDraftResult
import it.krpng.cassa.domain.repository.DeleteDraftResult
import it.krpng.cassa.domain.repository.OrderRepository
import it.krpng.cassa.domain.repository.QuickAddStandardResult
import it.krpng.cassa.domain.repository.ReplaceDraftResult
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
