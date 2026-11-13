package com.tcwcontrol.app

import android.app.Application
import com.tcwcontrol.app.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TcwApp : Application() {
    val repository: DeviceRepository by lazy { DeviceRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // After an update from the previous TCW Control app, take over its saved devices.
        appScope.launch { runCatching { repository.importLegacyOnce() } }
    }
}
