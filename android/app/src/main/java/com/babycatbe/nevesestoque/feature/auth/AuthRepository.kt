package com.babycatbe.nevesestoque.feature.auth

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor
import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.data.offline.isNetworkFailure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(private val client: SupabaseClient?) {
    private val _uiState = MutableStateFlow(
        if (client == null) AuthUiState(
            status = AuthStatus.ConfigMissing,
            errorMessage = "Configuração do Supabase ainda não foi fornecida para esta build.",
        ) else AuthUiState(status = AuthStatus.Loading)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var failureStatus: AuthStatus? = null
    private var bootstrappedAuthUserId: String? = null

    fun start(scope: CoroutineScope) {
        val activeClient = client ?: return
        // Ao reconectar, refaz a validação online sem voltar para a tela de carregamento.
        scope.launch {
            ConnectivityMonitor.online.collectLatest { online ->
                if (online && _uiState.value.status == AuthStatus.Ready && _uiState.value.offlineMode) {
                    activeClient.auth.currentSessionOrNull()?.let { revalidateSilently(it) }
                }
            }
        }
        scope.launch {
            activeClient.auth.sessionStatus.collectLatest { sessionStatus ->
                when (sessionStatus) {
                    SessionStatus.Initializing -> if (_uiState.value.status != AuthStatus.Ready) {
                        _uiState.value = _uiState.value.copy(status = AuthStatus.Loading)
                    }
                    is SessionStatus.Authenticated -> {
                        val authUserId = sessionStatus.session.user?.id
                        if (authUserId != null &&
                            bootstrappedAuthUserId == authUserId &&
                            _uiState.value.status == AuthStatus.Ready
                        ) return@collectLatest
                        bootstrapAccess(sessionStatus.session)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        bootstrappedAuthUserId = null
                        _uiState.value = AuthUiState(
                            status = failureStatus ?: AuthStatus.SignedOut,
                            errorMessage = _uiState.value.errorMessage,
                        )
                    }
                    is SessionStatus.RefreshFailure -> {
                        // Falha ao renovar a sessão costuma ser falta de internet: não desloga.
                        if (_uiState.value.status == AuthStatus.Ready) return@collectLatest
                        val authUserId = activeClient.auth.currentSessionOrNull()?.user?.id
                        if (authUserId != null && enterOfflineIfKnown(authUserId)) return@collectLatest
                        _uiState.value = _uiState.value.copy(
                            status = AuthStatus.Error,
                            errorMessage = "Não foi possível atualizar a sessão. Verifique a internet e tente novamente.",
                        )
                    }
                }
            }
        }
    }

    suspend fun signInWithUsername(rawUsername: String, password: String) {
        val activeClient = client ?: return setConfigMissing()
        val normalized = normalizeSecondaryUsername(rawUsername)
        if (!isValidSecondaryUsername(normalized) || password.isEmpty()) {
            _uiState.value = AuthUiState(
                status = AuthStatus.SignedOut,
                errorMessage = "Informe um usuário e uma senha válidos.",
            )
            return
        }

        failureStatus = null
        _uiState.value = AuthUiState(status = AuthStatus.Loading)
        runCatching {
            activeClient.auth.signInWith(Email) {
                email = secondaryAuthEmail(normalized)
                this.password = password
            }
        }.onFailure {
            _uiState.value = AuthUiState(
                status = AuthStatus.SignedOut,
                errorMessage = "Usuário ou senha incorretos.",
            )
        }
    }

    suspend fun signInWithGoogle() {
        val activeClient = client ?: return setConfigMissing()
        failureStatus = null
        _uiState.value = AuthUiState(status = AuthStatus.Loading)
        runCatching { activeClient.auth.signInWith(Google) }.onFailure {
            _uiState.value = AuthUiState(
                status = AuthStatus.SignedOut,
                errorMessage = "Não foi possível iniciar o login com Google.",
            )
        }
    }

    suspend fun signOut() {
        failureStatus = null
        bootstrappedAuthUserId = null
        DeviceIdentityStore.clearRegisteredDeviceId()
        OfflineAccessStore.clear()
        OfflineStore.clearCache()
        val activeClient = client ?: return setConfigMissing()
        runCatching { activeClient.auth.signOut() }
        _uiState.value = AuthUiState(status = AuthStatus.SignedOut)
    }

    private suspend fun bootstrapAccess(session: UserSession) {
        val activeClient = client ?: return setConfigMissing()
        val authUserId = session.user?.id ?: return blockAccess(
            AuthStatus.Unauthorized,
            "Não foi possível identificar o usuário autenticado.",
        )
        _uiState.value = AuthUiState(status = AuthStatus.Loading)

        try {
            validateOnline(activeClient, authUserId)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is AccessRefused) return blockAccess(error.status, error.message.orEmpty())
            if (isNetworkFailure(error)) {
                if (enterOfflineIfKnown(authUserId)) return
                // Nunca desloga nem apaga o dispositivo por falta de internet.
                _uiState.value = AuthUiState(
                    status = AuthStatus.Error,
                    errorMessage = "Sem internet. Este aparelho precisa de uma conexão para o primeiro acesso. " +
                        "Conecte-se e abra o app novamente.",
                )
                return
            }
            blockAccess(AuthStatus.Unauthorized, "Não foi possível validar este acesso no servidor.")
        }
    }

    /** Revalida em segundo plano após reconectar, mantendo a navegação atual. */
    private suspend fun revalidateSilently(session: UserSession) {
        val activeClient = client ?: return
        val authUserId = session.user?.id ?: return
        try {
            validateOnline(activeClient, authUserId)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is AccessRefused) return blockAccess(error.status, error.message.orEmpty())
            // Falha de rede ou temporária: continua no modo offline e tenta na próxima reconexão.
        }
    }

    private fun enterOfflineIfKnown(authUserId: String): Boolean {
        val record = OfflineAccessStore.load() ?: return false
        if (record.authUserId != authUserId) return false
        if (record.deviceKey != DeviceIdentityStore.getOrCreateDeviceKey()) return false
        failureStatus = null
        bootstrappedAuthUserId = authUserId
        _uiState.value = AuthUiState(
            status = AuthStatus.Ready,
            appUserId = record.appUserId,
            roleName = record.roleName,
            deviceId = record.deviceId,
            displayName = record.displayName,
            username = record.username,
            authMethod = record.authMethod,
            offlineMode = true,
        )
        return true
    }

    private class AccessRefused(val status: AuthStatus, message: String) : Exception(message)

    private suspend fun validateOnline(activeClient: SupabaseClient, authUserId: String) {
        run {
            val access = activeClient.postgrest.rpc("claim_app_access")
                .decodeList<AppAccessRow>().firstOrNull() ?: error("Acesso não retornado.")

            val profile = activeClient.from("app_users")
                .select(Columns.list("display_name", "username", "auth_method")) {
                    filter { eq("auth_user_id", authUserId) }
                }
                .decodeList<AppUserProfile>().firstOrNull() ?: error("Perfil não encontrado.")

            val device = activeClient.postgrest.rpc(
                function = "register_device",
                parameters = buildJsonObject {
                    put("p_device_key", DeviceIdentityStore.getOrCreateDeviceKey())
                    put("p_friendly_name", DeviceIdentityStore.friendlyName())
                },
            ).decodeList<RegisteredDeviceRow>().firstOrNull() ?: error("Dispositivo não retornado.")

            if (!device.isAllowed) throw AccessRefused(
                AuthStatus.DeviceBlocked,
                "Este dispositivo está bloqueado para o Neves Estoque.",
            )

            DeviceIdentityStore.setRegisteredDeviceId(device.deviceId)
            OfflineAccessStore.save(
                OfflineAccessRecord(
                    authUserId = authUserId,
                    deviceKey = DeviceIdentityStore.getOrCreateDeviceKey(),
                    appUserId = access.appUserId,
                    roleName = access.roleName,
                    deviceId = device.deviceId,
                    displayName = profile.displayName,
                    username = profile.username,
                    authMethod = profile.authMethod,
                    validatedAt = System.currentTimeMillis(),
                )
            )
            failureStatus = null
            bootstrappedAuthUserId = authUserId
            _uiState.value = AuthUiState(
                status = AuthStatus.Ready,
                appUserId = access.appUserId,
                roleName = access.roleName,
                deviceId = device.deviceId,
                displayName = profile.displayName,
                username = profile.username,
                authMethod = profile.authMethod,
            )
        }
    }

    private suspend fun blockAccess(status: AuthStatus, message: String) {
        failureStatus = status
        bootstrappedAuthUserId = null
        DeviceIdentityStore.clearRegisteredDeviceId()
        OfflineAccessStore.clear()
        OfflineStore.clearCache()
        _uiState.value = AuthUiState(status = status, errorMessage = message)
        runCatching { client?.auth?.signOut() }
    }

    private fun setConfigMissing() {
        _uiState.value = AuthUiState(
            status = AuthStatus.ConfigMissing,
            errorMessage = "Configuração do Supabase ainda não foi fornecida para esta build.",
        )
    }
}
