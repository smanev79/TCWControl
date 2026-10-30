package com.tcwcontrol.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tcwcontrol.app.TcwApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Restarts alarm monitoring after the phone reboots, if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = (context.applicationContext as TcwApp).repository
        if (runBlocking { repository.monitoringEnabled.first() }) MonitorService.start(context)
    }
}
