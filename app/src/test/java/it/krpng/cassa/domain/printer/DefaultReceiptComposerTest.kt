package it.krpng.cassa.domain.printer

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.ProductCategory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRINT-004 / D-051 / D-065 / D-066 / D-067 golden and focused tests.
 * Owns PRINT-T001..T008, T010..T014. Does not claim PRINT-T009.
 * D-065: every business PrintableLine uses DOUBLE_BOTH.
 * D-066: DRAFT TOTALE from persisted items; FINAL from order.total.
 * D-067: layout width = layoutWidth(baseCharsPerLine, DOUBLE_BOTH).
 */
class DefaultReceiptComposerTest {
    private val composer = DefaultReceiptComposer()
    private val baseCharsPerLine = 42
    private val width =
        ReceiptTextLayout.layoutWidth(baseCharsPerLine, PrintTextScale.DOUBLE_BOTH)

    // --- PRINT-T001..T008 ---

    @Test
    fun `PRINT-T001 draft header is BOZZA without displayNumber or date time`() {
        val order =
            baseOrder(
                status = OrderStatus.DRAFT,
                displayNumber = "A37",
                items = listOf(pizzaItem()),
            )
        val doc = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        assertEquals(PrintKind.DRAFT, doc.kind)
        assertEquals(banner(), doc.lines[0].text)
        assertEquals(PrintEmphasis.EMPHASIZED, doc.lines[1].emphasis)
        assertEquals(ReceiptTextLayout.center("BOZZA", width), doc.lines[1].text)
        assertEquals(banner(), doc.lines[2].text)
        val joined = doc.lines.joinToString("\n") { it.text }
        assertFalse(joined.contains("A37"))
        assertFalse(joined.contains("2026"))
        assertFalse(joined.contains("T10:"))
        assertFalse(joined.contains("RISTAMPA"))
        assertFalse(joined.contains("FINAL"))
    }

    @Test
    fun `PRINT-T002 final header is displayNumber only without date time`() {
        val order =
            baseOrder(
                status = OrderStatus.ACCEPTED,
                displayNumber = "A37",
                items = listOf(pizzaItem()),
            )
        val doc = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        assertEquals(PrintKind.FINAL, doc.kind)
        assertEquals(ReceiptTextLayout.center("A37", width), doc.lines[1].text)
        assertEquals(PrintEmphasis.EMPHASIZED, doc.lines[1].emphasis)
        val joined = doc.lines.joinToString("\n") { it.text }
        assertFalse(joined.contains("BOZZA"))
        assertFalse(joined.contains("RISTAMPA"))
        assertFalse(joined.contains("2026-09"))
        assertFalse(joined.contains("T10:00"))
    }

