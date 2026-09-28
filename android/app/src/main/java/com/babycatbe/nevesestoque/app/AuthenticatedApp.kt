package com.babycatbe.nevesestoque.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babycatbe.nevesestoque.feature.auth.AuthUiState
import com.babycatbe.nevesestoque.feature.home.HomeScreen
import com.babycatbe.nevesestoque.feature.home.homeModules
import com.babycatbe.nevesestoque.feature.module.ModulePlaceholderScreen
import com.babycatbe.nevesestoque.feature.stock.StockRoute

private const val HOME_ROUTE = "home"
private const val MODULE_ROUTE = "module/{route}"

@Composable
fun AuthenticatedApp(authState: AuthUiState, onSignOut: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen(
                displayName = authState.displayName,
                roleName = authState.roleName,
                onSignOut = onSignOut,
                onModuleClick = { navController.navigate("module/${it.route}") },
            )
        }
        composable(
            route = MODULE_ROUTE,
            arguments = listOf(navArgument("route") { type = NavType.StringType }),
        ) { entry ->
            val route = entry.arguments?.getString("route")
            val module = homeModules.firstOrNull { it.route == route }

            if (route == "estoque") {
                StockRoute(onBack = { navController.popBackStack() })
            } else {
                ModulePlaceholderScreen(
                    title = module?.title ?: "Módulo",
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
