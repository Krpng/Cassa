package it.krpng.cassa.app.navigation

import it.krpng.cassa.feature.menu.AdditionEditViewModel

object AdditionEditDestination {
    private const val BASE_ROUTE = "addition_edit"

    const val NEW_ADDITION_ID = -1L

    val routePattern: String =
        "$BASE_ROUTE?${AdditionEditViewModel.ADDITION_ID_ARGUMENT}={${AdditionEditViewModel.ADDITION_ID_ARGUMENT}}"

    fun createRoute(additionId: Long = NEW_ADDITION_ID): String =
        "$BASE_ROUTE?${AdditionEditViewModel.ADDITION_ID_ARGUMENT}=$additionId"
}
