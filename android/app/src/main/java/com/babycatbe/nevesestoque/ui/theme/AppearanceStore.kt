package com.babycatbe.nevesestoque.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Aparência escolhida em Configurações. Nesta versão não existe "Seguir sistema". */
enum class AppearanceMode(val storageValue: String) {
    Light("light"),
    Dark("dark");

    companion object {
        /** Valor ausente ou desconhecido cai no tema claro, o padrão aprovado. */
        fun fromStorage(value: String?): AppearanceMode = entries.firstOrNull { it.storageValue == value } ?: Light
    }
}

/** Armazenamento local mínimo; abstraído para permitir teste unitário sem Android. */
interface AppearanceStorage {
    fun read(): String?
    fun write(value: String)
}

/** Estado da aparência: carregado de forma síncrona na criação, gravado a cada escolha. */
class AppearancePreference(private val storage: AppearanceStorage) {
    private val state = MutableStateFlow(AppearanceMode.fromStorage(storage.read()))
    val mode: StateFlow<AppearanceMode> = state.asStateFlow()

    fun select(mode: AppearanceMode) {
        if (state.value == mode) return
        state.value = mode
        storage.write(mode.storageValue)
    }
}

private class SharedPreferencesAppearanceStorage(context: Context) : AppearanceStorage {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val PREFS = "neves_estoque_appearance"
        const val KEY = "theme_mode_v1"
    }
}

/**
 * Preferência local por aparelho (SharedPreferences privado do app). Não sincroniza com Supabase,
 * não depende de conta. Inicializada em NevesApplication.onCreate, antes da primeira tela, para que
 * o app já abra no tema salvo.
 */
object AppearanceStore {
    @Volatile
    private var preference: AppearancePreference? = null
    private val fallback = MutableStateFlow(AppearanceMode.Light)

    fun initialize(context: Context) {
        if (preference != null) return
        synchronized(this) {
            if (preference == null) {
                preference = AppearancePreference(SharedPreferencesAppearanceStorage(context.applicationContext))
            }
        }
    }

    val mode: StateFlow<AppearanceMode>
        get() = preference?.mode ?: fallback

    fun select(mode: AppearanceMode) {
        val current = preference
        if (current != null) current.select(mode) else fallback.value = mode
    }
}
