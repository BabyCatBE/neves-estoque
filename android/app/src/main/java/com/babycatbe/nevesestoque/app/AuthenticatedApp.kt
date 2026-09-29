package com.babycatbe.nevesestoque.app

import com.babycatbe.nevesestoque.ui.motion.NevesMotion
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.babycatbe.nevesestoque.data.offline.OfflineBanner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babycatbe.nevesestoque.feature.auth.AuthUiState
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceFormRoute
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceHistoryRoute
import com.babycatbe.nevesestoque.feature.conferences.ConferenceCategoriesRoute
import com.babycatbe.nevesestoque.feature.conferences.ConferenceDetailRoute
import com.babycatbe.nevesestoque.feature.conferences.ConferenceHistoryCategoriesRoute
import com.babycatbe.nevesestoque.feature.conferences.ConferencePrintRoute
import com.babycatbe.nevesestoque.feature.conferences.ConferencesHubScreen
import com.babycatbe.nevesestoque.feature.conferences.EditConferenceRoute
import com.babycatbe.nevesestoque.feature.conferences.ProductConferenceRoute
import com.babycatbe.nevesestoque.feature.conferences.ProductStockUpdateRoute
import com.babycatbe.nevesestoque.feature.entries.EditEntryRoute
import com.babycatbe.nevesestoque.feature.entries.EntriesHistoryRoute
import com.babycatbe.nevesestoque.feature.entries.EntriesHubScreen
import com.babycatbe.nevesestoque.feature.entries.EntryDetailRoute
import com.babycatbe.nevesestoque.feature.entries.NewEntryRoute
import com.babycatbe.nevesestoque.feature.home.HomeScreen
import com.babycatbe.nevesestoque.feature.home.homeModules
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
import com.babycatbe.nevesestoque.feature.alerts.AlertsRoute
import com.babycatbe.nevesestoque.feature.offline.PendingEditRoute
import com.babycatbe.nevesestoque.feature.offline.PendingListRoute
import com.babycatbe.nevesestoque.feature.offline.PendingStore
import com.babycatbe.nevesestoque.feature.alerts.AlertsViewModel
import com.babycatbe.nevesestoque.feature.purchases.PurchaseCategorySelectRoute
import com.babycatbe.nevesestoque.feature.purchases.PurchaseListRoute
import com.babycatbe.nevesestoque.feature.purchases.PurchaseMode
import com.babycatbe.nevesestoque.feature.purchases.PurchaseSupplierSelectRoute
import com.babycatbe.nevesestoque.feature.purchases.PurchasesHubScreen
import com.babycatbe.nevesestoque.feature.purchases.PurchasesViewModel
import com.babycatbe.nevesestoque.feature.reports.ReportsRoute
import com.babycatbe.nevesestoque.feature.suppliers.SuppliersRoute
import com.babycatbe.nevesestoque.feature.trash.TrashScreen
import com.babycatbe.nevesestoque.feature.settings.SettingsRoute

private const val HOME_ROUTE = "home"
private const val MODULE_ROUTE = "module/{route}"
private const val PRODUCTS_LIST_ROUTE = "products/list"
private const val PRODUCTS_CATEGORY_ROUTE = "products/list/category/{categoryId}"
private const val CATEGORIES_ROUTE = "products/categories"
private const val TRASH_ROUTE = "trash?filter={filter}"
private const val REPORTS_ROUTE = "stock/reports"
private const val ALERTS_ROUTE = "alerts"
private const val SETTINGS_ROUTE = "settings"
private const val PENDING_LOCAL_ROUTE = "offline/pending"
private const val PENDING_EDIT_ROUTE = "offline/pending/{localId}/edit"
private const val PENDING_SUPPLIERS_ROUTE = "suppliers/pending"
private const val PENDING_PRODUCTS_ROUTE = "products/list/pending"
private const val PURCHASE_SUPPLIERS_ROUTE = "purchases/suppliers"
private const val PURCHASE_SUPPLIER_ROUTE = "purchases/supplier/{supplierId}"
private const val PURCHASE_STOCK_ROUTE = "purchases/stock"
private const val PURCHASE_CATEGORIES_ROUTE = "purchases/categories"
private const val PURCHASE_CATEGORY_ROUTE = "purchases/category/{categoryId}"
private const val PRODUCT_DETAIL_ROUTE = "product/{productId}"
private const val PRODUCT_CREATE_ROUTE = "products/new"
private const val PRODUCT_EDIT_ROUTE = "product/{productId}/edit"
private const val PRODUCT_STOCK_ROUTE = "product/{productId}/stock"
private const val PRODUCT_CONFERENCE_ROUTE = "product/{productId}/stock/conference"
private const val PRODUCT_MERGE_ROUTE = "product/{productId}/merge"
private const val CONFERENCE_CATEGORIES_ROUTE = "conferences/categories"
private const val CONFERENCE_CATEGORY_CREATE_ROUTE = "conferences/categories/{categoryId}/new"
private const val CONFERENCE_HISTORY_ROUTE = "conferences/history"
private const val CONFERENCE_CATEGORY_HISTORY_ROUTE = "conferences/history/{categoryId}"
private const val CONFERENCE_DETAIL_ROUTE = "conferences/detail/{conferenceId}"
private const val CONFERENCE_EDIT_ROUTE = "conferences/detail/{conferenceId}/edit"
private const val CONFERENCE_PRINT_ROUTE = "conferences/print"
private const val CATEGORY_CREATE_ROUTE = "products/categories/new"
private const val CATEGORY_EDIT_ROUTE = "products/categories/{categoryId}/edit"
private const val SUPPLIER_DETAIL_ROUTE = "supplier/{supplierId}"
private const val SUPPLIER_CREATE_ROUTE = "suppliers/new"
private const val SUPPLIER_EDIT_ROUTE = "supplier/{supplierId}/edit"
private const val ENTRY_HISTORY_ROUTE = "entries/history"
private const val ENTRY_CREATE_ROUTE = "entries/new"
private const val ENTRY_CREATE_PRODUCT_ROUTE = "entries/new/product/{productId}"
private const val ENTRY_DETAIL_ROUTE = "entry/{entryId}"
private const val ENTRY_EDIT_ROUTE = "entry/{entryId}/edit"
private const val REFRESH_KEY = "catalog-refresh"
private const val NOTICE_KEY = "catalog-notice"

