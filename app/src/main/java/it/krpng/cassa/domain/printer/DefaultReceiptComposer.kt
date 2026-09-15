package it.krpng.cassa.domain.printer

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.acceptance.AcceptancePreviewOrdering
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.pricing.CalculateOrderTotal

/**
 * Pure Kotlin [ReceiptComposer] implementation (PRINT-004 / D-051).
 * Snapshot-only, deterministic; no catalog, clock, locale, Room, or Android.
 */
class DefaultReceiptComposer : ReceiptComposer {
    override fun compose(
        order: Order,
        kind: PrintKind,
        pricePrintMode: PricePrintMode,
        charsPerLine: Int,
    ): PrintableDocument {
        val width = ReceiptTextLayout.effectiveWidth(charsPerLine)
        val lines = mutableListOf<PrintableLine>()

        appendHeader(lines, kind, order.displayNumber, width)

        val sections = AcceptancePreviewOrdering.groupByCategory(order.items)
        var remainingItems = sections.sumOf { it.items.size }

        for (section in sections) {
            lines +=
                PrintableLine(
                    text = ReceiptTextLayout.sectionTitleLine(section.title, width),
                    emphasis = PrintEmphasis.EMPHASIZED,
                )
            for (item in section.items) {
                appendItem(lines, item, pricePrintMode, width)
                remainingItems--
                if (pricePrintMode == PricePrintMode.TOTAL_ONLY && remainingItems > 0) {
                    lines += PrintableLine("")
                }
            }
        }

        val generalNote = order.generalNote
        if (!generalNote.isNullOrBlank()) {
            lines += PrintableLine("NOTE ORDINE:")
            lines +=
                ReceiptTextLayout.wrapPreservingLeadingIndent(generalNote, width).map { text ->
                    PrintableLine(text)
                }
        }

        appendTotal(lines, order.total, width)

        return PrintableDocument(kind = kind, lines = lines)
    }

    private fun appendHeader(
        lines: MutableList<PrintableLine>,
        kind: PrintKind,
        displayNumber: String?,
        width: Int,
    ) {
        val token =
            when (kind) {
                PrintKind.DRAFT -> "BOZZA"
                PrintKind.FINAL -> displayNumber.orEmpty()
            }
        lines += PrintableLine(ReceiptTextLayout.bannerLine(width))
        lines +=
            PrintableLine(
                text = ReceiptTextLayout.center(token, width),
                emphasis = PrintEmphasis.EMPHASIZED,
            )
        lines += PrintableLine(ReceiptTextLayout.bannerLine(width))
    }

    private fun appendItem(
        lines: MutableList<PrintableLine>,
        item: OrderItem,
        pricePrintMode: PricePrintMode,
        width: Int,
    ) {
        val mainLeft = "${item.quantity}x ${item.productPrintedNameSnapshot}"
        val mainPrice =
            if (pricePrintMode == PricePrintMode.DETAILED) {
                PrintMoneyFormat.format(extendedLineCents(item.finalUnitPrice, item.quantity))
            } else {
                null
            }
        lines +=
            ReceiptTextLayout.linesWithOptionalPrice(mainLeft, mainPrice, width).map { text ->
                PrintableLine(text)
            }

        val showAdditionPrices = shouldShowAdditionPrices(item, pricePrintMode)
        val additions = item.additions.sortedBy { it.displayOrder }
        for (addition in additions) {
            appendAddition(lines, addition, item.quantity, showAdditionPrices, width)
        }

        val removals = item.removals.sortedBy { it.displayOrder }
        for (removal in removals) {
            val left = "   - ${removal.nameSnapshot}"
            lines +=
                ReceiptTextLayout.wrapPreservingLeadingIndent(left, width).map { text ->
                    PrintableLine(text)
                }
        }

        val note = item.note
        if (!note.isNullOrBlank()) {
            val noteText = "NOTA: $note"
            lines +=
                ReceiptTextLayout.wrapPreservingLeadingIndent(noteText, width).map { text ->
                    PrintableLine(text)
                }
        }
    }

    private fun appendAddition(
        lines: MutableList<PrintableLine>,
        addition: OrderItemAddition,
        quantity: Int,
        showPrices: Boolean,
        width: Int,
    ) {
        val left = "   + ${addition.printedNameSnapshot}"
        val priceText =
            if (showPrices) {
                val extended = extendedLineCents(addition.chargedPrice, quantity)
                if (extended.cents > 0L) PrintMoneyFormat.format(extended) else null
            } else {
                null
            }
        lines +=
            ReceiptTextLayout.linesWithOptionalPrice(left, priceText, width).map { text ->
                PrintableLine(text)
            }
    }

    private fun appendTotal(
        lines: MutableList<PrintableLine>,
        total: Money,
        width: Int,
    ) {
        lines += PrintableLine(ReceiptTextLayout.totalSeparator(width))
        val amount = PrintMoneyFormat.format(total)
        lines +=
            ReceiptTextLayout.linesWithOptionalPrice("TOTALE", amount, width).map { text ->
                PrintableLine(text)
            }
    }

    private fun shouldShowAdditionPrices(
        item: OrderItem,
        pricePrintMode: PricePrintMode,
    ): Boolean =
        pricePrintMode == PricePrintMode.DETAILED &&
            item.manualUnitPrice == null &&
            item.automaticExtrasPricingSnapshot

    /**
     * Quantity-extended amount using checked Money arithmetic (throws on overflow).
     */
    private fun extendedLineCents(
        unitPrice: Money,
        quantity: Int,
    ): Money = CalculateOrderTotal.lineTotalCents(unitPrice, quantity)
}
