package com.babycatbe.nevesestoque

import android.app.Application
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor
import com.babycatbe.nevesestoque.data.offline.OfflineStore

class NevesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeviceIdentityStore.initialize(this)
        OfflineStore.initialize(this)
        com.babycatbe.nevesestoque.feature.offline.PendingStore.reload()
        ConnectivityMonitor.initialize(this)
    }
}
