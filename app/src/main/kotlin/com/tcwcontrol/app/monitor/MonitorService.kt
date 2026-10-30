package com.tcwcontrol.app.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tcwcontrol.app.TcwApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that polls every device while the app is closed, so that alarms are logged
 * and notified (see [com.tcwcontrol.app.data.DeviceRepository.fetchStatus]) even then.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var notifier: AlarmNotifier
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = AlarmNotifier(this).apply { createChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            AlarmNotifier.MONITOR_NOTIFICATION_ID,
            notifier.monitorNotification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop == null) {
            // Keeps the CPU awake between checks; without it the timer stops while the phone sleeps.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TCWControl:monitor")
                .apply { acquire() }
            loop = scope.launch { pollForever() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private suspend fun pollForever() {
        val repository = (application as TcwApp).repository
        while (true) {
            if (!repository.monitoringEnabled.first()) {
                stopSelf()
                return
            }
            val devices = repository.devices.first()
            updateOngoing(devices.size)
            // fetchStatus logs the alarms and notifies about new ones.
            devices.map { device ->
                scope.async { runCatching { repository.fetchStatus(device) } }
            }.awaitAll()
            delay(repository.alarmCheckSeconds.first() * 1000L)
        }
    }

    private fun updateOngoing(deviceCount: Int) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(AlarmNotifier.MONITOR_NOTIFICATION_ID, notifier.monitorNotification(deviceCount))
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
