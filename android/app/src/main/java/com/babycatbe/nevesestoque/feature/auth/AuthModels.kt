package com.babycatbe.nevesestoque.feature.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AuthStatus { Loading, SignedOut, Ready, Unauthorized, DeviceBlocked, ConfigMissing, Error }

data class AuthUiState(
    val status: AuthStatus = AuthStatus.Loading,
    val appUserId: String? = null,
    val roleName: String? = null,
    val deviceId: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val authMethod: String? = null,
    val errorMessage: String? = null,
    /** true quando o acesso foi reconhecido localmente por falta de internet (sem validação online agora). */
    val offlineMode: Boolean = false,
    /**
     * true enquanto o app já está aberto pelo último acesso validado neste aparelho e a validação
     * online (acesso + perfil + dispositivo) ainda está em andamento em segundo plano.
     */
    val verifyingInBackground: Boolean = false,
)

/**
 * Decide se o app pode abrir imediatamente pelo último acesso validado online neste aparelho,
 * sem a tela bloqueante de verificação. Exige o MESMO aparelho e, quando a sessão já é conhecida,
 * o MESMO usuário. Não concede acesso novo: a validação online continua acontecendo em segundo plano
 * e, se o servidor recusar, o acesso local é apagado e o app volta para o login.
 */
fun canOpenFromLocalAccess(
    record: OfflineAccessRecord?,
    currentDeviceKey: String,
    sessionAuthUserId: String? = null,
): Boolean {
    if (record == null) return false
    if (record.authUserId.isBlank() || record.appUserId.isBlank() || record.deviceId.isBlank()) return false
    if (record.deviceKey != currentDeviceKey) return false
    if (sessionAuthUserId != null && sessionAuthUserId != record.authUserId) return false
    return true
}

fun OfflineAccessRecord.toReadyState(offlineMode: Boolean, verifyingInBackground: Boolean): AuthUiState =
    AuthUiState(
        status = AuthStatus.Ready,
        appUserId = appUserId,
        roleName = roleName,
        deviceId = deviceId,
        displayName = displayName,
        username = username,
        authMethod = authMethod,
        offlineMode = offlineMode,
        verifyingInBackground = verifyingInBackground,
    )

/**
 * Mínimo necessário para reconhecer, sem internet, o MESMO usuário no MESMO aparelho já validados online.
 * Não concede acesso novo: ao reconectar, a validação online é refeita e o banco decide.
 */
@Serializable
data class OfflineAccessRecord(
    val authUserId: String,
    val deviceKey: String,
    val appUserId: String,
    val roleName: String,
    val deviceId: String,
    val displayName: String,
    val username: String? = null,
    val authMethod: String,
    val validatedAt: Long,
)

@Serializable
data class AppAccessRow(
    @SerialName("app_user_id") val appUserId: String,
    @SerialName("role_name") val roleName: String,
)

@Serializable
data class RegisteredDeviceRow(
    @SerialName("device_id") val deviceId: String,
    @SerialName("is_allowed") val isAllowed: Boolean,
)

@Serializable
data class AppUserProfile(
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("auth_method") val authMethod: String,
)
