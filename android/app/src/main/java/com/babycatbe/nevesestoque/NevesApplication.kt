package com.babycatbe.nevesestoque

import android.app.Application
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore

class NevesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeviceIdentityStore.initialize(this)
    }
}
