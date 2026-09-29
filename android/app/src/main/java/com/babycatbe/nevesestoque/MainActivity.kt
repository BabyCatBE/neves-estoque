package com.babycatbe.nevesestoque

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.babycatbe.nevesestoque.app.NevesApp
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        SupabaseProvider.client?.handleDeeplinks(intent)
        setContent { NevesApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseProvider.client?.handleDeeplinks(intent)
    }
}
