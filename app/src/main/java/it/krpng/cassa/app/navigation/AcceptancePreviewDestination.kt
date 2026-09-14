package it.krpng.cassa.app.navigation

import it.krpng.cassa.feature.acceptance.AcceptancePreviewViewModel

object AcceptancePreviewDestination {
    private const val BASE_ROUTE = "acceptance_preview"

    val routePattern: String =
        "$BASE_ROUTE/{${AcceptancePreviewViewModel.DRAFT_ID_ARGUMENT}}"

    fun createRoute(draftId: String): String = "$BASE_ROUTE/$draftId"
}
