package com.babycatbe.nevesestoque.data.device

import android.content.Context
import android.os.Build
import java.util.UUID

object DeviceIdentityStore {
    private const val PREFS = "neves_estoque_device"
    private const val DEVICE_KEY = "device_key_v1"
    private const val REGISTERED_DEVICE_ID = "registered_device_id_v1"
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun preferences() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreateDeviceKey(): String {
        val prefs = preferences()
        val existing = prefs.getString(DEVICE_KEY, null)
        if (existing.isUuid()) return existing!!

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(DEVICE_KEY, created).apply()
        return created
    }

    fun registeredDeviceId(): String? =
        preferences().getString(REGISTERED_DEVICE_ID, null).takeIf { it.isUuid() }

    fun setRegisteredDeviceId(value: String) {
        require(value.isUuid()) { "Identificador registrado do dispositivo inválido." }
        preferences().edit().putString(REGISTERED_DEVICE_ID, value).apply()
    }

    fun clearRegisteredDeviceId() {
        preferences().edit().remove(REGISTERED_DEVICE_ID).apply()
    }

    fun friendlyName(): String {
        val maker = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()
        val device = listOf(maker, model).filter { it.isNotBlank() }.distinct()
            .joinToString(" ").ifBlank { "Android" }
        return "Android · $device".take(120)
    }

    private fun String?.isUuid(): Boolean =
        this != null && runCatching { UUID.fromString(this) }.isSuccess
}
