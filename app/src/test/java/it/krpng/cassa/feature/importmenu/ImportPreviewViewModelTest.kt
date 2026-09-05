package it.krpng.cassa.feature.importmenu

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.data.ods.AdditionImportCreate
import it.krpng.cassa.data.ods.AdditionImportUpdate
import it.krpng.cassa.data.ods.AdditionImportValues
import it.krpng.cassa.data.ods.ImportFieldUpdate
import it.krpng.cassa.data.ods.MalformedOdsArchiveException
import it.krpng.cassa.data.ods.MenuImportField
import it.krpng.cassa.data.ods.MenuImportPlan
import it.krpng.cassa.data.ods.MenuImportValidationError
import it.krpng.cassa.data.ods.MenuImportValidationErrorCode
import it.krpng.cassa.data.ods.OdsImportPreviewResult
import it.krpng.cassa.data.ods.ProductImportCreate
import it.krpng.cassa.data.ods.ProductImportUpdate
import it.krpng.cassa.data.ods.ProductImportValues
import it.krpng.cassa.data.ods.UnchangedAdditionImport
import it.krpng.cassa.data.ods.UnchangedProductImport
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportPreviewViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selected document is forwarded and valid plan counts are exposed`() =
        runTest(mainDispatcher) {
            val loader = FakeLoader(OdsImportPreviewResult.Ready(plan()))
            val viewModel = viewModel(loader, "content://menu/file.ods")

            advanceUntilIdle()

            assertEquals(listOf("content://menu/file.ods"), loader.requestedUris)
            val state = viewModel.uiState.value as ImportPreviewUiState.Ready
            assertEquals(
                ImportPreviewSummary(
                    productsToCreate = 1,
                    productsToUpdate = 1,
                    unchangedProducts = 1,
                    additionsToCreate = 1,
                    additionsToUpdate = 1,
                    unchangedAdditions = 1,
                ),
                state.summary,
            )
            assertTrue(state.canConfirm)
        }

    @Test
    fun `multiple validation errors remain visible and block confirmation`() =
        runTest(mainDispatcher) {
            val loader = FakeLoader(
                OdsImportPreviewResult.Invalid(
                    errors = listOf(
                        error(
                            sheet = "Aggiunte",
                            row = 31,
                            field = MenuImportField.ADDITION_PRICE,
                            code = MenuImportValidationErrorCode.INVALID_PRICE,
                        ),
                        error(
                            sheet = "Aggiunte",
                            row = 32,
                            field = MenuImportField.ADDITION_PRICE,
                            code = MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING,
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(loader)

            advanceUntilIdle()

            val state = viewModel.uiState.value as ImportPreviewUiState.Invalid
            assertFalse(state.canConfirm)
            assertEquals(2, state.errors.size)
            assertEquals("Aggiunte", state.errors[0].sheet)
            assertEquals(31, state.errors[0].row)
            assertEquals("Prezzo aggiunta", state.errors[0].field)
            assertEquals("Prezzo non numerico.", state.errors[0].problem)
            assertEquals("Valore obbligatorio mancante.", state.errors[1].problem)
        }

    @Test
    fun `parser failure becomes safe readable error state`() = runTest(mainDispatcher) {
        val viewModel = viewModel(FakeLoader(MalformedOdsArchiveException()))

        advanceUntilIdle()

        val state = viewModel.uiState.value as ImportPreviewUiState.Failure
        assertFalse(state.canConfirm)
        assertEquals("Il file non è un archivio ODS valido.", state.message)
    }

    @Test
    fun `missing navigation argument fails without invoking pipeline`() = runTest(mainDispatcher) {
        val loader = FakeLoader(OdsImportPreviewResult.Ready(plan()))
        val viewModel = ImportPreviewViewModel(SavedStateHandle(), loader)

        advanceUntilIdle()

        assertTrue(loader.requestedUris.isEmpty())
        assertEquals(
            "Nessun file ODS selezionato.",
            (viewModel.uiState.value as ImportPreviewUiState.Failure).message,
        )
    }

    private fun viewModel(
        loader: OdsImportPreviewLoader,
        uri: String = "content://menu/test.ods",
    ): ImportPreviewViewModel = ImportPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(ImportPreviewViewModel.DOCUMENT_URI_ARGUMENT to uri),
        ),
        previewLoader = loader,
    )

    private fun error(
        sheet: String,
        row: Int,
        field: MenuImportField,
        code: MenuImportValidationErrorCode,
    ): MenuImportValidationError = MenuImportValidationError(
        sourceSheet = sheet,
        sourceRow = row,
        field = field,
        rawValue = null,
        code = code,
    )

    private fun plan(): MenuImportPlan = MenuImportPlan(
        productsToCreate = listOf(
            ProductImportCreate("Prodotti", 2, productValues("Nuovo")),
        ),
        productsToUpdate = listOf(
            ProductImportUpdate(1, "Prodotti", 3, productValues("Aggiornato")),
        ),
        unchangedProducts = listOf(UnchangedProductImport(2, "Prodotti", 4)),
        additionsToCreate = listOf(
            AdditionImportCreate("Aggiunte", 2, additionValues("Nuova")),
        ),
        additionsToUpdate = listOf(
            AdditionImportUpdate(3, "Aggiunte", 3, additionValues("Aggiornata")),
        ),
        unchangedAdditions = listOf(UnchangedAdditionImport(4, "Aggiunte", 4)),
    )

    private fun productValues(name: String): ProductImportValues = ProductImportValues(
        name = name,
        normalizedName = name.lowercase(),
        price = Money.ofCents(700),
        category = ProductCategory.PIZZA,
        printedName = ImportFieldUpdate.Replace(null),
        ingredients = ImportFieldUpdate.Replace(emptyList()),
    )

    private fun additionValues(name: String): AdditionImportValues = AdditionImportValues(
        name = name,
        normalizedName = name.lowercase(),
        price = Money.ofCents(100),
        printedName = ImportFieldUpdate.Replace(null),
    )

    private class FakeLoader(
        private val result: Any,
    ) : OdsImportPreviewLoader {
        val requestedUris = mutableListOf<String>()

        override suspend fun load(documentUri: String): OdsImportPreviewResult {
            requestedUris += documentUri
            if (result is Exception) throw result
            return result as OdsImportPreviewResult
        }
    }
}
