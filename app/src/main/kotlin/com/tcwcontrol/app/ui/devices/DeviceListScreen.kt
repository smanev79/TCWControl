package com.tcwcontrol.app.ui.devices

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcwcontrol.app.R
import com.tcwcontrol.app.data.DeviceRepository
import com.tcwcontrol.app.monitor.MonitorService
import com.tcwcontrol.app.ui.WhileVisible
import com.tcwcontrol.app.ui.appViewModel
import com.tcwcontrol.app.ui.theme.AlarmRed
import com.tcwcontrol.app.ui.theme.IndicatorOff
import com.tcwcontrol.app.ui.theme.OfflineGrey
import com.tcwcontrol.app.ui.theme.OnlineGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val vm = appViewModel { DeviceListViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val refreshSeconds by vm.refreshSeconds.collectAsStateWithLifecycle()
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val alarmCheckSeconds by vm.alarmCheckSeconds.collectAsStateWithLifecycle()
    var pickInterval by remember { mutableStateOf(false) }
    var alarmSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun setMonitoring(enabled: Boolean) {
        scope.launch {
            vm.setMonitoringEnabled(enabled)
            if (enabled) MonitorService.start(context) else MonitorService.stop(context)
        }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setMonitoring(true)
    }
    fun toggleMonitoring() {
        when {
            monitoring -> setMonitoring(false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> setMonitoring(true)
        }
    }

    WhileVisible { vm.pollForever() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_devices), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { alarmSettings = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = stringResource(
                                if (monitoring) R.string.alarm_notifications_on else R.string.alarm_notifications_off,
                            ),
                            tint = if (monitoring) MaterialTheme.colorScheme.primary else IndicatorOff,
                        )
                    }
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
                    TextButton(onClick = { pickInterval = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_interval))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.seconds, refreshSeconds))
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ConnectionSummary(rows) }
                items(rows, key = { it.config.id }) { row ->
                    DeviceCard(row, onClick = { onOpen(row.config.id) }, onEdit = { onEdit(row.config.id) })
                }
            }
        }
    }

    if (alarmSettings) {
        AlarmSettingsDialog(
            enabled = monitoring,
            seconds = alarmCheckSeconds,
            onToggle = { toggleMonitoring() },
            onPick = vm::setAlarmCheckSeconds,
            onDismiss = { alarmSettings = false },
        )
    }

    if (pickInterval) {
        IntervalDialog(
            current = refreshSeconds,
            onPick = {
                vm.setRefreshSeconds(it)
                pickInterval = false
            },
            onDismiss = { pickInterval = false },
        )
    }
}

@Composable
private fun DeviceCard(row: DeviceRow, onClick: () -> Unit, onEdit: () -> Unit) {
    val config = row.config
    val model = (row.connection as? Connection.Online)?.status?.model ?: config.lastModel
    val title = when {
        config.isDemo -> stringResource(R.string.demo_unit)
        model.isNotEmpty() -> model
        else -> stringResource(R.string.unknown_device)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val subtitle = config.description.ifEmpty { "${config.host}:${config.port}" }
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionBadge(row.connection)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onEdit,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text(stringResource(R.string.edit)) }
        }
    }
}

/** Dot plus a word, so online and offline read at a glance without relying on colour alone. */
@Composable
private fun ConnectionBadge(connection: Connection) {
    val (color, label) = when (connection) {
        Connection.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.connecting)
        is Connection.Online -> if (connection.status.hasAlarm) {
            AlarmRed to stringResource(R.string.alarm)
        } else {
            OnlineGreen to stringResource(R.string.online)
        }
        is Connection.Offline -> OfflineGrey to stringResource(R.string.offline)
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (connection == Connection.Unknown) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = color)
        } else {
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionSummary(rows: List<DeviceRow>) {
    val online = rows.count { it.connection is Connection.Online }
    val offline = rows.count { it.connection is Connection.Offline }
    val alarm = rows.count { (it.connection as? Connection.Online)?.status?.hasAlarm == true }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.summary_online, online), color = OnlineGreen, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.summary_offline, offline), color = OfflineGrey, fontWeight = FontWeight.SemiBold)
        if (alarm > 0) Text(stringResource(R.string.summary_alarm, alarm), color = AlarmRed, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AlarmSettingsDialog(
    enabled: Boolean,
    seconds: Int,
    onToggle: () -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_notifications)) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = enabled, onValueChange = { onToggle() }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.alarm_notifications), Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = null)
                }
                Text(
                    stringResource(R.string.alarm_check_interval),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                DeviceRepository.ALARM_CHECK_OPTIONS.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = option == seconds, enabled = enabled) { onPick(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == seconds, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(8.dp))
                        Text(intervalLabel(option))
                    }
                }
                Text(
                    stringResource(R.string.alarm_check_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun intervalLabel(seconds: Int) =
    if (seconds < 60) stringResource(R.string.seconds, seconds) else stringResource(R.string.minutes, seconds / 60)

@Composable
private fun IntervalDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refresh_interval)) },
        text = {
            Column {
                DeviceRepository.REFRESH_OPTIONS.forEach { seconds ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = seconds == current) { onPick(seconds) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == current, onClick = { onPick(seconds) })
                        Text(stringResource(R.string.seconds, seconds))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
