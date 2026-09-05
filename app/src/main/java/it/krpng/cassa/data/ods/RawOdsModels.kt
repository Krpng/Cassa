package it.krpng.cassa.data.ods

data class RawMenuImport(
    val sheets: List<RawOdsSheet>,
)

data class RawOdsSheet(
    val name: String?,
    val rows: List<RawOdsRow>,
)

data class RawOdsRow(
    val cells: List<RawOdsCell>,
    val sourceRow: Int? = null,
)

data class RawOdsCell(
    val kind: RawOdsCellKind,
    val text: String,
    val rawValue: String?,
    val currencyCode: String?,
)

enum class RawOdsCellKind {
    EMPTY,
    TEXT,
    NUMBER,
    CURRENCY,
    OTHER,
}
