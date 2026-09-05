package it.krpng.cassa.feature.importmenu

object OdsDocumentSelection {
    const val MIME_TYPE: String = "application/vnd.oasis.opendocument.spreadsheet"

    fun pickerMimeTypes(): Array<String> = arrayOf(MIME_TYPE)
}
