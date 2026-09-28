package com.babycatbe.nevesestoque.feature.auth

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
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
                        _uiState.value = _uiState.value.copy(
                            status = AuthStatus.Error,
                            errorMessage = "Não foi possível atualizar a sessão. Tente entrar novamente.",
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

            if (!device.isAllowed) return blockAccess(
                AuthStatus.DeviceBlocked,
                "Este dispositivo está bloqueado para o Neves Estoque.",
            )

            DeviceIdentityStore.setRegisteredDeviceId(device.deviceId)
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
        } catch (_: Throwable) {
            blockAccess(AuthStatus.Unauthorized, "Não foi possível validar este acesso no servidor.")
        }
    }

    private suspend fun blockAccess(status: AuthStatus, message: String) {
        failureStatus = status
        bootstrappedAuthUserId = null
        DeviceIdentityStore.clearRegisteredDeviceId()
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
