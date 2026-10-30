package com.tcwcontrol.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcwcontrol.app.R
import com.tcwcontrol.app.data.DeviceRepository
import com.tcwcontrol.app.data.DeviceTraffic
import com.tcwcontrol.app.data.ThemeMode
import com.tcwcontrol.app.monitor.MonitorService
import com.tcwcontrol.app.ui.appViewModel
import com.tcwcontrol.app.ui.theme.AlarmRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SYSTEM_COUNTER_REFRESH_MS = 2_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val alarmCheckSeconds by vm.alarmCheckSeconds.collectAsStateWithLifecycle()
    val alarmLog by vm.alarmLogEnabled.collectAsStateWithLifecycle()
    val refreshSeconds by vm.refreshSeconds.collectAsStateWithLifecycle()
    val confirmRelays by vm.confirmRelays.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    var pickAlarmInterval by remember { mutableStateOf(false) }
    var pickRefresh by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    val theme by vm.themeMode.collectAsStateWithLifecycle()
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
    fun toggleMonitoring(enable: Boolean) {
        when {
            !enable -> setMonitoring(false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> setMonitoring(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.section_appearance))
            ValueRow(
                title = stringResource(R.string.theme),
                value = themeLabel(theme),
                onClick = { pickTheme = true },
            )

            SectionTitle(stringResource(R.string.section_alarms))
            SwitchRow(
                title = stringResource(R.string.alarm_notifications),
                summary = stringResource(R.string.alarm_notifications_summary),
                checked = monitoring,
                onChange = ::toggleMonitoring,
            )
            ValueRow(
                title = stringResource(R.string.alarm_check_interval),
                summary = stringResource(R.string.alarm_check_hint),
                value = intervalLabel(alarmCheckSeconds),
                enabled = monitoring,
                onClick = { pickAlarmInterval = true },
            )
            SwitchRow(
                title = stringResource(R.string.record_alarm_log),
                summary = stringResource(R.string.record_alarm_log_summary),
                checked = alarmLog,
                onChange = vm::setAlarmLogEnabled,
            )

            SectionTitle(stringResource(R.string.section_devices))
            ValueRow(
                title = stringResource(R.string.refresh_interval),
                summary = stringResource(R.string.refresh_interval_summary),
                value = intervalLabel(refreshSeconds),
                onClick = { pickRefresh = true },
            )
            SwitchRow(
                title = stringResource(R.string.confirm_relays),
                summary = stringResource(R.string.confirm_relays_summary),
                checked = confirmRelays,
                onChange = vm::setConfirmRelays,
            )

            SectionTitle(stringResource(R.string.section_traffic))
            TrafficSection(traffic, onReset = { confirmReset = true })

            SectionTitle(stringResource(R.string.section_about))
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            ValueRow(title = stringResource(R.string.app_name), value = version.orEmpty())
        }
    }

    if (pickAlarmInterval) {
        OptionDialog(
            title = stringResource(R.string.alarm_check_interval),
            options = DeviceRepository.ALARM_CHECK_OPTIONS,
            current = alarmCheckSeconds,
            onPick = {
                vm.setAlarmCheckSeconds(it)
                pickAlarmInterval = false
            },
            onDismiss = { pickAlarmInterval = false },
            label = { intervalLabel(it) },
        )
    }
    if (pickRefresh) {
        OptionDialog(
            title = stringResource(R.string.refresh_interval),
            options = DeviceRepository.REFRESH_OPTIONS,
            current = refreshSeconds,
            onPick = {
                vm.setRefreshSeconds(it)
                pickRefresh = false
            },
            onDismiss = { pickRefresh = false },
            label = { intervalLabel(it) },
        )
    }
    if (pickTheme) {
        OptionDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            current = theme,
            onPick = {
                vm.setThemeMode(it)
                pickTheme = false
            },
            onDismiss = { pickTheme = false },
            label = { themeLabel(it) },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_traffic_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetTraffic()
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrafficSection(traffic: TrafficView, onReset: () -> Unit) {
    val context = LocalContext.current
    val totals = traffic.totals
    val since = DateUtils.formatDateTime(
        context,
        totals.since,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.traffic_since, since), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stat(stringResource(R.string.traffic_received), bytes(totals.receivedBytes))
                Stat(stringResource(R.string.traffic_sent), bytes(totals.sentBytes))
                Stat(stringResource(R.string.traffic_requests), totals.requests.toString())
                Stat(
                    stringResource(R.string.traffic_failures),
                    totals.failures.toString(),
                    color = if (totals.failures > 0) AlarmRed else Color.Unspecified,
                )
            }
        }

        traffic.rows.forEach { row ->
            DeviceTrafficCard(row)
        }

        SystemCounter()

        Text(
            stringResource(R.string.traffic_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onReset, enabled = totals.requests > 0, shape = MaterialTheme.shapes.small) {
            Text(stringResource(R.string.reset_traffic))
        }
    }
}

@Composable
private fun DeviceTrafficCard(row: TrafficRow) {
    val t: DeviceTraffic = row.traffic
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                row.name ?: stringResource(R.string.deleted_device),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(row.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    R.string.traffic_device_line,
                    bytes(t.receivedBytes),
                    bytes(t.sentBytes),
                    t.requests,
                    t.failures,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** What Android itself counts for the app since the phone started, including anything outside the device requests. */
@Composable
private fun SystemCounter() {
    val uid = remember { Process.myUid() }
    val counts by produceState<Pair<Long, Long>?>(null) {
        while (true) {
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            value = if (rx < 0 || tx < 0) null else rx to tx
            delay(SYSTEM_COUNTER_REFRESH_MS)
        }
    }
    counts?.let { (rx, tx) ->
        Text(
            stringResource(R.string.traffic_system, bytes(rx), bytes(tx)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun bytes(count: Long): String = Formatter.formatShortFileSize(LocalContext.current, count)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<T>,
    current: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    label: @Composable (T) -> String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = option == current) { onPick(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    },
)

@Composable
private fun intervalLabel(seconds: Int) =
    if (seconds < 60) stringResource(R.string.seconds, seconds) else stringResource(R.string.minutes, seconds / 60)
