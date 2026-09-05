package it.krpng.cassa.data.ods

import it.krpng.cassa.core.normalization.TextNormalizer

enum class ProductOdsColumn {
    PRODUCT,
    TAKEAWAY_PRICE,
    ROOM_PRICE,
    PRINTED_NAME,
    CATEGORY,
    INGREDIENTS,
}

enum class AdditionOdsColumn {
    PRODUCT,
    PRICE,
    PRINTED_NAME,
}

data class DetectedProductSheet(
    val source: RawOdsSheet,
    val headerRowIndex: Int,
    val columns: Map<ProductOdsColumn, Int>,
)

data class DetectedAdditionSheet(
    val source: RawOdsSheet,
    val headerRowIndex: Int,
    val columns: Map<AdditionOdsColumn, Int>,
)

data class DetectedOdsSheets(
    val productSheet: DetectedProductSheet?,
    val additionSheet: DetectedAdditionSheet?,
    val emptySheets: List<RawOdsSheet>,
    val unclassifiedSheets: List<RawOdsSheet>,
)

sealed class OdsSheetDetectionException(message: String) : Exception(message)

class DuplicateOdsHeaderException(
    val sheetName: String?,
    val headerRowIndex: Int,
    val normalizedHeader: String,
) : OdsSheetDetectionException(
    "Il foglio ${sheetName.displayName()} contiene l'header duplicato " +
        "\"$normalizedHeader\" alla riga ${headerRowIndex + 1}.",
)

class AmbiguousOdsSheetException(
    val role: OdsSheetRole,
    val sheetNames: List<String?>,
) : OdsSheetDetectionException(
    "Più fogli corrispondono al ruolo ${role.displayName}: " +
        sheetNames.joinToString { it.displayName() } + ".",
)

class AmbiguousOdsSheetRoleException(
    val sheetName: String?,
    val headerRowIndex: Int,
) : OdsSheetDetectionException(
    "Il foglio ${sheetName.displayName()}, riga ${headerRowIndex + 1}, " +
        "corrisponde sia ai prodotti sia alle aggiunte.",
)

enum class OdsSheetRole(internal val displayName: String) {
    PRODUCTS("prodotti"),
    ADDITIONS("aggiunte"),
}

class OdsSheetDetector {
    fun detect(rawImport: RawMenuImport): DetectedOdsSheets {
        val productCandidates = mutableListOf<DetectedProductSheet>()
        val additionCandidates = mutableListOf<DetectedAdditionSheet>()
        val emptySheets = mutableListOf<RawOdsSheet>()
        val unclassifiedSheets = mutableListOf<RawOdsSheet>()

        rawImport.sheets.forEach { sheet ->
            if (sheet.isEmpty()) {
                emptySheets += sheet
                return@forEach
            }

            val sheetProducts = mutableListOf<DetectedProductSheet>()
            val sheetAdditions = mutableListOf<DetectedAdditionSheet>()

            sheet.rows.forEachIndexed { rowIndex, row ->
                val normalizedHeaders = row.normalizedHeaders()
                val headerNames = normalizedHeaders.map { it.normalizedName }.toSet()
                val matchesProducts = headerNames.containsAll(PRODUCT_REQUIRED_HEADERS)
                val matchesAdditions = headerNames.containsAll(ADDITION_REQUIRED_HEADERS)

                if (!matchesProducts && !matchesAdditions) return@forEachIndexed

                normalizedHeaders
                    .groupBy(NormalizedHeader::normalizedName)
                    .entries
                    .firstOrNull { (_, occurrences) -> occurrences.size > 1 }
                    ?.let { duplicate ->
                        throw DuplicateOdsHeaderException(
                            sheetName = sheet.name,
                            headerRowIndex = rowIndex,
                            normalizedHeader = duplicate.key,
                        )
                    }

                if (matchesProducts && matchesAdditions) {
                    throw AmbiguousOdsSheetRoleException(sheet.name, rowIndex)
                }

                if (matchesProducts) {
                    sheetProducts += DetectedProductSheet(
                        source = sheet,
                        headerRowIndex = rowIndex,
                        columns = normalizedHeaders.toProductColumns(),
                    )
                } else {
                    sheetAdditions += DetectedAdditionSheet(
                        source = sheet,
                        headerRowIndex = rowIndex,
                        columns = normalizedHeaders.toAdditionColumns(),
                    )
                }
            }

            productCandidates += sheetProducts
            additionCandidates += sheetAdditions
            if (sheetProducts.isEmpty() && sheetAdditions.isEmpty()) {
                unclassifiedSheets += sheet
            }
        }

        return DetectedOdsSheets(
            productSheet = productCandidates.singleOrAmbiguous(OdsSheetRole.PRODUCTS),
            additionSheet = additionCandidates.singleOrAmbiguous(OdsSheetRole.ADDITIONS),
            emptySheets = emptySheets,
            unclassifiedSheets = unclassifiedSheets,
        )
    }

