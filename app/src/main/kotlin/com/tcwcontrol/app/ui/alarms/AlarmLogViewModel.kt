package com.tcwcontrol.app.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcwcontrol.app.data.AlarmEvent
import com.tcwcontrol.app.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A device that appears in the log, for the filter chips. */
data class LogDevice(val id: String, val name: String)

data class AlarmLogState(
    val devices: List<LogDevice> = emptyList(),
    /** null shows every device. */
    val selected: String? = null,
    val events: List<AlarmEvent> = emptyList(),
)

class AlarmLogViewModel(private val repository: DeviceRepository) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)

    val state: StateFlow<AlarmLogState> = combine(repository.alarmLog.events, selected) { events, sel ->
        AlarmLogState(
            devices = events.distinctBy { it.deviceId }.map { LogDevice(it.deviceId, it.deviceName) }
                .sortedBy { it.name.lowercase() },
            selected = sel,
            events = if (sel == null) events else events.filter { it.deviceId == sel },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmLogState())

    init {
        viewModelScope.launch { repository.alarmLog.load() }
    }

    fun select(deviceId: String?) {
        selected.value = deviceId
    }

    fun clear() {
        viewModelScope.launch { repository.alarmLog.clear() }
    }
}
