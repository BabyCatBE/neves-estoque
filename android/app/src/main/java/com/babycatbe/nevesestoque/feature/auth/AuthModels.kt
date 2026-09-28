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
