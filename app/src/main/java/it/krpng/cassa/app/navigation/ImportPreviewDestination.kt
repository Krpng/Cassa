package it.krpng.cassa.app.navigation

import android.net.Uri
import it.krpng.cassa.feature.importmenu.ImportPreviewViewModel

object ImportPreviewDestination {
    private const val BASE_ROUTE = "import_preview"

    val routePattern: String =
        "$BASE_ROUTE/{${ImportPreviewViewModel.DOCUMENT_URI_ARGUMENT}}"

    fun createRoute(documentUri: Uri): String =
        "$BASE_ROUTE/${Uri.encode(documentUri.toString())}"
}
