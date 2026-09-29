package com.babycatbe.nevesestoque

import android.app.Application
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor
import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.ui.theme.AppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NevesApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Antes de qualquer tela: o app já abre no tema salvo, sem piscar Claro -> Escuro.
        AppearanceStore.initialize(this)
        DeviceIdentityStore.initialize(this)
        OfflineStore.initialize(this)
        applicationScope.launch {
            OfflineStore.prepare()
            com.babycatbe.nevesestoque.feature.offline.PendingStore.reload()
        }
        ConnectivityMonitor.initialize(this)
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
