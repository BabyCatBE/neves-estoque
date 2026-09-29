package com.babycatbe.nevesestoque.app

import com.babycatbe.nevesestoque.ui.theme.NevesColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babycatbe.nevesestoque.feature.auth.AuthStatus
import com.babycatbe.nevesestoque.feature.auth.AuthViewModel
import com.babycatbe.nevesestoque.feature.auth.LoginScreen
import com.babycatbe.nevesestoque.ui.theme.NevesTheme

private val StatusBarBackground = NevesColors.Header

@Composable
fun NevesApp(authViewModel: AuthViewModel = viewModel()) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    NevesTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(StatusBarBackground),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    when (authState.status) {
                        AuthStatus.Loading -> LoadingAccessScreen()
                        AuthStatus.Ready -> AuthenticatedApp(authState, authViewModel::signOut)
                        else -> LoginScreen(
                            state = authState,
                            onUsernameLogin = authViewModel::signInWithUsername,
                            onGoogleLogin = authViewModel::signInWithGoogle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingAccessScreen() {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            CircularProgressIndicator()
            Text("Verificando acesso…")
        }
    }
}

