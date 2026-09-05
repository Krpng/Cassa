package it.krpng.cassa.data.ods

data class RawAdditionRow(
    val sheetName: String?,
    val rowNumber: Int,
    val additionName: RawOdsCell?,
    val price: RawOdsCell?,
    val printedName: RawOptionalOdsCell,
)

class OdsAdditionRowParser {
    fun parse(sheet: DetectedAdditionSheet): List<RawAdditionRow> {
        require(sheet.headerRowIndex in sheet.source.rows.indices) {
            "L'indice della riga header non appartiene al foglio rilevato."
        }

        val additionNameColumn = sheet.requiredColumn(AdditionOdsColumn.PRODUCT)
        val priceColumn = sheet.requiredColumn(AdditionOdsColumn.PRICE)

        return sheet.source.rows
            .withIndex()
            .drop(sheet.headerRowIndex + 1)
            .filterNot { (_, row) -> row.isCompletelyEmpty() }
            .map { (rowIndex, row) ->
                RawAdditionRow(
                    sheetName = sheet.source.name,
                    rowNumber = row.sourceRow ?: rowIndex + 1,
                    additionName = row.cells.getOrNull(additionNameColumn),
                    price = row.cells.getOrNull(priceColumn),
                    printedName = row.optionalCell(sheet.columns[AdditionOdsColumn.PRINTED_NAME]),
                )
            }
    }

    private fun DetectedAdditionSheet.requiredColumn(column: AdditionOdsColumn): Int =
        requireNotNull(columns[column]) {
            "Il foglio aggiunte rilevato non contiene la colonna obbligatoria $column."
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
