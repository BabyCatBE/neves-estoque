package com.babycatbe.nevesestoque.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babycatbe.nevesestoque.feature.auth.AuthUiState
import com.babycatbe.nevesestoque.feature.conferences.ProductConferenceRoute
import com.babycatbe.nevesestoque.feature.conferences.ProductStockUpdateRoute
import com.babycatbe.nevesestoque.feature.home.HomeScreen
import com.babycatbe.nevesestoque.feature.home.homeModules
import com.babycatbe.nevesestoque.feature.module.ModulePlaceholderScreen
import com.babycatbe.nevesestoque.feature.products.CatalogTrashScreen
import com.babycatbe.nevesestoque.feature.products.CategoriesScreen
import com.babycatbe.nevesestoque.feature.products.CategoryFormRoute
import com.babycatbe.nevesestoque.feature.products.ProductDetailRoute
import com.babycatbe.nevesestoque.feature.products.ProductFormRoute
import com.babycatbe.nevesestoque.feature.products.ProductMergeRoute
import com.babycatbe.nevesestoque.feature.products.ProductsHubScreen
import com.babycatbe.nevesestoque.feature.products.ProductsListRoute
import com.babycatbe.nevesestoque.feature.stock.StockRoute
import com.babycatbe.nevesestoque.feature.suppliers.SupplierDetailRoute
import com.babycatbe.nevesestoque.feature.suppliers.SupplierFormRoute
import com.babycatbe.nevesestoque.feature.suppliers.SuppliersRoute

private const val HOME_ROUTE = "home"
private const val MODULE_ROUTE = "module/{route}"
private const val PRODUCTS_LIST_ROUTE = "products/list"
private const val PRODUCTS_CATEGORY_ROUTE = "products/list/category/{categoryId}"
private const val CATEGORIES_ROUTE = "products/categories"
private const val CATALOG_TRASH_ROUTE = "products/trash"
private const val PRODUCT_DETAIL_ROUTE = "product/{productId}"
private const val PRODUCT_CREATE_ROUTE = "products/new"
private const val PRODUCT_EDIT_ROUTE = "product/{productId}/edit"
private const val PRODUCT_STOCK_ROUTE = "product/{productId}/stock"
private const val PRODUCT_CONFERENCE_ROUTE = "product/{productId}/stock/conference"
private const val PRODUCT_MERGE_ROUTE = "product/{productId}/merge"
private const val CATEGORY_CREATE_ROUTE = "products/categories/new"
private const val CATEGORY_EDIT_ROUTE = "products/categories/{categoryId}/edit"
private const val SUPPLIER_DETAIL_ROUTE = "supplier/{supplierId}"
private const val SUPPLIER_CREATE_ROUTE = "suppliers/new"
private const val SUPPLIER_EDIT_ROUTE = "supplier/{supplierId}/edit"
private const val REFRESH_KEY = "catalog-refresh"
private const val NOTICE_KEY = "catalog-notice"

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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()

            when (route) {
                "estoque" -> StockRoute(
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate("product/$it") },
                    refreshKey = refreshKey,
                )
                "produtos" -> ProductsHubScreen(
                    onBack = { navController.popBackStack() },
                    onProducts = { navController.navigate(PRODUCTS_LIST_ROUTE) },
                    onCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onTrash = { navController.navigate(CATALOG_TRASH_ROUTE) },
                )
                "fornecedores" -> {
                    val notice by entry.savedStateHandle
                        .getStateFlow<String?>(NOTICE_KEY, null)
                        .collectAsState()
                    SuppliersRoute(
                        onBack = { navController.popBackStack() },
                        onSupplierClick = { navController.navigate("supplier/$it") },
                        onCreateSupplier = { navController.navigate(SUPPLIER_CREATE_ROUTE) },
                        refreshKey = refreshKey,
                        noticeMessage = notice,
                        onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
                    )
                }
                else -> ModulePlaceholderScreen(
                    title = module?.title ?: "Módulo",
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(PRODUCTS_LIST_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            ProductsListRoute(
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate("product/$it") },
                onCreateProduct = { navController.navigate(PRODUCT_CREATE_ROUTE) },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            PRODUCTS_CATEGORY_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            ProductsListRoute(
                categoryFilter = entry.arguments?.getString("categoryId"),
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate("product/$it") },
                onCreateProduct = { navController.navigate(PRODUCT_CREATE_ROUTE) },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(CATEGORIES_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onCategoryClick = { navController.navigate("products/list/category/$it") },
                onCreateCategory = { navController.navigate(CATEGORY_CREATE_ROUTE) },
                onEditCategory = { navController.navigate("products/categories/$it/edit") },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            PRODUCT_DETAIL_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            ProductDetailRoute(
                productId = productId,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        REFRESH_KEY,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
                onEdit = { navController.navigate("product/$productId/edit") },
                onUpdateStock = { navController.navigate("product/$productId/stock") },
                onMerge = { navController.navigate("product/$productId/merge") },
                onDeleted = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            PRODUCT_MERGE_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            ProductMergeRoute(
                productId = productId,
                onBack = { navController.popBackStack() },
                onMerged = { survivorProductId, message ->
                    navController.popBackStack(PRODUCT_DETAIL_ROUTE, inclusive = true)
                    navController.navigate("product/$survivorProductId")
                    navController.currentBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                },
            )
        }

        composable(
            PRODUCT_STOCK_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            ProductStockUpdateRoute(
                productId = productId,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        REFRESH_KEY,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
                onConference = { navController.navigate("product/$productId/stock/conference") },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            PRODUCT_CONFERENCE_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            ProductConferenceRoute(
                productId = productId,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(
            SUPPLIER_DETAIL_ROUTE,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType }),
        ) { entry ->
            val supplierId = entry.arguments?.getString("supplierId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsState()
            SupplierDetailRoute(
                supplierId = supplierId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("supplier/$supplierId/edit") },
                onDeleted = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(SUPPLIER_CREATE_ROUTE) {
            SupplierFormRoute(
                supplierId = null,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(
            SUPPLIER_EDIT_ROUTE,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType }),
        ) { entry ->
            val supplierId = entry.arguments?.getString("supplierId").orEmpty()
            SupplierFormRoute(
                supplierId = supplierId,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(CATALOG_TRASH_ROUTE) {
            CatalogTrashScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(PRODUCT_CREATE_ROUTE) {
            ProductFormRoute(
                productId = null,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(
            PRODUCT_EDIT_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            ProductFormRoute(
                productId = productId,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(CATEGORY_CREATE_ROUTE) {
            CategoryFormRoute(
                categoryId = null,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable(
            CATEGORY_EDIT_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            val categoryId = entry.arguments?.getString("categoryId").orEmpty()
            CategoryFormRoute(
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
