package it.krpng.cassa.platform.bluetooth

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.NumberingMode
import it.krpng.cassa.domain.model.Order
import it.krpng.cassa.domain.model.OrderItem
import it.krpng.cassa.domain.model.OrderItemAddition
import it.krpng.cassa.domain.model.OrderItemRemoval
import it.krpng.cassa.domain.model.OrderStatus
import it.krpng.cassa.domain.model.PricePrintMode
import it.krpng.cassa.domain.model.PrinterProfile
import it.krpng.cassa.domain.model.ProductCategory
import it.krpng.cassa.domain.printer.DefaultEscPosEncoder
import it.krpng.cassa.domain.printer.DefaultReceiptComposer
import it.krpng.cassa.domain.printer.EncodeResult
import it.krpng.cassa.domain.printer.PrintAlignment
import it.krpng.cassa.domain.printer.PrintEmphasis
import it.krpng.cassa.domain.printer.PrintKind
import it.krpng.cassa.domain.printer.PrintTextScale
import it.krpng.cassa.domain.printer.PrintableDocument
import it.krpng.cassa.domain.printer.PrintableLine
import it.krpng.cassa.domain.printer.PrinterResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHONE + printer hardware calibration harness for HW-001 / D-060.
 *
 * Synthetic ESC/POS capability / visual preview only — not a business receipt redesign.
 * Prints **one** calibration section per run (manual, operator-observed).
 *
 * PRECONDITIONS:
 * - `-e printerId` = bonded Bluetooth address ([BondedBluetoothDevice.id]).
 * - `-e calibrationSection` = WIDTH | FORMAT | CODEPAGE | FEED | ORDER_PREVIEW |
 *   ORDER_PREVIEW_2X | SIZE_COMPARE.
 * - Optional: `-e charsPerLine`, `-e codePage`, `-e escPosCodeTable`, `-e feedLines`.
 * - Device paired in Android Settings; Bluetooth on; BLUETOOTH_CONNECT on API 31+.
 *
 * TEST defaults (not calibrated finals): charsPerLine=32, codePage=ISO-8859-1,
 * escPosCodeTable absent, feedLines=2, supportsCut=false.
 * ORDER_PREVIEW typically passes charsPerLine=42 (TEST-ONLY preview width).
 * ORDER_PREVIEW_2X typically passes charsPerLine=21 (TEST-ONLY / TO BE CALIBRATED:
 * half of ~42 NORMAL fit, because DOUBLE_BOTH doubles physical character width).
 *
 * No retry / reconnect / cutter / DataStore / Room / hardcoded MAC or vendor name.
 */
