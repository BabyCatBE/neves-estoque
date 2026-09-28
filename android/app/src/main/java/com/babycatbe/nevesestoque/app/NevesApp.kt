package com.babycatbe.nevesestoque.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babycatbe.nevesestoque.feature.auth.AuthStatus
import com.babycatbe.nevesestoque.feature.auth.AuthViewModel
import com.babycatbe.nevesestoque.feature.auth.LoginScreen
import com.babycatbe.nevesestoque.ui.theme.NevesTheme

@Composable
fun NevesApp(authViewModel: AuthViewModel = viewModel()) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    NevesTheme {
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
