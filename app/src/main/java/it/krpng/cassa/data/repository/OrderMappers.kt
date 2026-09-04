package it.krpng.cassa.data.repository

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.relation.FullOrder
import it.krpng.cassa.data.database.relation.OrderItemWithModifiers
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import java.time.Instant
import java.time.LocalDate

internal fun FullOrder.toDomain(): Order = Order(
    id = order.id,
    status = order.status,
    displayNumber = order.displayNumber,
    numberingMode = order.numberingMode,
    numberingCycle = order.numberingCycle,
    businessDate = order.businessDate?.let(LocalDate::parse),
    createdAt = Instant.ofEpochMilli(order.createdAt),
    updatedAt = Instant.ofEpochMilli(order.updatedAt),
    acceptedAt = order.acceptedAt?.let(Instant::ofEpochMilli),
    total = Money.ofCents(order.totalCents),
    generalNote = order.generalNote,
    sourceOrderId = order.sourceOrderId,
    items = items
        .sortedWith(compareBy({ it.item.createdSequence }, { it.item.id }))
        .map(OrderItemWithModifiers::toDomain),
)

internal fun Order.toDatabaseModel(): FullOrder = FullOrder(
    order = OrderEntity(
        id = id,
        status = status,
        draftSlot = if (status == OrderStatus.DRAFT) DRAFT_SLOT else null,
        displayNumber = displayNumber,
        numberingMode = numberingMode,
        numberingCycle = numberingCycle,
        businessDate = businessDate?.toString(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        acceptedAt = acceptedAt?.toEpochMilli(),
        totalCents = total.cents,
        generalNote = generalNote,
        sourceOrderId = sourceOrderId,
    ),
    items = items.map { it.toDatabaseModel(orderId = id) },
)

private fun OrderItemWithModifiers.toDomain(): OrderItem = OrderItem(
    id = item.id,
    productId = item.productId,
    productNameSnapshot = item.productNameSnapshot,
    productPrintedNameSnapshot = item.productPrintedNameSnapshot,
    categorySnapshot = item.categorySnapshot,
    quantity = item.quantity,
    baseUnitPrice = Money.ofCents(item.baseUnitPriceCents),
    automaticExtrasTotal = Money.ofCents(item.automaticExtrasTotalCents),
    manualUnitPrice = item.manualUnitPriceCents?.let(Money::ofCents),
    finalUnitPrice = Money.ofCents(item.finalUnitPriceCents),
    automaticExtrasPricingSnapshot = item.automaticExtrasPricingSnapshot,
    note = item.note,
    createdSequence = item.createdSequence,
    additions = additions
        .sortedWith(compareBy({ it.displayOrder }, { it.id }))
        .map(OrderItemAdditionEntity::toDomain),
    removals = removals
        .sortedWith(compareBy({ it.displayOrder }, { it.id }))
        .map(OrderItemRemovalEntity::toDomain),
)

private fun OrderItem.toDatabaseModel(orderId: String): OrderItemWithModifiers =
    OrderItemWithModifiers(
        item = OrderItemEntity(
            id = id,
            orderId = orderId,
            productId = productId,
            productNameSnapshot = productNameSnapshot,
            productPrintedNameSnapshot = productPrintedNameSnapshot,
            categorySnapshot = categorySnapshot,
            quantity = quantity,
            baseUnitPriceCents = baseUnitPrice.cents,
            automaticExtrasTotalCents = automaticExtrasTotal.cents,
            manualUnitPriceCents = manualUnitPrice?.cents,
            finalUnitPriceCents = finalUnitPrice.cents,
            automaticExtrasPricingSnapshot = automaticExtrasPricingSnapshot,
            note = note,
            createdSequence = createdSequence,
        ),
        additions = additions.map { it.toEntity(orderItemId = id) },
        removals = removals.map { it.toEntity(orderItemId = id) },
    )

private fun OrderItemAdditionEntity.toDomain(): OrderItemAddition = OrderItemAddition(
    id = id,
    additionId = additionId,
    nameSnapshot = additionNameSnapshot,
    printedNameSnapshot = additionPrintedNameSnapshot,
    listedPrice = Money.ofCents(listedPriceCents),
    chargedPrice = Money.ofCents(chargedPriceCents),
    displayOrder = displayOrder,
)

private fun OrderItemAddition.toEntity(orderItemId: String): OrderItemAdditionEntity =
    OrderItemAdditionEntity(
        id = id,
        orderItemId = orderItemId,
        additionId = additionId,
        additionNameSnapshot = nameSnapshot,
        additionPrintedNameSnapshot = printedNameSnapshot,
        listedPriceCents = listedPrice.cents,
        chargedPriceCents = chargedPrice.cents,
        displayOrder = displayOrder,
    )

private fun OrderItemRemovalEntity.toDomain(): OrderItemRemoval = OrderItemRemoval(
    id = id,
    ingredientId = ingredientId,
    nameSnapshot = ingredientNameSnapshot,
    displayOrder = displayOrder,
)

private fun OrderItemRemoval.toEntity(orderItemId: String): OrderItemRemovalEntity =
    OrderItemRemovalEntity(
        id = id,
        orderItemId = orderItemId,
        ingredientId = ingredientId,
        ingredientNameSnapshot = nameSnapshot,
        displayOrder = displayOrder,
    )

private const val DRAFT_SLOT = 1
