package it.krpng.cassa.data.ods

sealed interface RawOptionalOdsCell {
    data object ColumnAbsent : RawOptionalOdsCell

    data class ColumnPresent(
        val cell: RawOdsCell?,
    ) : RawOptionalOdsCell
}

data class RawProductRow(
    val sheetName: String?,
    val rowNumber: Int,
    val productName: RawOdsCell?,
    val takeawayPrice: RawOdsCell?,
    val category: RawOdsCell?,
    val printedName: RawOptionalOdsCell,
    val ingredients: RawOptionalOdsCell,
)

class OdsProductRowParser {
    fun parse(sheet: DetectedProductSheet): List<RawProductRow> {
        require(sheet.headerRowIndex in sheet.source.rows.indices) {
            "L'indice della riga header non appartiene al foglio rilevato."
        }

        val productColumn = sheet.requiredColumn(ProductOdsColumn.PRODUCT)
        val takeawayPriceColumn = sheet.requiredColumn(ProductOdsColumn.TAKEAWAY_PRICE)
        val categoryColumn = sheet.requiredColumn(ProductOdsColumn.CATEGORY)

        return sheet.source.rows
            .withIndex()
            .drop(sheet.headerRowIndex + 1)
            .filterNot { (_, row) -> row.isCompletelyEmpty() }
            .map { (rowIndex, row) ->
                RawProductRow(
                    sheetName = sheet.source.name,
                    rowNumber = rowIndex + 1,
                    productName = row.cells.getOrNull(productColumn),
                    takeawayPrice = row.cells.getOrNull(takeawayPriceColumn),
                    category = row.cells.getOrNull(categoryColumn),
                    printedName = row.optionalCell(sheet.columns[ProductOdsColumn.PRINTED_NAME]),
                    ingredients = row.optionalCell(sheet.columns[ProductOdsColumn.INGREDIENTS]),
                )
            }
    }

    private fun DetectedProductSheet.requiredColumn(column: ProductOdsColumn): Int =
        requireNotNull(columns[column]) {
            "Il foglio prodotti rilevato non contiene la colonna obbligatoria $column."
        }

    private fun RawOdsRow.optionalCell(columnIndex: Int?): RawOptionalOdsCell =
        if (columnIndex == null) {
            RawOptionalOdsCell.ColumnAbsent
        } else {
            RawOptionalOdsCell.ColumnPresent(cells.getOrNull(columnIndex))
        }

    private fun RawOdsRow.isCompletelyEmpty(): Boolean = cells.all { cell ->
        cell.text.isBlank() && cell.rawValue.isNullOrBlank()
    }
}
