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
    /** Relay index with a command in flight; its buttons are disabled meanwhile. */
    val busyRelay: Int? = null,
)

class DeviceDetailViewModel(
    savedState: SavedStateHandle,
    private val repository: DeviceRepository,
) : ViewModel() {
    val deviceId: String = checkNotNull(savedState["id"])

    val device: StateFlow<DeviceConfig?> = repository.device(deviceId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private suspend fun refresh() {
        val config = device.filterNotNull().first()
        requestLock.withLock {
            runCatching { repository.fetchStatus(config) }
                .onSuccess { status -> _state.update { it.copy(status = status, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = e) } }
        }
    }

    fun relay(index: Int, action: RelayAction) {
        val config = device.value ?: return
        val status = _state.value.status ?: return
        if (_state.value.busyRelay != null) return
        _state.update { it.copy(busyRelay = index) }
        viewModelScope.launch {
            requestLock.withLock {
                runCatching { repository.controlRelay(config, status, index, action) }
                    .onSuccess { newStatus -> _state.update { it.copy(status = newStatus, error = null) } }
                    .onFailure { _relayErrors.send(it) }
            }
            _state.update { it.copy(busyRelay = null) }
        }
    }
}
