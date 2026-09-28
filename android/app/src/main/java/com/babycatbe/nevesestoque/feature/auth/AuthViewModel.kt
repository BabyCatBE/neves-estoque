package com.babycatbe.nevesestoque.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository(SupabaseProvider.client)
    val uiState: StateFlow<AuthUiState> = repository.uiState

    init { repository.start(viewModelScope) }

    fun signInWithUsername(username: String, password: String) {
        viewModelScope.launch { repository.signInWithUsername(username, password) }
    }

    fun signInWithGoogle() {
        viewModelScope.launch { repository.signInWithGoogle() }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
