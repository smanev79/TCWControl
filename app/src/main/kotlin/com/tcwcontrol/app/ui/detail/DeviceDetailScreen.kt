package com.tcwcontrol.app.ui.detail

import androidx.compose.foundation.background
import com.tcwcontrol.app.ui.theme.OnlineGreen
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
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
import com.tcwcontrol.app.ui.theme.PulseAmber
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val vm = appViewModel { DeviceDetailViewModel(createSavedStateHandle(), it) }
    val device by vm.device.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val confirmRelays by vm.confirmRelays.collectAsStateWithLifecycle()
    var pendingRelay by remember { mutableStateOf<Pair<Int, RelayAction>?>(null) }
    fun onRelay(index: Int, action: RelayAction) {
        if (confirmRelays) pendingRelay = index to action else vm.relay(index, action)
    }

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
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refreshNow,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    status != null -> StatusList(status, state.busyRelays, state.pulses, ::onRelay)
                    state.error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    // Nothing to show yet, but the list still takes the pull gesture so an offline device can be retried.
                    else -> LazyColumn(Modifier.fillMaxSize()) {}
                }
            }
        }
    }

    pendingRelay?.let { (index, action) ->
        val name = state.status?.relays?.firstOrNull { it.index == index }?.name.orEmpty().ifEmpty { "Relay $index" }
        AlertDialog(
            onDismissRequest = { pendingRelay = null },
            title = {
                Text(
                    stringResource(
                        when (action) {
                            RelayAction.ON -> R.string.confirm_relay_on
                            RelayAction.OFF -> R.string.confirm_relay_off
                            RelayAction.PULSE -> R.string.confirm_relay_pulse
                        },
                        name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRelay = null
                    vm.relay(index, action)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingRelay = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatusList(
    status: DeviceStatus,
    busyRelays: Set<Int>,
    pulses: Map<Int, Long>,
    onRelay: (Int, RelayAction) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        section(R.string.sensors, status.sensors) { SensorRow(it) }
        val analogTitle = if (status.family == DeviceFamily.CHANNELS) R.string.channels else R.string.analog_inputs
        section(analogTitle, status.analogInputs) { AnalogRow(it) }
        section(R.string.virtual_inputs, status.virtualInputs.filter { it.reading.isPresent }) { AnalogRow(it) }
        section(R.string.digital_inputs, status.digitalInputs) { DigitalRow(it) }
        section(R.string.relays, status.relays) { relay ->
            RelayRow(
                relay,
                busy = relay.index in busyRelays,
                pulseEndsAt = pulses[relay.index],
                onRelay = { onRelay(relay.index, it) },
            )
        }
    }
}

private fun <T> LazyListScope.section(title: Int, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item {
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 14.dp, top = 10.dp, bottom = 4.dp),
        )
    }
    items(rows) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { row(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One compact line: status dot, name with an optional note under it, and the value on the right. */
@Composable
private fun RowLayout(
    name: String,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    dot: Color? = null,
    alarm: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (alarm) Modifier.background(AlarmRed.copy(alpha = 0.07f)) else Modifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                    fontWeight = if (subtitleColor.isSpecified) FontWeight.Bold else null,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun readingDot(present: Boolean, alarm: Boolean) = when {
    alarm -> AlarmRed
    present -> OnlineGreen
    else -> IndicatorOff
}

@Composable
private fun ValueText(reading: Reading) {
    val color = if (reading.alarm) AlarmRed else MaterialTheme.colorScheme.onSurface
    val text = if (reading.isPresent) "${reading.value} ${reading.unit}".trim() else "---"
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SensorRow(sensor: Sensor) = RowLayout(
    sensor.name,
    dot = readingDot(sensor.isConnected, sensor.alarm),
    alarm = sensor.alarm,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        sensor.readings.filter { it.isPresent }.ifEmpty { sensor.readings.take(1) }.forEach { ValueText(it) }
    }
}

@Composable
private fun AnalogRow(input: AnalogInput) = RowLayout(
    input.name,
    input.reading.alarmLabel,
    dot = readingDot(input.reading.isPresent, input.reading.alarm),
    alarm = input.reading.alarm,
) { ValueText(input.reading) }

@Composable
private fun DigitalRow(input: DigitalInput) = RowLayout(
    input.name,
    input.alarmLabel,
    dot = readingDot(present = true, alarm = input.alarm),
    alarm = input.alarm,
) {
    Text(
        input.state,
        color = if (input.alarm) AlarmRed else MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun RelayRow(relay: Relay, busy: Boolean, pulseEndsAt: Long?, onRelay: (RelayAction) -> Unit) {
    val secondsLeft by produceState<Long?>(null, pulseEndsAt) {
        while (pulseEndsAt != null) {
            val left = pulseEndsAt - System.currentTimeMillis()
            value = (left + 999) / 1000
            if (left <= 0) break
            delay(left % 1000 + 1)
        }
    }
    val subtitle = when {
        pulseEndsAt != null && (secondsLeft ?: 1) > 0 -> stringResource(R.string.pulse_running, secondsLeft ?: 0)
        pulseEndsAt != null -> stringResource(R.string.pulse_checking)
        else -> relay.controlledBy?.let { stringResource(R.string.auto_control, it) }
    }
    RowLayout(
        relay.name,
        subtitle,
        subtitleColor = if (pulseEndsAt != null) PulseAmber else Color.Unspecified,
        dot = when {
            pulseEndsAt != null -> PulseAmber
            relay.isOn -> RelayOnGreen
            else -> IndicatorOff
        },
    ) {
        // The device switches this relay itself, so it only shows the state.
        if (relay.controlledBy != null) {
            Text(
                stringResource(if (relay.isOn) R.string.on else R.string.off),
                color = if (relay.isOn) RelayOnGreen else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@RowLayout
        }
        // During a pulse the buttons stay bright so the live state reads clearly; the view model ignores taps meanwhile.
        val pulsing = pulseEndsAt != null
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy && !pulsing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            RelayButton(
                label = stringResource(if (relay.isOn) R.string.on else R.string.off),
                indicator = if (relay.isOn) RelayOnGreen else IndicatorOff,
                enabled = !busy || pulsing,
                onClick = { onRelay(if (relay.isOn) RelayAction.OFF else RelayAction.ON) },
            )
            RelayButton(
                label = if (pulsing && (secondsLeft ?: 0) > 0) {
                    stringResource(R.string.pulse_seconds, secondsLeft ?: 0)
                } else {
                    stringResource(R.string.pulse)
                },
                indicator = if (pulsing) PulseAmber else IndicatorOff,
                enabled = !busy || pulsing,
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
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RelayButtonBg, contentColor = RelayButtonText),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
        modifier = Modifier.heightIn(min = 32.dp).widthIn(min = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(28.dp).height(3.dp).background(indicator, RoundedCornerShape(2.dp)))
        }
    }
}
