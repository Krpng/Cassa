package it.krpng.cassa.domain.printer

import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.PricePrintMode

/**
 * Maps an order snapshot to a [PrintableDocument] (D-049 / architecture §20).
 * Layout/body: [DefaultReceiptComposer] (PRINT-004 / D-051).
 *
 * [order] is the domain [Order] aggregate (draft or accepted snapshot), never a Room entity.
 */
interface ReceiptComposer {
    fun compose(
        order: Order,
        kind: PrintKind,
        pricePrintMode: PricePrintMode,
        charsPerLine: Int,
    ): PrintableDocument
}