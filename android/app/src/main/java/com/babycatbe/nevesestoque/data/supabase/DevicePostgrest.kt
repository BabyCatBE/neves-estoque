package com.babycatbe.nevesestoque.data.supabase

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder

@OptIn(SupabaseExperimental::class)
fun PostgrestRequestBuilder.attachRegisteredDevice() {
    DeviceIdentityStore.registeredDeviceId()?.let { deviceId ->
        headers.set("x-device-id", deviceId)
    }
}
