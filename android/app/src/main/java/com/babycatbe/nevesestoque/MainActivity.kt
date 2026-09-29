package com.babycatbe.nevesestoque

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.babycatbe.nevesestoque.app.NevesApp
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.ui.theme.AppearanceMode
import com.babycatbe.nevesestoque.ui.theme.AppearanceStore
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        SupabaseProvider.client?.handleDeeplinks(intent)
        // Fundo da janela acompanha o tema salvo: evita flash claro antes do primeiro frame no Escuro.
        lightWindowBackground = window.decorView.background
        applyWindowBackground(AppearanceStore.mode.value)
        setContent {
            val appearance by AppearanceStore.mode.collectAsStateWithLifecycle()
            SideEffect { applyWindowBackground(appearance) }
            NevesApp()
        }
    }

    private var lightWindowBackground: Drawable? = null

    private fun applyWindowBackground(mode: AppearanceMode) {
        // Claro mantém exatamente o fundo original da janela; Escuro usa o fundo grafite aprovado.
        window.setBackgroundDrawable(if (mode == AppearanceMode.Dark) ColorDrawable(DARK_WINDOW_BACKGROUND) else lightWindowBackground)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseProvider.client?.handleDeeplinks(intent)
    }
}

private const val DARK_WINDOW_BACKGROUND = 0xFF181614.toInt()
