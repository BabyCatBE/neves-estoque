package com.babycatbe.nevesestoque.feature.auth

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor
import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.data.offline.isNetworkFailure
import com.babycatbe.nevesestoque.ui.load.SharedSnapshotLoads
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

    /**
     * Usuário aberto pelo último acesso validado neste aparelho, ainda sem a confirmação online desta
     * abertura. Enquanto estiver preenchido, a sessão desse usuário é validada em segundo plano, sem a
     * tela bloqueante "Verificando acesso".
     */
    private var provisionalAuthUserId: String? = null

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
            // Abertura imediata: se este aparelho já tem um acesso validado online, a interface abre
            // direto e a validação acontece em segundo plano quando a sessão for carregada.
            openFromLocalAccessIfKnown()
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
                        if (authUserId != null &&
                            provisionalAuthUserId == authUserId &&
                            _uiState.value.status == AuthStatus.Ready
                        ) {
                            verifyInBackground(sessionStatus.session)
                            return@collectLatest
                        }
                        provisionalAuthUserId = null
                        bootstrapAccess(sessionStatus.session)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        bootstrappedAuthUserId = null
                        provisionalAuthUserId = null
                        _uiState.value = AuthUiState(
                            status = failureStatus ?: AuthStatus.SignedOut,
                            errorMessage = _uiState.value.errorMessage,
                        )
                    }
                    is SessionStatus.RefreshFailure -> {
                        // Falha ao renovar a sessão costuma ser falta de internet: não desloga.
                        if (_uiState.value.status == AuthStatus.Ready) {
                            if (_uiState.value.verifyingInBackground) {
                                // Aberto pelo acesso local e sem conseguir renovar a sessão agora:
                                // passa ao modo sem internet. A validação é refeita quando a sessão
                                // voltar a ser autenticada ou quando a conexão retornar.
                                _uiState.value = _uiState.value.copy(
                                    offlineMode = true,
                                    verifyingInBackground = false,
                                )
                            }
                            return@collectLatest
                        }
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
        provisionalAuthUserId = null
        DeviceIdentityStore.clearRegisteredDeviceId()
        OfflineAccessStore.clear()
        OfflineStore.clearCache()
        SharedSnapshotLoads.clearAll()
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

    /**
     * Abre a interface pelo último acesso validado online neste aparelho, sem esperar a rede.
     * Não roda depois de login, falha ou bloqueio (só a partir do estado inicial de carregamento).
     */
    private suspend fun openFromLocalAccessIfKnown() {
        if (_uiState.value.status != AuthStatus.Loading) return
        val record = OfflineAccessStore.load()
        if (!canOpenFromLocalAccess(record, DeviceIdentityStore.getOrCreateDeviceKey())) return
        if (_uiState.value.status != AuthStatus.Loading) return
        val known = record ?: return
        provisionalAuthUserId = known.authUserId
        _uiState.value = known.toReadyState(offlineMode = false, verifyingInBackground = true)
    }

    /**
     * Confirma online o acesso de quem já está usando o app aberto pelo acesso local.
     * Mesmas regras do bootstrap: recusa do servidor bloqueia e apaga o acesso local; falta de
     * internet mantém o modo sem internet sem deslogar.
     */
    private suspend fun verifyInBackground(session: UserSession) {
        val activeClient = client ?: return setConfigMissing()
        val authUserId = session.user?.id ?: return blockAccess(
            AuthStatus.Unauthorized,
            "Não foi possível identificar o usuário autenticado.",
        )
        if (!_uiState.value.verifyingInBackground) {
            _uiState.value = _uiState.value.copy(verifyingInBackground = true)
        }
        try {
            validateOnline(activeClient, authUserId)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is AccessRefused) return blockAccess(error.status, error.message.orEmpty())
            if (isNetworkFailure(error)) {
                // Mesmo tratamento do bootstrap sem internet: segue pela cópia deste aparelho.
                provisionalAuthUserId = null
                if (!enterOfflineIfKnown(authUserId)) {
                    _uiState.value = AuthUiState(
                        status = AuthStatus.Error,
                        errorMessage = "Sem internet. Este aparelho precisa de uma conexão para o primeiro acesso. " +
                            "Conecte-se e abra o app novamente.",
                    )
                }
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

    private suspend fun enterOfflineIfKnown(authUserId: String): Boolean {
        val record = OfflineAccessStore.load() ?: return false
        if (record.authUserId != authUserId) return false
        if (record.deviceKey != DeviceIdentityStore.getOrCreateDeviceKey()) return false
        failureStatus = null
        bootstrappedAuthUserId = authUserId
        provisionalAuthUserId = null
        _uiState.value = record.toReadyState(offlineMode = true, verifyingInBackground = false)
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
            provisionalAuthUserId = null
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
        provisionalAuthUserId = null
        DeviceIdentityStore.clearRegisteredDeviceId()
        OfflineAccessStore.clear()
        OfflineStore.clearCache()
        SharedSnapshotLoads.clearAll()
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
