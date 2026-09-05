package it.krpng.cassa.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.krpng.cassa.feature.archive.ArchiveScreen
import it.krpng.cassa.feature.home.HomeScreen
import it.krpng.cassa.feature.menu.MenuRoute
import it.krpng.cassa.feature.order.NewOrderScreen
import it.krpng.cassa.feature.settings.SettingsScreen
import it.krpng.cassa.feature.todayorders.TodayOrdersScreen

@Composable
fun CassaNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CassaDestination.HOME.route,
    ) {
        composable(CassaDestination.HOME.route) {
            HomeScreen(
                onNewOrder = { navController.navigate(CassaDestination.NEW_ORDER.route) },
                onTodayOrders = { navController.navigate(CassaDestination.TODAY_ORDERS.route) },
                onArchive = { navController.navigate(CassaDestination.ARCHIVE.route) },
                onMenu = { navController.navigate(CassaDestination.MENU.route) },
                onSettings = { navController.navigate(CassaDestination.SETTINGS.route) },
            )
        }
        composable(CassaDestination.NEW_ORDER.route) {
            NewOrderScreen(onBack = navController::navigateUp)
        }
        composable(CassaDestination.TODAY_ORDERS.route) {
            TodayOrdersScreen(onBack = navController::navigateUp)
        }
        composable(CassaDestination.ARCHIVE.route) {
            ArchiveScreen(onBack = navController::navigateUp)
        }
        composable(CassaDestination.MENU.route) {
            MenuRoute(onBack = navController::navigateUp)
        }
        composable(CassaDestination.SETTINGS.route) {
            SettingsScreen(onBack = navController::navigateUp)
        }
    }
}
