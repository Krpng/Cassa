package it.krpng.cassa.feature.importmenu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.krpng.cassa.data.ods.MenuImportField
import it.krpng.cassa.data.ods.MenuImportValidationError
import it.krpng.cassa.data.ods.MenuImportValidationErrorCode
import it.krpng.cassa.data.ods.NoRecognizedOdsSheetException
import it.krpng.cassa.data.ods.OdsImportPreviewResult
import it.krpng.cassa.data.ods.OdsMenuParseException
import it.krpng.cassa.data.ods.OdsSheetDetectionException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportPreviewUiState {
    val canConfirm: Boolean
        get() = this is Ready

    data object Loading : ImportPreviewUiState

    data class Ready(
        val summary: ImportPreviewSummary,
    ) : ImportPreviewUiState

    data class Invalid(
        val errors: List<ImportPreviewError>,
    ) : ImportPreviewUiState

    data class Failure(
        val message: String,
    ) : ImportPreviewUiState
}

data class ImportPreviewSummary(
    val productsToCreate: Int,
    val productsToUpdate: Int,
    val unchangedProducts: Int,
    val additionsToCreate: Int,
    val additionsToUpdate: Int,
    val unchangedAdditions: Int,
)

data class ImportPreviewError(
    val sheet: String,
    val row: Int,
    val field: String,
    val problem: String,
)

@HiltViewModel
class ImportPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewLoader: OdsImportPreviewLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ImportPreviewUiState>(ImportPreviewUiState.Loading)
    val uiState: StateFlow<ImportPreviewUiState> = _uiState.asStateFlow()

    init {
        val documentUri = savedStateHandle.get<String>(DOCUMENT_URI_ARGUMENT)
        if (documentUri.isNullOrBlank()) {
            _uiState.value = ImportPreviewUiState.Failure(
                message = "Nessun file ODS selezionato.",
            )
        } else {
            loadPreview(documentUri)
        }
    }

    private fun loadPreview(documentUri: String) {
        viewModelScope.launch {
            try {
                _uiState.value = when (val result = previewLoader.load(documentUri)) {
                    is OdsImportPreviewResult.Ready -> ImportPreviewUiState.Ready(
                        summary = ImportPreviewSummary(
                            productsToCreate = result.plan.productsToCreate.size,
                            productsToUpdate = result.plan.productsToUpdate.size,
                            unchangedProducts = result.plan.unchangedProducts.size,
                            additionsToCreate = result.plan.additionsToCreate.size,
                            additionsToUpdate = result.plan.additionsToUpdate.size,
                            unchangedAdditions = result.plan.unchangedAdditions.size,
                        ),
                    )

                    is OdsImportPreviewResult.Invalid -> ImportPreviewUiState.Invalid(
                        errors = result.errors.map { error -> error.toUiError() },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = ImportPreviewUiState.Failure(error.safeMessage())
            }
        }
    }

    private fun MenuImportValidationError.toUiError(): ImportPreviewError =
        ImportPreviewError(
            sheet = sourceSheet?.takeIf(String::isNotBlank) ?: "Senza nome",
            row = sourceRow,
            field = field.displayName(),
            problem = code.displayMessage(),
        )

    private fun MenuImportField.displayName(): String = when (this) {
        MenuImportField.PRODUCT_NAME -> "Prodotto"
        MenuImportField.TAKEAWAY_PRICE -> "Prezzo Asporto"
        MenuImportField.CATEGORY -> "Categoria"
        MenuImportField.ADDITION_NAME -> "Aggiunta"
        MenuImportField.ADDITION_PRICE -> "Prezzo aggiunta"
    }

    private fun MenuImportValidationErrorCode.displayMessage(): String = when (this) {
        MenuImportValidationErrorCode.REQUIRED_VALUE_MISSING -> "Valore obbligatorio mancante."
        MenuImportValidationErrorCode.INVALID_PRICE -> "Prezzo non numerico."
        MenuImportValidationErrorCode.NEGATIVE_PRICE -> "Il prezzo non può essere negativo."
        MenuImportValidationErrorCode.TOO_MANY_DECIMALS ->
            "Il prezzo può avere al massimo due decimali."
        MenuImportValidationErrorCode.PRICE_OVERFLOW -> "Il prezzo è troppo grande."
        MenuImportValidationErrorCode.UNSUPPORTED_CELL_TYPE ->
            "Il tipo della cella non è supportato."
        MenuImportValidationErrorCode.UNKNOWN_CATEGORY -> "Categoria non riconosciuta."
        MenuImportValidationErrorCode.DUPLICATE_NORMALIZED_NAME ->
            "Nome duplicato nello stesso file."
    }

    private fun Exception.safeMessage(): String = when (this) {
        is OdsDocumentOpenException,
        is OdsMenuParseException,
        is OdsSheetDetectionException,
        is NoRecognizedOdsSheetException -> message ?: "Impossibile leggere il file ODS."

        else -> "Impossibile preparare l'anteprima dell'importazione."
    }

    companion object {
        const val DOCUMENT_URI_ARGUMENT = "documentUri"
    }
}
