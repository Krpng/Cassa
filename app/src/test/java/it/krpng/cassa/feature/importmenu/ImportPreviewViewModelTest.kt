package it.krpng.cassa.feature.importmenu

import androidx.lifecycle.SavedStateHandle
import it.krpng.cassa.data.ods.AdditionImportCreate
import it.krpng.cassa.data.ods.AdditionImportUpdate
import it.krpng.cassa.data.ods.AdditionImportValues
import it.krpng.cassa.data.ods.ImportFieldUpdate
import it.krpng.cassa.data.ods.MalformedOdsArchiveException
import it.krpng.cassa.data.ods.MenuImportCommitter
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
import it.krpng.cassa.data.ods.UnexpectedMenuImportException
import it.krpng.cassa.core.money.Money
import it.krpng.cassa.domain.model.ProductCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        val viewModel = ImportPreviewViewModel(SavedStateHandle(), loader, FakeCommitter())

        advanceUntilIdle()

        assertTrue(loader.requestedUris.isEmpty())
        assertEquals(
            "Nessun file ODS selezionato.",
            (viewModel.uiState.value as ImportPreviewUiState.Failure).message,
        )
    }

    @Test
    fun `confirmation commits the prepared plan once and reports completion`() =
        runTest(mainDispatcher) {
            val expectedPlan = plan()
            val committer = FakeCommitter()
            val viewModel = viewModel(
                loader = FakeLoader(OdsImportPreviewResult.Ready(expectedPlan)),
                committer = committer,
            )
            advanceUntilIdle()

            viewModel.confirmImport()
            advanceUntilIdle()

            assertEquals(listOf(expectedPlan), committer.committedPlans)
            assertEquals(ImportPreviewUiState.Imported, viewModel.uiState.value)
        }

    @Test
    fun `double tap while import is running starts only one commit`() =
        runTest(mainDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val committer = FakeCommitter(gate = gate)
            val viewModel = viewModel(
                loader = FakeLoader(OdsImportPreviewResult.Ready(plan())),
                committer = committer,
            )
            advanceUntilIdle()

            viewModel.confirmImport()
            viewModel.confirmImport()
            runCurrent()

            assertEquals(1, committer.committedPlans.size)
            assertFalse(viewModel.uiState.value.canConfirm)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(ImportPreviewUiState.Imported, viewModel.uiState.value)
        }

    @Test
    fun `commit failure stays in preview and permits retry`() = runTest(mainDispatcher) {
        val committer = FakeCommitter(
            failures = ArrayDeque(
                listOf(UnexpectedMenuImportException(IllegalStateException("test"))),
            ),
        )
        val viewModel = viewModel(
            loader = FakeLoader(OdsImportPreviewResult.Ready(plan())),
            committer = committer,
        )
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()

        val failedState = viewModel.uiState.value as ImportPreviewUiState.Ready
        assertTrue(failedState.canConfirm)
        assertEquals(
            "Errore imprevisto. Nessuna modifica è stata salvata.",
            failedState.importError,
        )

        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(2, committer.committedPlans.size)
        assertEquals(ImportPreviewUiState.Imported, viewModel.uiState.value)
    }

    private fun viewModel(
        loader: OdsImportPreviewLoader,
        uri: String = "content://menu/test.ods",
        committer: MenuImportCommitter = FakeCommitter(),
    ): ImportPreviewViewModel = ImportPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(ImportPreviewViewModel.DOCUMENT_URI_ARGUMENT to uri),
        ),
        previewLoader = loader,
        importCommitter = committer,
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

    private class FakeCommitter(
        private val gate: CompletableDeferred<Unit>? = null,
        private val failures: ArrayDeque<Exception> = ArrayDeque(),
    ) : MenuImportCommitter {
        val committedPlans = mutableListOf<MenuImportPlan>()

        override suspend fun commit(plan: MenuImportPlan) {
            committedPlans += plan
            gate?.await()
            if (failures.isNotEmpty()) throw failures.removeFirst()
        }
    }
}
