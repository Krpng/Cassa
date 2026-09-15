package it.krpng.cassa.domain.printer

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.PricePrintMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class PrintableDocumentContractsTest {
    @Test
    fun `PrintKind contains exactly DRAFT and FINAL`() {
        assertEquals(
            listOf("DRAFT", "FINAL"),
            PrintKind.entries.map(PrintKind::name),
        )
    }

    @Test
    fun `PrintEmphasis contains exactly NORMAL and EMPHASIZED`() {
        assertEquals(
            listOf("NORMAL", "EMPHASIZED"),
            PrintEmphasis.entries.map(PrintEmphasis::name),
        )
    }

    @Test
    fun `PrintableLine stores text and defaults emphasis to NORMAL`() {
        val line = PrintableLine(text = "BOZZA")
        assertEquals("BOZZA", line.text)
        assertEquals(PrintEmphasis.NORMAL, line.emphasis)
    }

    @Test
    fun `PrintableLine stores EMPHASIZED when provided`() {
        val line = PrintableLine(text = "A37", emphasis = PrintEmphasis.EMPHASIZED)
        assertEquals("A37", line.text)
        assertEquals(PrintEmphasis.EMPHASIZED, line.emphasis)
    }

    @Test
    fun `PrintableDocument preserves line order and allows empty lines`() {
        val empty =
            PrintableDocument(
                kind = PrintKind.DRAFT,
                lines = emptyList(),
            )
        assertEquals(PrintKind.DRAFT, empty.kind)
        assertEquals(emptyList<PrintableLine>(), empty.lines)

        val ordered =
            PrintableDocument(
                kind = PrintKind.FINAL,
                lines =
                    listOf(
                        PrintableLine("first"),
                        PrintableLine("second", PrintEmphasis.EMPHASIZED),
                        PrintableLine("third"),
                    ),
            )
        assertEquals(
            listOf("first", "second", "third"),
            ordered.lines.map(PrintableLine::text),
        )
        assertEquals(PrintEmphasis.EMPHASIZED, ordered.lines[1].emphasis)
    }

    @Test
    fun `ReceiptComposer compose stub matches frozen signature with domain Order and PricePrintMode`() {
        val sampleOrder =
            Order(
                id = "order-1",
                status = OrderStatus.DRAFT,
                displayNumber = null,
                numberingMode = null,
                numberingCycle = null,
                businessDate = null,
                createdAt = Instant.parse("2026-09-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-09-15T10:00:00Z"),
                acceptedAt = null,
                total = Money.ZERO,
                generalNote = null,
                sourceOrderId = null,
                items = emptyList(),
            )

        val composer: ReceiptComposer =
            object : ReceiptComposer {
                override fun compose(
                    order: Order,
                    kind: PrintKind,
                    pricePrintMode: PricePrintMode,
                    charsPerLine: Int,
                ): PrintableDocument {
                    assertSame(sampleOrder, order)
                    assertEquals(PrintKind.DRAFT, kind)
                    assertEquals(PricePrintMode.DETAILED, pricePrintMode)
                    assertEquals(32, charsPerLine)
                    return PrintableDocument(kind = kind, lines = emptyList())
                }
            }

        val document =
            composer.compose(
                order = sampleOrder,
                kind = PrintKind.DRAFT,
                pricePrintMode = PricePrintMode.DETAILED,
                charsPerLine = 32,
            )

        assertNotNull(document)
        assertEquals(PrintKind.DRAFT, document.kind)
        assertEquals(emptyList<PrintableLine>(), document.lines)
    }

    @Test
    fun `PricePrintMode TOTAL_ONLY is accepted by ReceiptComposer signature`() {
        val sampleOrder =
            Order(
                id = "order-2",
                status = OrderStatus.ACCEPTED,
                displayNumber = "037",
                numberingMode = null,
                numberingCycle = null,
                businessDate = null,
                createdAt = Instant.parse("2026-09-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-09-15T10:00:00Z"),
                acceptedAt = Instant.parse("2026-09-15T10:05:00Z"),
                total = Money.ofCents(2450),
                generalNote = null,
                sourceOrderId = null,
                items = emptyList(),
            )

        val composer: ReceiptComposer =
            object : ReceiptComposer {
                override fun compose(
                    order: Order,
                    kind: PrintKind,
                    pricePrintMode: PricePrintMode,
                    charsPerLine: Int,
                ): PrintableDocument =
                    PrintableDocument(
                        kind = kind,
                        lines = listOf(PrintableLine(pricePrintMode.name)),
                    )
            }

        val document =
            composer.compose(
                order = sampleOrder,
                kind = PrintKind.FINAL,
                pricePrintMode = PricePrintMode.TOTAL_ONLY,
                charsPerLine = 42,
            )

        assertEquals(PrintKind.FINAL, document.kind)
        assertEquals("TOTAL_ONLY", document.lines.single().text)
    }
}