@Composable
fun AuthenticatedApp(authState: AuthUiState, onSignOut: () -> Unit) {
    val navController = rememberNavController()

    Column(Modifier.fillMaxSize()) {
    OfflineBanner(offlineMode = authState.offlineMode)
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = Modifier.weight(1f),
        enterTransition = { NevesMotion.navigationEnter },
        exitTransition = { NevesMotion.navigationExit },
        popEnterTransition = { NevesMotion.navigationEnter },
        popExitTransition = { NevesMotion.navigationExit },
        // Predictive back has separate defaults (including scaleOut), independent of pop transitions.
        predictivePopEnterTransition = { _ -> NevesMotion.navigationEnter },
        predictivePopExitTransition = { _ -> NevesMotion.navigationExit },
    ) {
        composable(HOME_ROUTE) {
            val alertsViewModel: AlertsViewModel = viewModel()
            val alertsState by alertsViewModel.uiState.collectAsStateWithLifecycle()
            val localPending by PendingStore.pending.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { alertsViewModel.refresh() }
            HomeScreen(
                displayName = authState.displayName,
                roleName = authState.roleName,
                onSignOut = onSignOut,
                onModuleClick = { navController.navigate("module/${it.route}") },
                alertCount = (alertsState.counts?.total ?: 0) + localPending.size,
                onAlerts = { navController.navigate(ALERTS_ROUTE) },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }

        composable(SETTINGS_ROUTE) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        composable(ALERTS_ROUTE) {
            AlertsRoute(
                onBack = { navController.popBackStack() },
                onTrash = { navController.navigate("trash") },
                onPendingSuppliers = { navController.navigate(PENDING_SUPPLIERS_ROUTE) },
                onPendingProducts = { navController.navigate(PENDING_PRODUCTS_ROUTE) },
                onLocalPending = { navController.navigate(PENDING_LOCAL_ROUTE) },
            )
        }

        composable(PENDING_LOCAL_ROUTE) { entry ->
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            PendingListRoute(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("offline/pending/$it/edit") },
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            PENDING_EDIT_ROUTE,
            arguments = listOf(navArgument("localId") { type = NavType.StringType }),
        ) { entry ->
            PendingEditRoute(
                localId = entry.arguments?.getString("localId").orEmpty(),
                onBack = { navController.popBackStack() },
                onSaved = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                    navController.popBackStack()
                },
            )
        }

        composable(PENDING_SUPPLIERS_ROUTE) {
            SuppliersRoute(
                onBack = { navController.popBackStack() },
                onSupplierClick = { navController.navigate("supplier/$it") },
                onCreateSupplier = { navController.navigate(SUPPLIER_CREATE_ROUTE) },
                initialPendingOnly = true,
                onTrash = { navController.navigate("trash?filter=supplier") },
            )
        }

        composable(PENDING_PRODUCTS_ROUTE) {
            ProductsListRoute(
                pendingOnly = true,
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate("product/$it") },
                onCreateProduct = { navController.navigate(PRODUCT_CREATE_ROUTE) },
            )
        }

        composable(MODULE_ROUTE, arguments = listOf(navArgument("route") { type = NavType.StringType })) { entry ->
            val route = entry.arguments?.getString("route")
            val module = homeModules.firstOrNull { it.route == route }
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()

            when (route) {
                "estoque" -> StockRoute(
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate("product/$it") },
                    refreshKey = refreshKey,
                    onReports = { navController.navigate(REPORTS_ROUTE) },
                )
                "produtos" -> ProductsHubScreen(
                    onBack = { navController.popBackStack() },
                    onProducts = { navController.navigate(PRODUCTS_LIST_ROUTE) },
                    onCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onTrash = { navController.navigate("trash") },
                )
                "fornecedores" -> {
                    val notice by entry.savedStateHandle
                        .getStateFlow<String?>(NOTICE_KEY, null)
                        .collectAsStateWithLifecycle()
                    SuppliersRoute(
                        onBack = { navController.popBackStack() },
                        onSupplierClick = { navController.navigate("supplier/$it") },
                        onCreateSupplier = { navController.navigate(SUPPLIER_CREATE_ROUTE) },
                        onTrash = { navController.navigate("trash?filter=supplier") },
                        refreshKey = refreshKey,
                        noticeMessage = notice,
                        onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
                    )
                }
                "entradas" -> EntriesHubScreen(
                    onBack = { navController.popBackStack() },
                    onNewEntry = { navController.navigate(ENTRY_CREATE_ROUTE) },
                    onHistory = { navController.navigate(ENTRY_HISTORY_ROUTE) },
                    onTrash = { navController.navigate("trash?filter=entry") },
                )
                "conferencias" -> ConferencesHubScreen(
                    onBack = { navController.popBackStack() },
                    onPrint = { navController.navigate(CONFERENCE_PRINT_ROUTE) },
                    onNewConference = { navController.navigate(CONFERENCE_CATEGORIES_ROUTE) },
                    onHistory = { navController.navigate(CONFERENCE_HISTORY_ROUTE) },
                    onTrash = { navController.navigate("trash?filter=conference") },
                )
                "compras" -> PurchasesHubScreen(
                    onBack = { navController.popBackStack() },
                    onSupplier = { navController.navigate(PURCHASE_SUPPLIERS_ROUTE) },
                    onStock = { navController.navigate(PURCHASE_STOCK_ROUTE) },
                    onCategory = { navController.navigate(PURCHASE_CATEGORIES_ROUTE) },
                )
                else -> LaunchedEffect(route) { navController.popBackStack() }
            }
        }

        composable(PRODUCTS_LIST_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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
                onEntry = { navController.navigate("entries/new/product/$productId") },
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
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
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

        composable(ENTRY_HISTORY_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            EntriesHistoryRoute(
                onBack = { navController.popBackStack() },
                onNewEntry = { navController.navigate(ENTRY_CREATE_ROUTE) },
                onEntryClick = { navController.navigate("entry/$it") },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(ENTRY_CREATE_ROUTE) {
            NewEntryRoute(
                onBack = { navController.popBackStack() },
                onSaved = { entryId, message ->
                    navController.popBackStack()
                    navController.navigate("entry/$entryId")
                    navController.currentBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                },
                onSavedPending = { message ->
                    navController.popBackStack()
                    navController.navigate(PENDING_LOCAL_ROUTE)
                    navController.currentBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                },
            )
        }

        composable(
            ENTRY_CREATE_PRODUCT_ROUTE,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            NewEntryRoute(
                initialProductId = productId,
                onBack = { navController.popBackStack() },
                onSaved = { entryId, message ->
                    navController.popBackStack()
                    navController.navigate("entry/$entryId")
                    navController.currentBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                },
                onSavedPending = { message ->
                    navController.popBackStack()
                    navController.navigate(PENDING_LOCAL_ROUTE)
                    navController.currentBackStackEntry?.savedStateHandle?.set(NOTICE_KEY, message)
                },
            )
        }

        composable(
            ENTRY_DETAIL_ROUTE,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { entry ->
            val entryId = entry.arguments?.getString("entryId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            EntryDetailRoute(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("entry/$entryId/edit") },
                onDeleted = { message ->
                    navController.popBackStack(MODULE_ROUTE, inclusive = false)
                    navController.navigate(ENTRY_HISTORY_ROUTE)
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            ENTRY_EDIT_ROUTE,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { entry ->
            val entryId = entry.arguments?.getString("entryId").orEmpty()
            EditEntryRoute(
                entryId = entryId,
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

        composable(CONFERENCE_CATEGORIES_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            ConferenceCategoriesRoute(
                onBack = { navController.popBackStack() },
                onCategoryClick = { navController.navigate("conferences/categories/$it/new") },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            CONFERENCE_CATEGORY_CREATE_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            val categoryId = entry.arguments?.getString("categoryId").orEmpty()
            CategoryConferenceFormRoute(
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

        composable(CONFERENCE_HISTORY_ROUTE) { entry ->
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            ConferenceHistoryCategoriesRoute(
                onBack = { navController.popBackStack() },
                onCategoryClick = { navController.navigate("conferences/history/$it") },
                refreshKey = refreshKey,
            )
        }

        composable(
            CONFERENCE_CATEGORY_HISTORY_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            val categoryId = entry.arguments?.getString("categoryId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            CategoryConferenceHistoryRoute(
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onConferenceClick = { navController.navigate("conferences/detail/$it") },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            CONFERENCE_DETAIL_ROUTE,
            arguments = listOf(navArgument("conferenceId") { type = NavType.StringType }),
        ) { entry ->
            val conferenceId = entry.arguments?.getString("conferenceId").orEmpty()
            val refreshKey by entry.savedStateHandle.getStateFlow(REFRESH_KEY, 0L).collectAsStateWithLifecycle()
            val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE_KEY, null).collectAsStateWithLifecycle()
            ConferenceDetailRoute(
                conferenceId = conferenceId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("conferences/detail/$conferenceId/edit") },
                onDeleted = { categoryId, message ->
                    navController.popBackStack(CONFERENCE_CATEGORY_HISTORY_ROUTE, inclusive = true)
                    navController.navigate("conferences/history/$categoryId")
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set(REFRESH_KEY, System.currentTimeMillis())
                        set(NOTICE_KEY, message)
                    }
                },
                refreshKey = refreshKey,
                noticeMessage = notice,
                onDismissNotice = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        composable(
            CONFERENCE_EDIT_ROUTE,
            arguments = listOf(navArgument("conferenceId") { type = NavType.StringType }),
        ) { entry ->
            val conferenceId = entry.arguments?.getString("conferenceId").orEmpty()
            EditConferenceRoute(
                conferenceId = conferenceId,
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

        composable(CONFERENCE_PRINT_ROUTE) {
            ConferencePrintRoute(onBack = { navController.popBackStack() })
        }

        composable(PURCHASE_SUPPLIERS_ROUTE) {
            PurchaseSupplierSelectRoute(
                onBack = { navController.popBackStack() },
                onSelect = { navController.navigate("purchases/supplier/$it") },
            )
        }

        composable(
            PURCHASE_SUPPLIER_ROUTE,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType }),
        ) { entry ->
            // Reaproveita os dados de Compras já carregados na tela de escolha (evita
            // repetir a leitura completa do histórico ao abrir a lista).
            val selectionEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(PURCHASE_SUPPLIERS_ROUTE) }.getOrNull()
            }
            PurchaseListRoute(
                mode = PurchaseMode.Supplier,
                targetId = entry.arguments?.getString("supplierId"),
                onBack = { navController.popBackStack() },
                vm = if (selectionEntry != null) viewModel<PurchasesViewModel>(selectionEntry) else viewModel<PurchasesViewModel>(),
            )
        }

        composable(PURCHASE_STOCK_ROUTE) {
            PurchaseListRoute(mode = PurchaseMode.Stock, targetId = null, onBack = { navController.popBackStack() })
        }

        composable(PURCHASE_CATEGORIES_ROUTE) {
            PurchaseCategorySelectRoute(
                onBack = { navController.popBackStack() },
                onSelect = { navController.navigate("purchases/category/$it") },
            )
        }

        composable(
            PURCHASE_CATEGORY_ROUTE,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            // Reaproveita os dados de Compras já carregados na tela de escolha (evita
            // repetir a leitura completa do histórico ao abrir a lista).
            val selectionEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(PURCHASE_CATEGORIES_ROUTE) }.getOrNull()
            }
            PurchaseListRoute(
                mode = PurchaseMode.Category,
                targetId = entry.arguments?.getString("categoryId"),
                onBack = { navController.popBackStack() },
                vm = if (selectionEntry != null) viewModel<PurchasesViewModel>(selectionEntry) else viewModel<PurchasesViewModel>(),
            )
        }

        composable(REPORTS_ROUTE) {
            ReportsRoute(onBack = { navController.popBackStack() })
        }

        composable(
            TRASH_ROUTE,
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { entry ->
            TrashScreen(
                onBack = { navController.popBackStack() },
                initialFilter = entry.arguments?.getString("filter"),
                onDataChanged = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        REFRESH_KEY,
                        System.currentTimeMillis(),
                    )
                },
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
}

