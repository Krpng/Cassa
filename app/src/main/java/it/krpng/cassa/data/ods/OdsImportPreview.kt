package it.krpng.cassa.data.ods

import it.krpng.cassa.domain.model.Addition
import it.krpng.cassa.domain.model.Product
import java.io.InputStream

sealed interface OdsImportPreviewResult {
    data class Ready(
        val plan: MenuImportPlan,
    ) : OdsImportPreviewResult

    data class Invalid(
        val errors: List<MenuImportValidationError>,
    ) : OdsImportPreviewResult
}
class NoRecognizedOdsSheetException : Exception(
    "Il file non contiene un foglio prodotti o aggiunte riconoscibile.",
)

class OdsImportPreviewProcessor(
    private val menuParser: OdsMenuParser = OdsMenuParser(),
    private val sheetDetector: OdsSheetDetector = OdsSheetDetector(),
    private val productRowParser: OdsProductRowParser = OdsProductRowParser(),
    private val additionRowParser: OdsAdditionRowParser = OdsAdditionRowParser(),
    private val validator: MenuImportValidator = MenuImportValidator(),
    private val planner: MenuImportPlanner = MenuImportPlanner(),
) {
    fun createPreview(
        input: InputStream,
        existingProducts: List<Product>,
        existingAdditions: List<Addition>,
    ): OdsImportPreviewResult {
        val detectedSheets = sheetDetector.detect(menuParser.parse(input))
        if (detectedSheets.productSheet == null && detectedSheets.additionSheet == null) {
            throw NoRecognizedOdsSheetException()
        }

        val validation = validator.validate(
            productRows = detectedSheets.productSheet
                ?.let(productRowParser::parse)
                .orEmpty(),
            additionRows = detectedSheets.additionSheet
                ?.let(additionRowParser::parse)
                .orEmpty(),
        )
        if (!validation.isValid) {
            return OdsImportPreviewResult.Invalid(errors = validation.errors)
        }

        return OdsImportPreviewResult.Ready(
            plan = planner.createPlan(
                validatedImport = validation.data,
                existingProducts = existingProducts,
                existingAdditions = existingAdditions,
            ),
        )
    }
}