@RunWith(AndroidJUnit4::class)
class EscPosCalibrationHardwareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun grantBluetoothConnectIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        }
    }

    @Test
    fun oneSection_connectEncodePrintDisconnect_forOperatorObservation() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val printerId = args.getString(ARG_PRINTER_ID)?.trim().orEmpty()
            assertTrue(
                "PRECONDITION: pass instrumentation argument `$ARG_PRINTER_ID` with the " +
                    "bonded Bluetooth address. Example: -e $ARG_PRINTER_ID AA:BB:CC:DD:EE:FF. " +
                    "Do not rely on discovery or hardcoded MAC/vendor names.",
                printerId.isNotEmpty(),
            )

            val sectionRaw = args.getString(ARG_CALIBRATION_SECTION)?.trim().orEmpty()
            val section =
                CalibrationSection.entries.firstOrNull { it.name.equals(sectionRaw, ignoreCase = true) }
            if (section == null) {
                fail(
                    "Invalid or missing `$ARG_CALIBRATION_SECTION`. " +
                        "Use one of: ${CalibrationSection.entries.joinToString { it.name }}. " +
                        "Got: '$sectionRaw'.",
                )
                return@runBlocking
            }

            val charsPerLine = parsePositiveIntArg(args.getString(ARG_CHARS_PER_LINE), DEFAULT_CHARS_PER_LINE)
            val codePage = args.getString(ARG_CODE_PAGE)?.trim().takeUnless { it.isNullOrEmpty() }
                ?: DEFAULT_CODE_PAGE
            val feedLines = parseNonNegativeIntArg(args.getString(ARG_FEED_LINES), DEFAULT_FEED_LINES)
            val escPosCodeTable = parseOptionalSelector(args.getString(ARG_ESC_POS_CODE_TABLE))

            val permissionManager = AndroidBluetoothPermissionManagerFactory.create(context)
            assertTrue(
                "Required Bluetooth runtime permissions must be granted for this PHONE + printer gate",
                permissionManager.areRequiredRuntimePermissionsGranted(),
            )

            val profile =
                PrinterProfile(
                    id = printerId,
                    name = "CalibrationTest",
                    charsPerLine = charsPerLine,
                    codePage = codePage,
                    feedLines = feedLines,
                    paperWidthMm = 80,
                    supportsCut = false,
                    cutCommandVariant = null,
                    pricePrintMode = PricePrintMode.DETAILED,
                    escPosCodeTable = escPosCodeTable,
                )

            val document = sectionDocument(section, charsPerLine)
            val encoded = DefaultEscPosEncoder().encode(document, profile)
            assertTrue(
                "Expected EncodeResult.Success for calibration section=${section.name}; got: $encoded",
                encoded is EncodeResult.Success,
            )
            val bytes = (encoded as EncodeResult.Success).bytes
            assertTrue("Encoded calibration payload must be non-empty", bytes.isNotEmpty())
            assertFalse(
                "HW-001 calibration harness must not emit GS V cut commands",
                containsGsV(bytes),
            )

            val driver = AndroidBluetoothPrinterDriverFactory.create(context)
            try {
                val connectResult = driver.connect(profile)
                assertEquals(
                    "Secure RFCOMM connect must succeed for printerId=$printerId; got: $connectResult",
                    PrinterResult.Success,
                    connectResult,
                )

                val printResult = driver.print(bytes)
                assertEquals(
                    "Single calibration print must succeed; got: $printResult",
                    PrinterResult.Success,
                    printResult,
                )

                Log.i(
                    TAG,
                    "HW-001 calibration section=${section.name} printed for printerId=$printerId " +
                        "(charsPerLine=$charsPerLine codePage=$codePage " +
                        "escPosCodeTable=$escPosCodeTable feedLines=$feedLines). " +
                        "MANUAL CHECK on paper only — no automatic verdict.",
                )
            } finally {
                driver.disconnect()
            }
        }
    }

    private fun sectionDocument(
        section: CalibrationSection,
        charsPerLine: Int,
    ): PrintableDocument =
        when (section) {
            CalibrationSection.WIDTH -> widthDocument()
            CalibrationSection.FORMAT -> formatDocument()
            CalibrationSection.CODEPAGE -> codePageDocument()
            CalibrationSection.FEED -> feedDocument()
            CalibrationSection.ORDER_PREVIEW -> orderPreviewDocument(charsPerLine)
            CalibrationSection.ORDER_PREVIEW_2X -> orderPreview2xDocument(charsPerLine)
            CalibrationSection.SIZE_COMPARE -> sizeCompareDocument()
        }

    /**
     * Paper comparison of native GS ! scales only (D-060): NORMAL = 100%, DOUBLE_BOTH = 200%.
     * No fake 150% / raster / undocumented fonts. Operator decides if ~200% is acceptable base size.
     */
    private fun sizeCompareDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("SIZE TEST", PrintEmphasis.EMPHASIZED),
                    PrintableLine(""),
                    PrintableLine("CURRENT 100%", PrintEmphasis.EMPHASIZED),
                    PrintableLine("ABCDEFGHIJKLMNOP", textScale = PrintTextScale.NORMAL),
                    PrintableLine("Pizza Margherita EUR 7,00", textScale = PrintTextScale.NORMAL),
                    PrintableLine(""),
                    PrintableLine("NATIVE 200%", PrintEmphasis.EMPHASIZED),
                    PrintableLine("ABCDEFGHIJKLMNOP", textScale = PrintTextScale.DOUBLE_BOTH),
                    PrintableLine(
                        "Pizza Margherita EUR 7,00",
                        textScale = PrintTextScale.DOUBLE_BOTH,
                    ),
                ),
        )

    /**
     * Visual preview of **current** production receipt styling via
     * [DefaultReceiptComposer] + [DefaultEscPosEncoder]. Synthetic ACCEPTED order only —
     * no DB writes, no ReceiptComposer changes, no new ESC/POS scales/alignments.
     */
    private fun orderPreviewDocument(charsPerLine: Int): PrintableDocument {
        val order = syntheticPreviewOrder()
        return DefaultReceiptComposer().compose(
            order = order,
            kind = PrintKind.FINAL,
            pricePrintMode = PricePrintMode.DETAILED,
            charsPerLine = charsPerLine,
        )
    }

    /**
     * Full synthetic DETAILED receipt with **global** [PrintTextScale.DOUBLE_BOTH]
     * (GS ! 0x11 — 2× width + 2× height). Production [DefaultReceiptComposer] unchanged —
     * scale applied test-only after compose; emphasis/alignment preserved.
     *
     * charsPerLine=21 is TEST-ONLY / TO BE CALIBRATED (≈ half of NORMAL@42).
     */
    private fun orderPreview2xDocument(charsPerLine: Int): PrintableDocument {
        val composed = orderPreviewDocument(charsPerLine)
        return composed.copy(
            lines =
                composed.lines.map { line ->
                    line.copy(textScale = PrintTextScale.DOUBLE_BOTH)
                },
        )
    }

    private fun syntheticPreviewOrder(): Order {
        val fixed = Instant.parse("2026-09-16T16:00:00Z")
        val items =
            listOf(
                item(
                    id = "prev-pizza-margherita",
                    name = "Margherita",
                    category = ProductCategory.PIZZA,
                    quantity = 1,
                    unitCents = 700,
                    sequence = 1,
                ),
                item(
                    id = "prev-pizza-diavola",
                    name = "Diavola",
                    category = ProductCategory.PIZZA,
                    quantity = 2,
                    unitCents = 800,
                    sequence = 2,
                    note = "ben cotta",
                    additions =
                        listOf(
                            addition("prev-add-bufala", "Bufala", chargedCents = 200, displayOrder = 1),
                            addition("prev-add-prosciutto", "Prosciutto", chargedCents = 150, displayOrder = 2),
                        ),
                    removals =
                        listOf(
                            removal("prev-rem-cipolla", "Cipolla", displayOrder = 1),
                        ),
                ),
                item(
                    id = "prev-frittura-crocche",
                    name = "Crocche",
                    category = ProductCategory.FRITTURA,
                    quantity = 2,
                    unitCents = 300,
                    sequence = 3,
                ),
                item(
                    id = "prev-frittura-frittatina",
                    name = "Frittatina",
                    category = ProductCategory.FRITTURA,
                    quantity = 1,
                    unitCents = 350,
                    sequence = 4,
                ),
                item(
                    id = "prev-bibita-cola",
                    name = "Coca-Cola",
                    category = ProductCategory.BIBITA,
                    quantity = 1,
                    unitCents = 250,
                    sequence = 5,
                ),
                item(
                    id = "prev-bibita-acqua",
                    name = "Acqua",
                    category = ProductCategory.BIBITA,
                    quantity = 1,
                    unitCents = 100,
                    sequence = 6,
                ),
            )
        val totalCents =
            items.sumOf { item ->
                item.finalUnitPrice.cents * item.quantity +
                    item.additions.sumOf { it.chargedPrice.cents * item.quantity }
            }
        return Order(
            id = "hw001-order-preview-2x",
            status = OrderStatus.ACCEPTED,
            displayNumber = "127",
            numberingMode = NumberingMode.SEQUENTIAL,
            numberingCycle = 1,
            businessDate = LocalDate.of(2026, 9, 16),
            createdAt = fixed,
            updatedAt = fixed,
            acceptedAt = fixed,
            total = Money.ofCents(totalCents),
            generalNote = "preparare tutto insieme",
            sourceOrderId = null,
            items = items,
        )
    }

    private fun item(
        id: String,
        name: String,
        category: ProductCategory,
        quantity: Int,
        unitCents: Long,
        sequence: Int,
        note: String? = null,
        additions: List<OrderItemAddition> = emptyList(),
        removals: List<OrderItemRemoval> = emptyList(),
    ): OrderItem =
        OrderItem(
            id = id,
            productId = null,
            productNameSnapshot = name,
            productPrintedNameSnapshot = name,
            categorySnapshot = category,
            quantity = quantity,
            baseUnitPrice = Money.ofCents(unitCents),
            automaticExtrasTotal = Money.ZERO,
            manualUnitPrice = null,
            finalUnitPrice = Money.ofCents(unitCents),
            automaticExtrasPricingSnapshot = true,
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
            additionId = null,
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
            ingredientId = null,
            nameSnapshot = name,
            displayOrder = displayOrder,
        )

    private fun widthDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("HW-001 WIDTH", PrintEmphasis.EMPHASIZED),
                    PrintableLine(""),
                    rulerLine(32),
                    PrintableLine(""),
                    rulerLine(42),
                    PrintableLine(""),
                    rulerLine(48),
                ),
        )

    private fun formatDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("HW-001 FORMAT", PrintEmphasis.EMPHASIZED),
                    PrintableLine(""),
                    PrintableLine("LEFT", alignment = PrintAlignment.LEFT),
                    PrintableLine("CENTER", alignment = PrintAlignment.CENTER),
                    PrintableLine("RIGHT", alignment = PrintAlignment.RIGHT),
                    PrintableLine(""),
                    PrintableLine("NORMAL", emphasis = PrintEmphasis.NORMAL),
                    PrintableLine("BOLD", emphasis = PrintEmphasis.EMPHASIZED),
                    PrintableLine(""),
                    PrintableLine("NORMAL SIZE", textScale = PrintTextScale.NORMAL),
                    PrintableLine("DOUBLE HEIGHT", textScale = PrintTextScale.DOUBLE_HEIGHT),
                    PrintableLine("DOUBLE WIDTH", textScale = PrintTextScale.DOUBLE_WIDTH),
                    PrintableLine("DOUBLE BOTH", textScale = PrintTextScale.DOUBLE_BOTH),
                ),
        )

    private fun codePageDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("HW-001 CODE PAGE", PrintEmphasis.EMPHASIZED),
                    PrintableLine(""),
                    PrintableLine("à"),
                    PrintableLine("è"),
                    PrintableLine("é"),
                    PrintableLine("ì"),
                    PrintableLine("ò"),
                    PrintableLine("ù"),
                    PrintableLine("€"),
                    PrintableLine(""),
                    PrintableLine("a' e' e-accent i' o' u' euro"),
                    PrintableLine("à è é ì ò ù €"),
                ),
        )

    private fun feedDocument(): PrintableDocument =
        PrintableDocument(
            kind = PrintKind.DRAFT,
            lines =
                listOf(
                    PrintableLine("HW-001 FEED START", PrintEmphasis.EMPHASIZED),
                    PrintableLine("observe feedLines after END"),
                    PrintableLine("HW-001 FEED END", PrintEmphasis.EMPHASIZED),
                ),
        )

    private fun rulerLine(width: Int): PrintableLine {
        val digits = buildString(width) {
            for (i in 0 until width) {
                append(((i % 10) + '0'.code).toChar())
            }
        }
        require(digits.length == width) { "ruler must be exactly $width chars" }
        return PrintableLine("[$width] $digits")
    }

    private fun parsePositiveIntArg(
        raw: String?,
        default: Int,
    ): Int {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return default
        val value = trimmed.toIntOrNull()
        assertTrue("`$ARG_CHARS_PER_LINE` must be a positive int; got: '$raw'", value != null && value > 0)
        return value!!
    }

    private fun parseNonNegativeIntArg(
        raw: String?,
        default: Int,
    ): Int {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return default
        val value = trimmed.toIntOrNull()
        assertTrue("`$ARG_FEED_LINES` must be a non-negative int; got: '$raw'", value != null && value >= 0)
        return value!!
    }

    private fun parseOptionalSelector(raw: String?): Int? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toIntOrNull()
        assertTrue(
            "`$ARG_ESC_POS_CODE_TABLE` must be an int 0..255 when provided; got: '$raw'",
            value != null && value in 0..255,
        )
        return value
    }

    private fun containsGsV(bytes: ByteArray): Boolean {
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x56.toByte()) {
                return true
            }
        }
        return false
    }

    private enum class CalibrationSection {
        WIDTH,
        FORMAT,
        CODEPAGE,
        FEED,
        ORDER_PREVIEW,
        ORDER_PREVIEW_2X,
        SIZE_COMPARE,
    }

    companion object {
        private const val TAG = "EscPosCalibrationHwTest"
        const val ARG_PRINTER_ID: String = "printerId"
        const val ARG_CALIBRATION_SECTION: String = "calibrationSection"
        const val ARG_CHARS_PER_LINE: String = "charsPerLine"
        const val ARG_CODE_PAGE: String = "codePage"
        const val ARG_ESC_POS_CODE_TABLE: String = "escPosCodeTable"
        const val ARG_FEED_LINES: String = "feedLines"

        private const val DEFAULT_CHARS_PER_LINE: Int = 32
        private const val DEFAULT_CODE_PAGE: String = "ISO-8859-1"
        private const val DEFAULT_FEED_LINES: Int = 2
    }
}
