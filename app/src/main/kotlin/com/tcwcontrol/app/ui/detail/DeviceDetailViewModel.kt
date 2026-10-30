package com.tcwcontrol.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcwcontrol.app.data.DeviceConfig
import com.tcwcontrol.app.data.DeviceRepository
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.net.RelayAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DetailState(
    val status: DeviceStatus? = null,
    val error: Throwable? = null,
    /** Relays with a command in flight; each relay is independent of the others. */
    val busyRelays: Set<Int> = emptySet(),
    /** Relay index -> when its pulse ends, shown with a countdown until the relay is read back after it. */
    val pulses: Map<Int, Long> = emptyMap(),
)

class DeviceDetailViewModel(
    savedState: SavedStateHandle,
    private val repository: DeviceRepository,
) : ViewModel() {
    val deviceId: String = checkNotNull(savedState["id"])

    val device: StateFlow<DeviceConfig?> = repository.device(deviceId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val confirmRelays: StateFlow<Boolean> = repository.confirmRelays
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    /** One-off errors from relay commands, shown in a snackbar. */
    private val _relayErrors = Channel<Throwable>(Channel.BUFFERED)
    val relayErrors = _relayErrors.receiveAsFlow()

    // Keeps a poll and a relay command from overwriting each other's result.
    private val requestLock = Mutex()

    suspend fun pollForever() {
        while (true) {
            refresh()
            delay(repository.refreshSeconds.first() * 1000L)
        }
    }

    private suspend fun refresh(): DeviceStatus? {
        val config = device.filterNotNull().first()
        return requestLock.withLock {
            runCatching { repository.fetchStatus(config) }
                .onSuccess { status -> _state.update { it.copy(status = status, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = e) } }
                .getOrNull()
        }
    }

    fun relay(index: Int, action: RelayAction) {
        val config = device.value ?: return
        val status = _state.value.status ?: return
        if (index in _state.value.busyRelays) return
        _state.update { it.copy(busyRelays = it.busyRelays + index) }
        viewModelScope.launch {
            val sent = requestLock.withLock {
                runCatching { repository.controlRelay(config, status, index, action) }
                    .onSuccess { newStatus -> _state.update { it.copy(status = newStatus, error = null) } }
                    .onFailure { _relayErrors.send(it) }
                    .isSuccess
            }
            if (sent && action == RelayAction.PULSE) followPulse(index)
            _state.update { it.copy(busyRelays = it.busyRelays - index, pulses = it.pulses - index) }
        }
    }

    /**
     * Reads the device during the pulse so its state shows as it happens, then after the pulse ends
     * until the relay reads back off (or a few tries have passed).
     */
    private suspend fun followPulse(index: Int) {
        val seconds = _state.value.status?.relays?.firstOrNull { it.index == index }?.pulseWidth
            ?.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        val endsAt = System.currentTimeMillis() + (seconds * 1000).toLong()
        _state.update { it.copy(pulses = it.pulses + (index to endsAt)) }
        while (true) {
            val left = endsAt - System.currentTimeMillis()
            if (left <= 0) break
            delay(minOf(left, PULSE_POLL_MS))
            if (endsAt - System.currentTimeMillis() > 0) refresh()
        }
        repeat(AFTER_PULSE_TRIES) {
            delay(AFTER_PULSE_DELAY_MS)
            val relay = refresh()?.relays?.firstOrNull { it.index == index }
            if (relay != null && !relay.isOn) return
        }
    }

    private companion object {
        const val PULSE_POLL_MS = 1_000L
        const val AFTER_PULSE_DELAY_MS = 700L
        const val AFTER_PULSE_TRIES = 4
    }
}
