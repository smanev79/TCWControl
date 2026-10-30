package com.tcwcontrol.app.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tcwcontrol.app.MainActivity
import com.tcwcontrol.app.R
import com.tcwcontrol.app.ui.theme.NotificationRed
import com.tcwcontrol.core.model.ActiveAlarm

class AlarmNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH),
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun monitorNotification(deviceCount: Int) =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.monitor_running, deviceCount))
            .setContentIntent(openApp(null))
            .setOngoing(true)
            .build()

    /** Shows (or replaces) the alarm notification of a device listing the alarms that just started. */
    fun showAlarms(name: String, deviceId: String, alarms: List<ActiveAlarm>) {
        if (!canNotify()) return
        val lines = alarms.take(MAX_LINES).map { it.text }
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        if (alarms.size > MAX_LINES) style.setSummaryText(context.getString(R.string.alarm_more, alarms.size - MAX_LINES))
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(NotificationRed.toArgb())
            .setContentTitle(context.getString(R.string.alarm_title, name))
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp(deviceId))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked in canNotify()
        manager.notify(deviceId.hashCode(), notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(deviceId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (deviceId != null) putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId) }
        return PendingIntent.getActivity(
            context,
            deviceId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ALARMS = "alarms"
        const val CHANNEL_MONITOR = "monitor"
        const val MONITOR_NOTIFICATION_ID = 1
        private const val MAX_LINES = 6
    }
}
