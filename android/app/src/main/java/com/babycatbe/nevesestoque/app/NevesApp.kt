package com.babycatbe.nevesestoque.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.babycatbe.nevesestoque.feature.home.HomeScreen
import com.babycatbe.nevesestoque.ui.theme.NevesTheme
import kotlinx.coroutines.launch

@Composable
fun NevesApp() {
    NevesTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        HomeScreen(
            snackbarHostState = snackbarHostState,
            onModuleClick = { module ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "${module.title}: integração funcional entra na próxima etapa Android.",
                    )
                }
            },
        )
    }
}
