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
import com.babycatbe.nevesestoque.feature.products.CategoriesScreen
import com.babycatbe.nevesestoque.feature.products.ProductDetailRoute
import com.babycatbe.nevesestoque.feature.products.ProductsHubScreen
import com.babycatbe.nevesestoque.feature.products.ProductsListRoute
import com.babycatbe.nevesestoque.feature.stock.StockRoute

private const val HOME_ROUTE = "home"
private const val MODULE_ROUTE = "module/{route}"
private const val PRODUCTS_LIST_ROUTE = "products/list"
private const val PRODUCTS_CATEGORY_ROUTE = "products/list/category/{categoryId}"
private const val CATEGORIES_ROUTE = "products/categories"
private const val PRODUCT_DETAIL_ROUTE = "product/{productId}"

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

        composable(MODULE_ROUTE, arguments = listOf(navArgument("route") { type = NavType.StringType })) { entry ->
            val route = entry.arguments?.getString("route")
            val module = homeModules.firstOrNull { it.route == route }

            when (route) {
                "estoque" -> StockRoute(
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate("product/$it") },
                )
                "produtos" -> ProductsHubScreen(
                    onBack = { navController.popBackStack() },
                    onProducts = { navController.navigate(PRODUCTS_LIST_ROUTE) },
                    onCategories = { navController.navigate(CATEGORIES_ROUTE) },
                )
                else -> ModulePlaceholderScreen(
                    title = module?.title ?: "Módulo",
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(PRODUCTS_LIST_ROUTE) {
            ProductsListRoute(
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate("product/$it") },
            )
        }

        composable(
            PRODUCTS_CATEGORY_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            ProductsListRoute(
                categoryFilter = entry.arguments?.getString("categoryId"),
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate("product/$it") },
            )
        }

        composable(CATEGORIES_ROUTE) {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onCategoryClick = { navController.navigate("products/list/category/$it") },
            )
        }

        composable(
            PRODUCT_DETAIL_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            ProductDetailRoute(
                productId = productId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