    private fun RawOdsSheet.isEmpty(): Boolean = rows.all { row ->
        row.cells.all { cell ->
            TextNormalizer.normalize(cell.text).isEmpty() && cell.rawValue.isNullOrBlank()
        }
    }

    private fun RawOdsRow.normalizedHeaders(): List<NormalizedHeader> =
        cells.mapIndexedNotNull { index, cell ->
            TextNormalizer.normalize(cell.text)
                .takeIf(String::isNotEmpty)
                ?.let { normalizedName -> NormalizedHeader(normalizedName, index) }
        }

    private fun List<NormalizedHeader>.toProductColumns(): Map<ProductOdsColumn, Int> =
        mapNotNull { header ->
            PRODUCT_HEADERS[header.normalizedName]?.let { column -> column to header.columnIndex }
        }.toMap()

    private fun List<NormalizedHeader>.toAdditionColumns(): Map<AdditionOdsColumn, Int> =
        mapNotNull { header ->
            ADDITION_HEADERS[header.normalizedName]?.let { column -> column to header.columnIndex }
        }.toMap()

    private fun <T> List<T>.singleOrAmbiguous(role: OdsSheetRole): T? {
        if (size > 1) {
            val names = map { candidate ->
                when (candidate) {
                    is DetectedProductSheet -> candidate.source.name
                    is DetectedAdditionSheet -> candidate.source.name
                    else -> null
                }
            }
            throw AmbiguousOdsSheetException(role, names)
        }
        return singleOrNull()
    }

    private data class NormalizedHeader(
        val normalizedName: String,
        val columnIndex: Int,
    )

    private companion object {
        const val PRODUCT_HEADER = "prodotto"
        const val TAKEAWAY_PRICE_HEADER = "prezzo asporto"
        const val ROOM_PRICE_HEADER = "prezzo sala"
        const val PRINTED_NAME_HEADER = "nome stampato"
        const val CATEGORY_HEADER = "categoria"
        const val INGREDIENTS_HEADER = "ingredienti"
        const val PRICE_HEADER = "prezzo"

        val PRODUCT_REQUIRED_HEADERS = setOf(
            PRODUCT_HEADER,
            TAKEAWAY_PRICE_HEADER,
            CATEGORY_HEADER,
        )
        val ADDITION_REQUIRED_HEADERS = setOf(PRODUCT_HEADER, PRICE_HEADER)

        val PRODUCT_HEADERS = mapOf(
            PRODUCT_HEADER to ProductOdsColumn.PRODUCT,
            TAKEAWAY_PRICE_HEADER to ProductOdsColumn.TAKEAWAY_PRICE,
            ROOM_PRICE_HEADER to ProductOdsColumn.ROOM_PRICE,
            PRINTED_NAME_HEADER to ProductOdsColumn.PRINTED_NAME,
            CATEGORY_HEADER to ProductOdsColumn.CATEGORY,
            INGREDIENTS_HEADER to ProductOdsColumn.INGREDIENTS,
        )
        val ADDITION_HEADERS = mapOf(
            PRODUCT_HEADER to AdditionOdsColumn.PRODUCT,
            PRICE_HEADER to AdditionOdsColumn.PRICE,
            PRINTED_NAME_HEADER to AdditionOdsColumn.PRINTED_NAME,
        )
    }
}

private fun String?.displayName(): String = this?.let { "\"$it\"" } ?: "senza nome"
