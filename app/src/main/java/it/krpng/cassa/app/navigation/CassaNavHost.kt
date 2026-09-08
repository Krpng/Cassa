package it.krpng.cassa.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.krpng.cassa.feature.archive.ArchiveScreen
import it.krpng.cassa.feature.home.DraftRecoveryRoute
import it.krpng.cassa.feature.home.HomeRoute
import it.krpng.cassa.feature.importmenu.ImportPreviewRoute
import it.krpng.cassa.feature.importmenu.ImportPreviewViewModel
import it.krpng.cassa.feature.menu.AdditionEditRoute
import it.krpng.cassa.feature.menu.AdditionEditViewModel
import it.krpng.cassa.feature.menu.MenuRoute
import it.krpng.cassa.feature.menu.ProductEditRoute
import it.krpng.cassa.feature.menu.ProductEditViewModel
import it.krpng.cassa.feature.order.NewOrderRoute
import it.krpng.cassa.feature.order.NewOrderViewModel
import it.krpng.cassa.feature.order.OrderItemDetailRoute
import it.krpng.cassa.feature.order.OrderItemDetailViewModel
import it.krpng.cassa.feature.settings.SettingsScreen
import it.krpng.cassa.feature.todayorders.TodayOrdersScreen

@Composable
fun CassaNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CassaDestination.DRAFT_RECOVERY.route,
    ) {
        composable(CassaDestination.DRAFT_RECOVERY.route) {
            DraftRecoveryRoute(
                onContinueToHome = {
                    navController.navigate(CassaDestination.HOME.route) {
                        popUpTo(CassaDestination.DRAFT_RECOVERY.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onResumeDraft = { draftId ->
                    navController.navigate(CassaDestination.HOME.route) {
                        popUpTo(CassaDestination.DRAFT_RECOVERY.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                    navController.navigate(NewOrderDestination.createRoute(draftId))
                },
            )
        }
        composable(CassaDestination.HOME.route) {
            HomeRoute(
                onOpenDraft = { draftId ->
                    navController.navigate(NewOrderDestination.createRoute(draftId))
                },
                onTodayOrders = { navController.navigate(CassaDestination.TODAY_ORDERS.route) },
                onArchive = { navController.navigate(CassaDestination.ARCHIVE.route) },
                onMenu = { navController.navigate(CassaDestination.MENU.route) },
                onSettings = { navController.navigate(CassaDestination.SETTINGS.route) },
            )
        }
        composable(
            route = NewOrderDestination.routePattern,
            arguments = listOf(
                navArgument(NewOrderViewModel.DRAFT_ID_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
        ) {
            NewOrderRoute(
                onBack = navController::navigateUp,
                onOrderItemSelected = { orderId, orderItemId ->
                    navController.navigate(
                        OrderItemDetailDestination.createRoute(orderId, orderItemId),
                    )
                },
            )
        }
        composable(
            route = OrderItemDetailDestination.routePattern,
            arguments = listOf(
                navArgument(OrderItemDetailViewModel.ORDER_ID_ARGUMENT) {
                    type = NavType.StringType
                },
                navArgument(OrderItemDetailViewModel.ORDER_ITEM_ID_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
        ) {
            OrderItemDetailRoute(
                onBack = navController::navigateUp,
                onSaved = navController::navigateUp,
            )
        }
        composable(CassaDestination.TODAY_ORDERS.route) {
            TodayOrdersScreen(onBack = navController::navigateUp)
        }
        composable(CassaDestination.ARCHIVE.route) {
            ArchiveScreen(onBack = navController::navigateUp)
        }
        composable(CassaDestination.MENU.route) {
            MenuRoute(
                onBack = navController::navigateUp,
                onCreateProduct = { category ->
                    navController.navigate(ProductEditDestination.createRoute(category = category))
                },
                onEditProduct = { productId ->
                    navController.navigate(ProductEditDestination.createRoute(productId = productId))
                },
                onCreateAddition = {
                    navController.navigate(AdditionEditDestination.createRoute())
                },
                onEditAddition = { additionId ->
                    navController.navigate(AdditionEditDestination.createRoute(additionId))
                },
                onOdsDocumentSelected = { documentUri ->
                    navController.navigate(ImportPreviewDestination.createRoute(documentUri))
                },
            )
        }
        composable(
            route = ImportPreviewDestination.routePattern,
            arguments = listOf(
                navArgument(ImportPreviewViewModel.DOCUMENT_URI_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
        ) {
            ImportPreviewRoute(
                onBack = navController::navigateUp,
                onCancel = navController::navigateUp,
                onImported = navController::navigateUp,
            )
        }
        composable(
            route = ProductEditDestination.routePattern,
            arguments = listOf(
                navArgument(ProductEditViewModel.PRODUCT_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = ProductEditDestination.NEW_PRODUCT_ID
                },
                navArgument(ProductEditViewModel.CATEGORY_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
        ) {
            ProductEditRoute(
                onBack = navController::navigateUp,
                onSaved = navController::navigateUp,
            )
        }
        composable(
            route = AdditionEditDestination.routePattern,
            arguments = listOf(
                navArgument(AdditionEditViewModel.ADDITION_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AdditionEditDestination.NEW_ADDITION_ID
                },
            ),
        ) {
            AdditionEditRoute(
                onBack = navController::navigateUp,
                onSaved = navController::navigateUp,
            )
        }
        composable(CassaDestination.SETTINGS.route) {
            SettingsScreen(onBack = navController::navigateUp)
        }
    }
}
