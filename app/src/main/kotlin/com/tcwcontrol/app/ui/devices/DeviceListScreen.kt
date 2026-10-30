package com.tcwcontrol.app.ui.devices

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
    var pickInterval by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { toggleMonitoring() }) {
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
                items(rows, key = { it.config.id }) { row ->
                    DeviceCard(row, onClick = { onOpen(row.config.id) }, onEdit = { onEdit(row.config.id) })
                }
            }
        }
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
            ConnectionIcon(row.connection)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onEdit,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text(stringResource(R.string.edit)) }
        }
    }
}

@Composable
private fun ConnectionIcon(connection: Connection) {
    when (connection) {
        Connection.Unknown -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        is Connection.Online -> if (connection.status.hasAlarm) {
            Icon(Icons.Default.Warning, stringResource(R.string.alarm), tint = AlarmRed)
        } else {
            Icon(Icons.Default.CheckCircle, stringResource(R.string.online), tint = OnlineGreen)
        }
        is Connection.Offline -> Icon(
            Icons.Default.Close,
            stringResource(R.string.offline),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
