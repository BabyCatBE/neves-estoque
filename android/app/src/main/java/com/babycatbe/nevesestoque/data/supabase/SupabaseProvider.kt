package com.babycatbe.nevesestoque.data.supabase

import com.babycatbe.nevesestoque.BuildConfig
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

object SupabaseProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) null else createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "nevesestoque"
                host = "auth"
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest) { requireValidSession = true }
            httpConfig {
                defaultRequest {
                    DeviceIdentityStore.registeredDeviceId()?.let { header("x-device-id", it) }
                }
            }
        }
    }
}
