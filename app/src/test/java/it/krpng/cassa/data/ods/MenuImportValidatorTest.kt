package it.krpng.cassa.data.ods

import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuImportValidatorTest {
    private val validator = MenuImportValidator()

    @Test
    fun `validates products and accepts zero-priced additions`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(
                    row = 2,
                    name = "Margerita",
                    price = "7,50",
                    category = "  PÌZZE ",
                    printedName = present("MARG"),
                ),
            ),
            additionRows = listOf(
                additionRow(row = 2, name = "Pomodoro", price = "0,00"),
            ),
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(
            ValidatedProductImport(
                sourceSheet = "Prodotti",
                sourceRow = 2,
                name = "Margerita",
                normalizedName = "margerita",
                price = Money.ofCents(750),
                category = ProductCategory.PIZZA,
                printedName = ValidatedOptionalField.ColumnPresent("MARG"),
                ingredients = ValidatedOptionalField.ColumnAbsent,
            ),
            result.data.products.single(),
        )
        assertEquals(Money.ZERO, result.data.additions.single().price)
    }

    @Test
    fun `ODS-004 partial product row reports every missing required field`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(row = 7, name = "Marinara", price = null, category = null),
            ),
            additionRows = emptyList(),
        )

        assertFalse(result.isValid)
        assertEquals(
            listOf(MenuImportField.TAKEAWAY_PRICE, MenuImportField.CATEGORY),
            result.errors.map(MenuImportValidationError::field),
        )
        assertTrue(result.errors.all {
            it.code == MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING &&
                it.sourceSheet == "Prodotti" && it.sourceRow == 7
        })
    }

    @Test
    fun `blank names are required for both product and addition`() {
        val result = validator.validate(
            productRows = listOf(productRow(row = 3, name = "  ", price = "7", category = "Pizze")),
            additionRows = listOf(additionRow(row = 4, name = null, price = "1")),
        )

        assertEquals(
            listOf(MenuImportField.PRODUCT_NAME, MenuImportField.ADDITION_NAME),
            result.errors.map(MenuImportValidationError::field),
        )
        assertTrue(result.errors.all {
            it.code == MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING
        })
    }

    @Test
    fun `ODS-005 invalid product price and price detail failures are typed`() {
        val rows = listOf(
            productRow(row = 2, name = "A", price = "Gorgonzola", category = "Pizze"),
            productRow(row = 3, name = "B", price = "-1", category = "Pizze"),
            productRow(row = 4, name = "C", price = "1,234", category = "Pizze"),
            productRow(
                row = 5,
                name = "D",
                price = "92233720368547758,08",
                category = "Pizze",
            ),
        )

        val result = validator.validate(rows, emptyList())

        assertEquals(
            listOf(
                MenuImportValidationErrorCode.INVALID_PRICE,
                MenuImportValidationErrorCode.NEGATIVE_PRICE,
                MenuImportValidationErrorCode.TOO_MANY_DECIMALS,
                MenuImportValidationErrorCode.PRICE_OVERFLOW,
            ),
            result.errors.map(MenuImportValidationError::code),
        )
    }

    @Test
    fun `ODS-006 accepts only documented normalized categories`() {
        val validRows = listOf(
            productRow(row = 2, name = "Pizza", price = "1", category = " PÌZZE "),
            productRow(row = 3, name = "Fritto", price = "1", category = "FRITTURA"),
            productRow(row = 4, name = "Acqua", price = "1", category = "bibite"),
        )
        val invalid = productRow(row = 5, name = "Dolce", price = "1", category = "Dolci")

        val result = validator.validate(validRows + invalid, emptyList())

        assertEquals(
            listOf(ProductCategory.PIZZA, ProductCategory.FRITTURA, ProductCategory.BIBITA),
            result.data.products.map(ValidatedProductImport::category),
        )
        assertEquals(MenuImportValidationErrorCode.UNKNOWN_CATEGORY, result.errors.single().code)
        assertEquals("Dolci", result.errors.single().rawValue)
    }

    @Test
    fun `ODS-013 duplicate products use normalized identity and mark every duplicate`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(row = 2, name = "  Pìzza   Speciale ", price = "7", category = "Pizze"),
                productRow(row = 8, name = "PIZZA SPECIALE", price = "8", category = "Pizze"),
            ),
            additionRows = emptyList(),
        )

        assertTrue(result.data.products.isEmpty())
        assertEquals(listOf(2, 8), result.errors.map(MenuImportValidationError::sourceRow))
        assertTrue(result.errors.all {
            it.code == MenuImportValidationErrorCode.DUPLICATE_NORMALIZED_NAME
        })
    }

    @Test
    fun `ODS-014 duplicate additions are blocked but product namespace stays separate`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(row = 2, name = "Mozzarella", price = "7", category = "Pizze"),
            ),
            additionRows = listOf(
                additionRow(row = 2, name = "Mozzarella", price = "1"),
                additionRow(row = 9, name = "MOZZARÈLLA", price = "2"),
            ),
        )

        assertEquals(1, result.data.products.size)
        assertTrue(result.data.additions.isEmpty())
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all {
            it.field == MenuImportField.ADDITION_NAME &&
                it.code == MenuImportValidationErrorCode.DUPLICATE_NORMALIZED_NAME
        })
    }

    @Test
    fun `ODS-015 preserves printed-name absent versus present blank for future compare`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(row = 2, name = "Marinara", price = "6", category = "Pizze"),
                productRow(
                    row = 3,
                    name = "Margherita",
                    price = "7",
                    category = "Pizze",
                    printedName = present("  "),
                ),
            ),
            additionRows = listOf(
                additionRow(row = 2, name = "Pomodoro", price = "0", printedName = present("")),
            ),
        )

        assertEquals(ValidatedOptionalField.ColumnAbsent, result.data.products[0].printedName)
        assertEquals(
            ValidatedOptionalField.ColumnPresent<String>(null),
            result.data.products[1].printedName,
        )
        assertEquals(
            ValidatedOptionalField.ColumnPresent<String>(null),
            result.data.additions.single().printedName,
        )
    }

    @Test
    fun `ODS-016 ingredients trim deduplicate by normalized name and preserve first order`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(
                    row = 2,
                    name = "Speciale",
                    price = "9",
                    category = "Pizze",
                    ingredients = present(
                        " Pomodoro, , Prorcini, pomodòro,  Wrustel   e patate, Prorcini ",
                    ),
                ),
            ),
            additionRows = emptyList(),
        )

        val ingredients = (result.data.products.single().ingredients as
            ValidatedOptionalField.ColumnPresent).value

        assertEquals(
            listOf(
                ValidatedIngredientImport("Pomodoro", "pomodoro"),
                ValidatedIngredientImport("Prorcini", "prorcini"),
                ValidatedIngredientImport("Wrustel e patate", "wrustel e patate"),
            ),
            ingredients,
        )
    }

    @Test
    fun `ODS-017 and ODS-018 preserve ingredients present blank versus column absent`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(
                    row = 2,
                    name = "Con colonna",
                    price = "1",
                    category = "Pizze",
                    ingredients = present(""),
                ),
                productRow(row = 3, name = "Senza colonna", price = "1", category = "Pizze"),
            ),
            additionRows = emptyList(),
        )

        assertEquals(
            ValidatedOptionalField.ColumnPresent(emptyList<ValidatedIngredientImport>()),
            result.data.products[0].ingredients,
        )
        assertEquals(ValidatedOptionalField.ColumnAbsent, result.data.products[1].ingredients)
    }

    @Test
    fun `ODS-002 invalid Prezzo Sala is discarded before validation and never blocks`() {
        val detected = detectedProductSheetWithRoomPrice("valore non numerico")
        val rows = OdsProductRowParser().parse(detected)

        val result = validator.validate(rows, emptyList())

        assertTrue(result.isValid)
        assertEquals(Money.ofCents(600), result.data.products.single().price)
    }

    @Test
    fun `fully empty rows are ignored while partial rows become validation errors`() {
        val sheet = RawOdsSheet(
            name = "Prodotti",
            rows = listOf(
                rawRow("Prodotto", "Prezzo Asporto", "Categoria"),
                RawOdsRow(emptyList()),
                rawRow("", "", ""),
                rawRow("Marinara", "", "Pizze"),
            ),
        )
        val detected = requireNotNull(OdsSheetDetector().detect(RawMenuImport(listOf(sheet))).productSheet)
        val rows = OdsProductRowParser().parse(detected)

        val result = validator.validate(rows, emptyList())

        assertEquals(1, rows.size)
        assertEquals(1, result.errors.size)
        assertEquals(MenuImportField.TAKEAWAY_PRICE, result.errors.single().field)
    }

    @Test
    fun `collects the four known blocking addition errors with sheet row and field`() {
        val result = validator.validate(
            productRows = emptyList(),
            additionRows = listOf(
                additionRow(row = 31, name = "Cipolle", price = "Gorgonzola"),
                additionRow(row = 32, name = "Pomodoro sorrento", price = null),
                additionRow(row = 33, name = "Gorgonzola", price = ""),
                additionRow(row = 41, name = "Mignon", price = null),
            ),
        )

        assertFalse(result.isValid)
        assertEquals(4, result.errors.size)
        assertEquals(listOf(31, 32, 33, 41), result.errors.map(MenuImportValidationError::sourceRow))
        assertTrue(result.errors.all {
            it.sourceSheet == "Aggiunte" && it.field == MenuImportField.ADDITION_PRICE
        })
        assertEquals(MenuImportValidationErrorCode.INVALID_PRICE, result.errors[0].code)
        assertTrue(result.errors.drop(1).all {
            it.code == MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING
        })
    }

    @Test
    fun `preserves display spelling while normalizing only technical identity`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(
                    row = 2,
                    name = "  Margerita  ",
                    price = "7",
                    category = "Pizze",
                    ingredients = present("Prorcini, Wrustel e patate"),
                ),
            ),
            additionRows = emptyList(),
        )

        val product = result.data.products.single()
        assertEquals("Margerita", product.name)
        assertEquals("margerita", product.normalizedName)
        val ingredients = (product.ingredients as ValidatedOptionalField.ColumnPresent).value.orEmpty()
        assertEquals(listOf("Prorcini", "Wrustel e patate"), ingredients.map { it.name })
    }

    @Test
    fun `collects independent errors across rows instead of stopping at first failure`() {
        val result = validator.validate(
            productRows = listOf(
                productRow(row = 2, name = "", price = "bad", category = "Altro"),
            ),
            additionRows = listOf(
                additionRow(row = 7, name = "", price = "-1"),
            ),
        )

        assertEquals(5, result.errors.size)
        assertEquals(
            setOf(
                MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING,
                MenuImportValidationErrorCode.INVALID_PRICE,
                MenuImportValidationErrorCode.UNKNOWN_CATEGORY,
                MenuImportValidationErrorCode.NEGATIVE_PRICE,
            ),
            result.errors.map(MenuImportValidationError::code).toSet(),
        )
    }

    private fun productRow(
        row: Int,
        name: String?,
        price: String?,
        category: String?,
        printedName: RawOptionalOdsCell = RawOptionalOdsCell.ColumnAbsent,
        ingredients: RawOptionalOdsCell = RawOptionalOdsCell.ColumnAbsent,
    ): RawProductRow = RawProductRow(
        sheetName = "Prodotti",
        rowNumber = row,
        productName = name?.let(::cell),
        takeawayPrice = price?.let(::cell),
        category = category?.let(::cell),
        printedName = printedName,
        ingredients = ingredients,
    )

    private fun additionRow(
        row: Int,
        name: String?,
        price: String?,
        printedName: RawOptionalOdsCell = RawOptionalOdsCell.ColumnAbsent,
    ): RawAdditionRow = RawAdditionRow(
        sheetName = "Aggiunte",
        rowNumber = row,
        additionName = name?.let(::cell),
        price = price?.let(::cell),
        printedName = printedName,
    )

    private fun present(value: String): RawOptionalOdsCell =
        RawOptionalOdsCell.ColumnPresent(cell(value))

    private fun detectedProductSheetWithRoomPrice(roomPrice: String): DetectedProductSheet {
        val sheet = RawOdsSheet(
            name = "Catalogo",
            rows = listOf(
                rawRow("Prodotto", "Prezzo Sala", "Prezzo Asporto", "Categoria"),
                rawRow("Marinara", roomPrice, "6,00", "Pizze"),
            ),
        )
        return requireNotNull(OdsSheetDetector().detect(RawMenuImport(listOf(sheet))).productSheet)
    }

    private fun rawRow(vararg values: String): RawOdsRow = RawOdsRow(values.map(::cell))

    private fun cell(value: String): RawOdsCell = RawOdsCell(
        kind = if (value.isEmpty()) RawOdsCellKind.EMPTY else RawOdsCellKind.TEXT,
        text = value,
        rawValue = null,
        currencyCode = null,
    )
}
