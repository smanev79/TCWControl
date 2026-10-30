package com.tcwcontrol.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.tcwcontrol.app.R
import com.tcwcontrol.app.ui.WhileVisible
import com.tcwcontrol.app.ui.appViewModel
import com.tcwcontrol.app.ui.theme.AlarmRed
import com.tcwcontrol.app.ui.theme.IndicatorOff
import com.tcwcontrol.app.ui.theme.RelayButtonBg
import com.tcwcontrol.app.ui.theme.RelayButtonText
import com.tcwcontrol.app.ui.theme.RelayOnGreen
import com.tcwcontrol.app.ui.toUserMessage
import com.tcwcontrol.core.model.AnalogInput
import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.model.DigitalInput
import com.tcwcontrol.core.model.Reading
import com.tcwcontrol.core.model.Relay
import com.tcwcontrol.core.model.Sensor
import com.tcwcontrol.core.net.RelayAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val vm = appViewModel { DeviceDetailViewModel(createSavedStateHandle(), it) }
    val device by vm.device.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    WhileVisible { vm.pollForever() }
    LaunchedEffect(vm) {
        vm.relayErrors.collect {
            snackbar.showSnackbar(context.getString(R.string.relay_failed, it.toUserMessage(context)))
        }
    }

    val title = state.status?.model?.ifEmpty { null } ?: device?.lastModel?.ifEmpty { null } ?: device?.host.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = { TextButton(onClick = { onEdit(vm.deviceId) }) { Text(stringResource(R.string.edit)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val status = state.status
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { error ->
                Text(
                    error.toUserMessage(context),
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.fillMaxWidth().background(AlarmRed).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                status != null -> StatusList(status, state.busyRelay, vm::relay)
                state.error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun StatusList(status: DeviceStatus, busyRelay: Int?, onRelay: (Int, RelayAction) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        section(R.string.sensors, status.sensors) { SensorRow(it) }
        val analogTitle = if (status.family == DeviceFamily.CHANNELS) R.string.channels else R.string.analog_inputs
        section(analogTitle, status.analogInputs) { AnalogRow(it) }
        section(R.string.virtual_inputs, status.virtualInputs.filter { it.reading.isPresent }) { AnalogRow(it) }
        section(R.string.digital_inputs, status.digitalInputs) { DigitalRow(it) }
        section(R.string.relays, status.relays) { relay ->
            RelayRow(relay, busy = busyRelay == relay.index, onRelay = { onRelay(relay.index, it) })
        }
    }
}

private fun <T> LazyListScope.section(title: Int, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item {
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
        )
    }
    items(rows) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { row(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RowLayout(name: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun ValueText(reading: Reading) {
    val color = if (reading.alarm) AlarmRed else MaterialTheme.colorScheme.onSurface
    val text = if (reading.isPresent) "${reading.value} ${reading.unit}".trim() else "---"
    Text(text, color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SensorRow(sensor: Sensor) = RowLayout(sensor.name) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sensor.readings.forEach { ValueText(it) }
    }
}

@Composable
private fun AnalogRow(input: AnalogInput) = RowLayout(input.name, input.reading.alarmLabel) { ValueText(input.reading) }

@Composable
private fun DigitalRow(input: DigitalInput) = RowLayout(input.name, input.alarmLabel) {
    Text(
        input.state,
        color = if (input.alarm) AlarmRed else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun RelayRow(relay: Relay, busy: Boolean, onRelay: (RelayAction) -> Unit) {
    val subtitle = relay.controlledBy?.let { stringResource(R.string.auto_control, it) }
    RowLayout(relay.name, subtitle) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            RelayButton(
                label = stringResource(if (relay.isOn) R.string.on else R.string.off),
                indicator = if (relay.isOn) RelayOnGreen else IndicatorOff,
                enabled = !busy,
                onClick = { onRelay(if (relay.isOn) RelayAction.OFF else RelayAction.ON) },
            )
            RelayButton(
                label = stringResource(R.string.pulse),
                indicator = IndicatorOff,
                enabled = !busy,
                onClick = { onRelay(RelayAction.PULSE) },
            )
        }
    }
}

@Composable
private fun RelayButton(label: String, indicator: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RelayButtonBg, contentColor = RelayButtonText),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(40.dp).height(4.dp).background(indicator, RoundedCornerShape(2.dp)))
        }
    }
}