    @Test
    fun `PRINT-T003 section order is PIZZE then FRITTURA then BIBITE`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        item(
                            id = "b1",
                            name = "Coca Cola",
                            category = ProductCategory.BIBITA,
                            sequence = 1,
                            unitCents = 250,
                        ),
                        item(
                            id = "f1",
                            name = "Arancino",
                            category = ProductCategory.FRITTURA,
                            sequence = 2,
                            unitCents = 300,
                        ),
                        item(
                            id = "p1",
                            name = "Margherita",
                            category = ProductCategory.PIZZA,
                            sequence = 3,
                            unitCents = 700,
                        ),
                    ),
                totalCents = 1250,
            )
        val titles =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
                .lines
                .filter { it.emphasis == PrintEmphasis.EMPHASIZED }
                .map { it.text }
                .filter { it.contains("PIZZE") || it.contains("FRITTURA") || it.contains("BIBITE") }
        assertEquals(3, titles.size)
        assertTrue(titles[0].contains("PIZZE"))
        assertTrue(titles[1].contains("FRITTURA"))
        assertTrue(titles[2].contains("BIBITE"))
    }

    @Test
    fun `PRINT-T004 empty categories are omitted`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        item(
                            id = "p1",
                            name = "Margherita",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            unitCents = 700,
                        ),
                        item(
                            id = "b1",
                            name = "Acqua",
                            category = ProductCategory.BIBITA,
                            sequence = 2,
                            unitCents = 100,
                        ),
                    ),
                totalCents = 800,
            )
        val joined =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
                .lines
                .joinToString("\n") { it.text }
        assertTrue(joined.contains("PIZZE"))
        assertTrue(joined.contains("BIBITE"))
        assertFalse(joined.contains("FRITTURA"))
    }

    @Test
    fun `PRINT-T005 charged additions show extended price in DETAILED`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            quantity = 2,
                            unitCents = 700,
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Provola",
                                        chargedCents = 300,
                                        displayOrder = 0,
                                    ),
                                ),
                        ),
                    ),
                totalCents = 2000,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.startsWith("2x Margherita") && it.endsWith("14,00") })
        assertTrue(texts.any { it.contains("+ Provola") && it.endsWith("6,00") })
    }

    @Test
    fun `PRINT-T006 removals never priced`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            removals =
                                listOf(
                                    removal(id = "r1", name = "Mozzarella", displayOrder = 0),
                                ),
                        ),
                    ),
            )
        val removalLines =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
                .lines
                .map { it.text }
                .filter { it.contains("- Mozzarella") }
        assertEquals(1, removalLines.size)
        assertEquals("   - Mozzarella", removalLines.single())
        assertFalse(removalLines.single().contains(","))
    }

    @Test
    fun `PRINT-T007 item note wrapping`() {
        val note = "per favore senza cipolla cruda grazie"
        val order = baseOrder(items = listOf(pizzaItem(note = note)))
        val layout = ReceiptTextLayout.layoutWidth(20, PrintTextScale.DOUBLE_BOTH)
        val texts =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED, charsPerLine = 20)
                .lines
                .map { it.text }
        val expected = ReceiptTextLayout.wrapPreservingLeadingIndent("NOTA: $note", layout)
        assertTrue(expected.size >= 2)
        assertTrue(texts.containsAll(expected))
        assertTrue(expected.first().startsWith("NOTA:"))
    }

    @Test
    fun `PRINT-T008 persisted order total is printed without reprice`() {
        val order =
            baseOrder(
                items = listOf(pizzaItem(unitCents = 700)),
                totalCents = 9999,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.contains(separator()))
        assertTrue(texts.any { it.startsWith("TOTALE") && it.endsWith("99,99") })
        assertFalse(texts.any { it.endsWith("7,00") && it.startsWith("TOTALE") })
    }

    // --- PRINT-T010..T014 ---

    @Test
    fun `PRINT-T010 manual price hides addition prices`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            unitCents = 1300,
                            manualCents = 1300,
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Provola",
                                        chargedCents = 300,
                                        displayOrder = 0,
                                    ),
                                ),
                            removals =
                                listOf(removal(id = "r1", name = "Mozzarella", displayOrder = 0)),
                        ),
                    ),
                totalCents = 1300,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.startsWith("1x Margherita") && it.endsWith("13,00") })
        assertEquals("   + Provola", texts.first { it.contains("+ Provola") })
        assertFalse(texts.any { it.contains("+ Provola") && it.contains("3,00") })
    }

    @Test
    fun `PRINT-T011 automaticExtrasPricing false hides addition prices`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            name = "Pizza Fritta",
                            unitCents = 1300,
                            automaticExtras = false,
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Provola",
                                        chargedCents = 0,
                                        displayOrder = 0,
                                    ),
                                    addition(
                                        id = "a2",
                                        printed = "Prosciutto cotto",
                                        chargedCents = 0,
                                        displayOrder = 1,
                                    ),
                                ),
                        ),
                    ),
                totalCents = 1300,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.startsWith("1x Pizza Fritta") && it.endsWith("13,00") })
        assertEquals("   + Provola", texts.first { it.contains("+ Provola") })
        assertEquals("   + Prosciutto cotto", texts.first { it.contains("+ Prosciutto") })
        assertFalse(texts.any { it.contains("+ ") && it.matches(Regex(".*\\d,\\d\\d$")) })
    }

    @Test
    fun `PRINT-T012 TOTAL_ONLY omits line and addition prices but prints total with blank between items`() {
        val order =
            baseOrder(
                displayNumber = "037",
                items =
                    listOf(
                        pizzaItem(
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Provola",
                                        chargedCents = 300,
                                        displayOrder = 0,
                                    ),
                                ),
                            removals =
                                listOf(removal(id = "r1", name = "Mozzarella", displayOrder = 0)),
                        ),
                        item(
                            id = "b1",
                            name = "Coca Cola",
                            category = ProductCategory.BIBITA,
                            sequence = 2,
                            quantity = 2,
                            unitCents = 250,
                        ),
                    ),
                totalCents = 2450,
            )
        val texts =
            compose(order, PrintKind.FINAL, PricePrintMode.TOTAL_ONLY).lines.map { it.text }
        assertEquals("1x Margherita", texts.first { it.startsWith("1x Margherita") })
        assertEquals("   + Provola", texts.first { it.contains("+ Provola") })
        assertEquals("2x Coca Cola", texts.first { it.startsWith("2x Coca Cola") })
        assertFalse(texts.any { it.startsWith("1x ") && it.contains(",") })
        assertFalse(texts.any { it.contains("+ ") && it.matches(Regex(".*\\d,\\d\\d$")) })
        assertTrue(texts.any { it.startsWith("TOTALE") && it.endsWith("24,50") })

        val margheritaIdx = texts.indexOf("1x Margherita")
        val blankIdx =
            texts.withIndex().indexOfFirst { (i, t) -> i > margheritaIdx && t.isEmpty() }
        assertTrue(blankIdx > margheritaIdx)
        val colaSectionIdx = texts.indexOfFirst { it.contains("BIBITE") }
        assertTrue(colaSectionIdx > blankIdx)
    }

    @Test
    fun `PRINT-T013 uses productPrintedNameSnapshot not catalog name`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            name = "Catalog Only Name",
                            printedName = "Stampabile Snapshot",
                        ),
                    ),
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(
            texts.joinToString("")
                .replace(" ", "")
                .contains("StampabileSnapshot"),
        )
        assertFalse(texts.any { it.contains("Catalog Only Name") })
    }

    @Test
    fun `PRINT-T014 long product names wrap without truncation`() {
        val longName = "Supercalifragilisticexpialidocious Pizza Speciale"
        val order = baseOrder(items = listOf(pizzaItem(printedName = longName)))
        val layout = ReceiptTextLayout.layoutWidth(24, PrintTextScale.DOUBLE_BOTH)
        val texts =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED, charsPerLine = 24)
                .lines
                .map { it.text }
        val expectedLeft = "1x $longName"
        val wrapped = ReceiptTextLayout.wrapPreservingLeadingIndent(expectedLeft, layout)
        assertTrue(texts.containsAll(wrapped))
        assertTrue(wrapped.joinToString("").replace(" ", "").contains(longName.replace(" ", "")))
    }

    // --- Focused extra coverage ---

    @Test
    fun `reprint FINAL produces identical content`() {
        val order =
            baseOrder(
                status = OrderStatus.ACCEPTED,
                displayNumber = "037",
                items = listOf(pizzaItem()),
            )
        val first = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        val reprint = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        assertEquals(first, reprint)
        assertFalse(first.lines.any { it.text.contains("RISTAMPA") })
    }

    @Test
    fun `manual euro zero still hides addition prices and prints main 0,00`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            unitCents = 0,
                            manualCents = 0,
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Provola",
                                        chargedCents = 300,
                                        displayOrder = 0,
                                    ),
                                ),
                        ),
                    ),
                totalCents = 0,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.startsWith("1x Margherita") && it.endsWith("0,00") })
        assertEquals("   + Provola", texts.first { it.contains("+ Provola") })
    }

    @Test
    fun `addition chargedPrice zero hides 0,00 in DETAILED`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            additions =
                                listOf(
                                    addition(
                                        id = "a1",
                                        printed = "Ben cotta",
                                        chargedCents = 0,
                                        displayOrder = 0,
                                    ),
                                ),
                        ),
                    ),
            )
        assertEquals(
            "   + Ben cotta",
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
                .lines
                .map { it.text }
                .first { it.contains("Ben cotta") },
        )
    }

    @Test
    fun `explicit newlines in item and general notes are preserved then wrapped`() {
        val order =
            baseOrder(
                generalNote = "prima\nseconda riga lunga abbastanza",
                items = listOf(pizzaItem(note = "alpha\nbeta")),
                totalCents = 700,
            )
        val texts =
            compose(order, PrintKind.FINAL, PricePrintMode.DETAILED, charsPerLine = 16)
                .lines
                .map { it.text }
        val joined = texts.joinToString("\n")
        assertTrue(joined.contains("NOTA:"))
        assertTrue(joined.contains("alpha"))
        assertTrue(joined.contains("beta"))
        val noteOrdineIdx = texts.indexOf("NOTE ORDINE:")
        assertTrue(noteOrdineIdx >= 0)
        assertTrue(texts[noteOrdineIdx + 1].startsWith("prima") || texts[noteOrdineIdx + 1] == "prima")
        val layout = ReceiptTextLayout.layoutWidth(16, PrintTextScale.DOUBLE_BOTH)
        val totalSepIdx = texts.indexOf(separator(layout))
        assertTrue(totalSepIdx > noteOrdineIdx)
    }

    @Test
    fun `general note sits after sections and before total`() {
        val order =
            baseOrder(
                generalNote = "Senza glutine",
                items = listOf(pizzaItem()),
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        val sectionIdx = texts.indexOfFirst { it.contains("PIZZE") }
        val noteIdx = texts.indexOf("NOTE ORDINE:")
        val totalIdx = texts.indexOf(separator())
        assertTrue(sectionIdx < noteIdx)
        assertTrue(noteIdx < totalIdx)
        assertEquals("Senza glutine", texts[noteIdx + 1])
    }

    @Test
    fun `blank and null notes are omitted`() {
        val blank =
            compose(
                baseOrder(generalNote = "   ", items = listOf(pizzaItem(note = "  "))),
                PrintKind.FINAL,
                PricePrintMode.DETAILED,
            ).lines.map { it.text }
        assertFalse(blank.any { it.startsWith("NOTA:") })
        assertFalse(blank.contains("NOTE ORDINE:"))

        val absent =
            compose(
                baseOrder(generalNote = null, items = listOf(pizzaItem(note = null))),
                PrintKind.FINAL,
                PricePrintMode.DETAILED,
            ).lines.map { it.text }
        assertFalse(absent.any { it.startsWith("NOTA:") })
        assertFalse(absent.contains("NOTE ORDINE:"))
    }

    @Test
    fun `item order tie-break is createdSequence then id`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        item(
                            id = "z",
                            name = "Second",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            unitCents = 100,
                        ),
                        item(
                            id = "a",
                            name = "First",
                            category = ProductCategory.PIZZA,
                            sequence = 1,
                            unitCents = 100,
                        ),
                        item(
                            id = "m",
                            name = "Third",
                            category = ProductCategory.PIZZA,
                            sequence = 2,
                            unitCents = 100,
                        ),
                    ),
                totalCents = 300,
            )
        val names =
            compose(order, PrintKind.FINAL, PricePrintMode.TOTAL_ONLY)
                .lines
                .map { it.text }
                .filter { it.startsWith("1x ") }
        assertEquals(listOf("1x First", "1x Second", "1x Third"), names)
    }

    @Test
    fun `deterministic compose repeats identically`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            additions =
                                listOf(
                                    addition("a2", "Funghi", 300, 1),
                                    addition("a1", "Provola", 300, 0),
                                ),
                        ),
                    ),
                totalCents = 1300,
            )
        val a = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        val b = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        assertEquals(a, b)
    }

    @Test
    fun `charsPerLine zero and negative still compose deterministically`() {
        val order = baseOrder(items = listOf(pizzaItem()))
        val zero = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED, charsPerLine = 0)
        val negative = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED, charsPerLine = -3)
        assertEquals(zero, negative)
        assertEquals("=", zero.lines[0].text)
        assertEquals(PrintEmphasis.EMPHASIZED, zero.lines[1].emphasis)
        assertTrue(zero.lines[1].text.contains("BOZZA"))
    }

    @Test
    fun `money one thousand has no thousands grouping`() {
        val order =
            baseOrder(
                items = listOf(pizzaItem(unitCents = 100_000)),
                totalCents = 100_000,
            )
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.endsWith("1000,00") })
        assertFalse(texts.any { it.contains("1.000") || it.contains("1 000") || it.contains("€") })
    }

    @Test
    fun `DETAILED golden pizza with priced addition and removal`() {
        val order =
            baseOrder(
                displayNumber = "A37",
                items =
                    listOf(
                        pizzaItem(
                            additions =
                                listOf(
                                    addition("a1", "Provola", 300, 0),
                                    addition("a2", "Funghi", 300, 1),
                                ),
                            removals = listOf(removal("r1", "Mozzarella", 0)),
                        ),
                    ),
                totalCents = 1300,
            )
        val expected =
            listOf(
                line(banner()),
                line(ReceiptTextLayout.center("A37", width), PrintEmphasis.EMPHASIZED),
                line(banner()),
                line(ReceiptTextLayout.sectionTitleLine("PIZZE", width), PrintEmphasis.EMPHASIZED),
                line(ReceiptTextLayout.linesWithOptionalPrice("1x Margherita", "7,00", width).single()),
                line(ReceiptTextLayout.linesWithOptionalPrice("   + Provola", "3,00", width).single()),
                line(ReceiptTextLayout.linesWithOptionalPrice("   + Funghi", "3,00", width).single()),
                line("   - Mozzarella"),
                line(separator()),
                line(ReceiptTextLayout.linesWithOptionalPrice("TOTALE", "13,00", width).single()),
            )
        assertEquals(expected, compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines)
    }

    // --- D-065 business receipt base text scale ---

    @Test
    fun `D065 DRAFT every line is DOUBLE_BOTH preserving BOZZA alignment and emphasis`() {
        val order =
            baseOrder(
                status = OrderStatus.DRAFT,
                displayNumber = null,
                items = listOf(pizzaItem()),
                totalCents = 700,
            )
        val doc = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        assertTrue(doc.lines.isNotEmpty())
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })
        assertEquals(ReceiptTextLayout.center("BOZZA", width), doc.lines[1].text)
        assertEquals(PrintEmphasis.EMPHASIZED, doc.lines[1].emphasis)
        assertEquals(PrintAlignment.LEFT, doc.lines[1].alignment)
        assertFalse(doc.lines.joinToString("\n") { it.text }.contains("A37"))
        assertTrue(doc.lines.none { it.textScale == PrintTextScale.NORMAL })
    }

    @Test
    fun `D065 FINAL every line is DOUBLE_BOTH preserving displayNumber alignment and emphasis`() {
        val order =
            baseOrder(
                status = OrderStatus.ACCEPTED,
                displayNumber = "A37",
                items = listOf(pizzaItem()),
                totalCents = 700,
            )
        val doc = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        assertTrue(doc.lines.isNotEmpty())
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })
        assertEquals(ReceiptTextLayout.center("A37", width), doc.lines[1].text)
        assertEquals(PrintEmphasis.EMPHASIZED, doc.lines[1].emphasis)
        assertEquals(PrintAlignment.LEFT, doc.lines[1].alignment)
        assertFalse(doc.lines.any { it.text.contains("BOZZA") })
    }

    @Test
    fun `D065 DETAILED and TOTAL_ONLY content and PricePrintMode semantics unchanged aside from scale`() {
        val order =
            baseOrder(
                displayNumber = "A37",
                generalNote = "Portare fuori",
                items =
                    listOf(
                        pizzaItem(
                            additions = listOf(addition("a1", "Provola", 300, 0)),
                            removals = listOf(removal("r1", "Mozzarella", 0)),
                            note = "ben cotta",
                        ),
                        item(
                            id = "b1",
                            name = "Acqua",
                            category = ProductCategory.BIBITA,
                            sequence = 1,
                            unitCents = 100,
                        ),
                    ),
                totalCents = 1100,
            )

        val detailed = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        val totalOnly = compose(order, PrintKind.FINAL, PricePrintMode.TOTAL_ONLY)

        assertTrue(detailed.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })
        assertTrue(totalOnly.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })

        val detailedTexts = detailed.lines.map { it.text }
        assertTrue(detailedTexts.any { it.contains("1x Margherita") && it.contains("7,00") })
        assertTrue(detailedTexts.any { it.contains("+ Provola") && it.contains("3,00") })
        assertTrue(detailedTexts.any { it.contains("- Mozzarella") && !it.contains(",") })
        assertTrue(detailedTexts.any { it.contains("NOTA: ben cotta") })
        assertTrue(detailedTexts.any { it == "NOTE ORDINE:" })
        assertTrue(detailedTexts.any { it.contains("Portare fuori") })
        assertTrue(detailedTexts.any { it.contains("TOTALE") && it.contains("11,00") })

        val totalOnlyTexts = totalOnly.lines.map { it.text }
        assertTrue(totalOnlyTexts.any { it.startsWith("1x Margherita") && !it.contains("7,00") })
        assertTrue(totalOnlyTexts.any { it.contains("+ Provola") && !it.contains("3,00") })
        assertTrue(totalOnlyTexts.any { it.contains("TOTALE") && it.contains("11,00") })
        assertTrue(totalOnlyTexts.contains(""))

        assertTrue(detailed.lines.all { it.alignment == PrintAlignment.LEFT })
        assertTrue(totalOnly.lines.all { it.alignment == PrintAlignment.LEFT })
        assertEquals(
            PrintEmphasis.EMPHASIZED,
            detailed.lines.first { it.text.contains("PIZZE") }.emphasis,
        )
    }

    // --- D-066 draft printable total source ---

    @Test
    fun `D066 DRAFT derives TOTALE from items when order total is ZERO`() {
        val order =
            baseOrder(
                status = OrderStatus.DRAFT,
                displayNumber = null,
                totalCents = 0,
                items =
                    listOf(
                        pizzaItem(
                            id = "p-4f",
                            name = "4 formaggi",
                            unitCents = 1400,
                            sequence = 1,
                        ),
                        pizzaItem(
                            id = "p-mar",
                            name = "Marinara",
                            unitCents = 600,
                            sequence = 2,
                        ),
                        item(
                            id = "f-ara",
                            name = "Arancino G",
                            category = ProductCategory.FRITTURA,
                            sequence = 1,
                            unitCents = 250,
                        ),
                    ),
            )
        assertEquals(Money.ZERO, order.total)
        assertNull(order.displayNumber)
        assertEquals(OrderStatus.DRAFT, order.status)

        val doc = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        val texts = doc.lines.map { it.text }

        assertEquals(PrintKind.DRAFT, doc.kind)
        assertEquals(ReceiptTextLayout.center("BOZZA", width), doc.lines[1].text)
        assertFalse(texts.any { it.contains("A37") })
        assertTrue(texts.any { it.contains("1x 4 formaggi") && it.endsWith("14,00") })
        assertTrue(texts.any { it.contains("1x Marinara") && it.endsWith("6,00") })
        assertTrue(texts.any { it.contains("1x Arancino G") && it.endsWith("2,50") })
        assertTrue(texts.any { it.startsWith("TOTALE") && it.endsWith("22,50") })
        assertFalse(texts.any { it.startsWith("TOTALE") && it.endsWith("0,00") })
        assertTrue(doc.lines.isNotEmpty())
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })

        // Compose is observational — input aggregate remains ZERO.
        assertEquals(Money.ZERO, order.total)
        assertEquals(OrderStatus.DRAFT, order.status)
        assertNull(order.displayNumber)
    }

    @Test
    fun `D066 FINAL still prints frozen order total not item sum`() {
        val order =
            baseOrder(
                status = OrderStatus.ACCEPTED,
                displayNumber = "A37",
                totalCents = 9999,
                items = listOf(pizzaItem(unitCents = 700)),
            )
        // Item-derived sum would be 7,00; frozen accepted total is 99,99.
        val texts = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED).lines.map { it.text }
        assertTrue(texts.any { it.startsWith("TOTALE") && it.endsWith("99,99") })
        assertFalse(texts.any { it.startsWith("TOTALE") && it.endsWith("7,00") })
        assertEquals(ReceiptTextLayout.center("A37", width), texts[1])
        assertEquals(Money.ofCents(9999), order.total)
    }

    // --- D-067 scale-aware layout width ---

    @Test
    fun `D067 DOUBLE_BOTH separators and FRITTURA fit layout width 21`() {
        val order =
            baseOrder(
                status = OrderStatus.DRAFT,
                displayNumber = null,
                totalCents = 0,
                items =
                    listOf(
                        item(
                            id = "f1",
                            name = "Arancino",
                            category = ProductCategory.FRITTURA,
                            sequence = 1,
                            unitCents = 250,
                        ),
                    ),
            )
        val doc = compose(order, PrintKind.DRAFT, PricePrintMode.DETAILED)
        assertEquals(21, width)
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })

        val banners = doc.lines.map { it.text }.filter { it.all { ch -> ch == '=' } && it.isNotEmpty() }
        assertTrue(banners.isNotEmpty())
        assertTrue(banners.all { it.length == width })

        val sep = doc.lines.map { it.text }.first { it.all { ch -> ch == '-' } && it.isNotEmpty() }
        assertEquals(width, sep.length)

        val fritturaLine =
            doc.lines.first { it.text.contains("FRITTURA") }
        assertEquals(1, doc.lines.count { it.text.contains("FRITTURA") })
        assertTrue(fritturaLine.text.length <= width)
        assertEquals(PrintEmphasis.EMPHASIZED, fritturaLine.emphasis)

        assertEquals(ReceiptTextLayout.center("BOZZA", width), doc.lines[1].text)
        assertTrue(doc.lines[1].text.length <= width)

        val totale = doc.lines.map { it.text }.first { it.startsWith("TOTALE") }
        assertTrue(totale.length <= width)
        assertTrue(totale.endsWith("2,50"))
    }

    @Test
    fun `D067 long product wraps within layout width and preserves price once`() {
        val longName = "Supercalifragilisticexpialidocious Pizza Speciale Con Molto Testo"
        val order =
            baseOrder(
                items = listOf(pizzaItem(printedName = longName, unitCents = 700)),
                totalCents = 700,
            )
        val doc = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        val texts = doc.lines.map { it.text }
        val productLines =
            texts.filter {
                !it.startsWith("TOTALE") &&
                    !it.all { ch -> ch == '=' || ch == '-' } &&
                    !it.contains("PIZZE") &&
                    it.isNotEmpty() &&
                    (it.contains("1x") || it.contains("Supercal") || it.contains("Pizza") ||
                        it.contains("Speciale") || it.contains("Testo") || it.matches(Regex("^\\d+,\\d\\d$")))
            }
        assertTrue(productLines.size >= 2)
        assertTrue(
            texts.joinToString("")
                .replace(" ", "")
                .contains(longName.replace(" ", "")),
        )
        assertTrue(productLines.filter { !it.matches(Regex("^\\d+,\\d\\d$")) }.all { it.length <= width })
        assertEquals(1, texts.count { it.contains("7,00") && !it.startsWith("TOTALE") })
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })
    }

    @Test
    fun `D067 addition removal and note wrap within layout width`() {
        val order =
            baseOrder(
                items =
                    listOf(
                        pizzaItem(
                            additions =
                                listOf(
                                    addition(
                                        "a1",
                                        "Provola Affumicata Extra Lunga",
                                        300,
                                        0,
                                    ),
                                ),
                            removals = listOf(removal("r1", "Mozzarella Fresca Di Bufala", 0)),
                            note = "ben cotta senza cipolla cruda per favore grazie",
                        ),
                    ),
                totalCents = 1000,
            )
        val doc = compose(order, PrintKind.FINAL, PricePrintMode.DETAILED)
        val texts = doc.lines.map { it.text }
        assertTrue(texts.any { it.contains("+ Provola") || it.contains("Affumicata") })
        assertTrue(texts.any { it.contains("- Mozzarella") || it.contains("Bufala") })
        assertTrue(texts.any { it.startsWith("NOTA:") || it.contains("cipolla") })
        assertTrue(
            texts
                .filter { it.contains('+') || it.contains('-') || it.startsWith("NOTA") || it.contains("cipolla") }
                .all { it.length <= width },
        )
        assertTrue(doc.lines.all { it.textScale == PrintTextScale.DOUBLE_BOTH })
    }

    // --- helpers ---

    private fun compose(
        order: Order,
        kind: PrintKind,
        mode: PricePrintMode,
        charsPerLine: Int = baseCharsPerLine,
    ): PrintableDocument = composer.compose(order, kind, mode, charsPerLine)

    private fun banner(w: Int = width): String = ReceiptTextLayout.bannerLine(w)

    private fun separator(w: Int = width): String = ReceiptTextLayout.totalSeparator(w)

    private fun line(
        text: String,
        emphasis: PrintEmphasis = PrintEmphasis.NORMAL,
    ): PrintableLine =
        PrintableLine(
            text = text,
            emphasis = emphasis,
            textScale = PrintTextScale.DOUBLE_BOTH,
        )

    private fun baseOrder(
        status: OrderStatus = OrderStatus.ACCEPTED,
        displayNumber: String? = "A37",
        generalNote: String? = null,
        items: List<OrderItem>,
        totalCents: Long = items.sumOf { it.finalUnitPrice.cents * it.quantity },
    ): Order =
        Order(
            id = "order-1",
            status = status,
            displayNumber = displayNumber,
            numberingMode = null,
            numberingCycle = null,
            businessDate = null,
            createdAt = FIXED_INSTANT,
            updatedAt = FIXED_INSTANT,
            acceptedAt = if (status == OrderStatus.ACCEPTED) FIXED_INSTANT else null,
            total = Money.ofCents(totalCents),
            generalNote = generalNote,
            sourceOrderId = null,
            items = items,
        )

    private fun pizzaItem(
        id: String = "item-p1",
        name: String = "Margherita",
        printedName: String = name,
        quantity: Int = 1,
        unitCents: Long = 700,
        manualCents: Long? = null,
        automaticExtras: Boolean = true,
        note: String? = null,
        sequence: Int = 1,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem =
        item(
            id = id,
            name = name,
            printedName = printedName,
            category = ProductCategory.PIZZA,
            quantity = quantity,
            unitCents = unitCents,
            manualCents = manualCents,
            automaticExtras = automaticExtras,
            note = note,
            sequence = sequence,
            additions = additions,
            removals = removals,
        )

    private fun item(
        id: String,
        name: String,
        printedName: String = name,
        category: ProductCategory,
        quantity: Int = 1,
        unitCents: Long,
        manualCents: Long? = null,
        automaticExtras: Boolean = true,
        note: String? = null,
        sequence: Int,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem =
        OrderItem(
            id = id,
            productId = 1L,
            productNameSnapshot = name,
            productPrintedNameSnapshot = printedName,
            categorySnapshot = category,
            quantity = quantity,
            baseUnitPrice = Money.ofCents(unitCents),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = manualCents?.let(Money::ofCents),
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = automaticExtras,
            note = note,
            createdSequence = sequence,
            additions = additions,
            removals = removals,
        )

    private fun addition(
        id: String,
        printed: String,
        chargedCents: Long,
        displayOrder: Int,
    ): OrderItemAddition =
        OrderItemAddition(
            id = id,
            additionId = 10L,
            nameSnapshot = printed,
            printedNameSnapshot = printed,
            listedPrice = Money.ofCents(chargedCents),
            chargedPrice = Money.ofCents(chargedCents),
            displayOrder = displayOrder,
        )

    private fun removal(
        id: String,
        name: String,
        displayOrder: Int,
    ): OrderItemRemoval =
        OrderItemRemoval(
            id = id,
            ingredientId = 20L,
            nameSnapshot = name,
            displayOrder = displayOrder,
        )

    companion object {
        private val FIXED_INSTANT: Instant = Instant.parse("2026-09-15T10:00:00Z")
    }
}
