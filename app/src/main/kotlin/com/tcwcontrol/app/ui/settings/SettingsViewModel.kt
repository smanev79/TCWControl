package com.tcwcontrol.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcwcontrol.app.data.DeviceRepository
import com.tcwcontrol.app.data.DeviceTraffic
import com.tcwcontrol.app.data.ThemeMode
import com.tcwcontrol.app.data.TrafficCounter
import com.tcwcontrol.app.data.TrafficTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Traffic of one device; [name] is null for a device that has since been deleted. */
data class TrafficRow(val name: String?, val address: String, val traffic: DeviceTraffic)

data class TrafficView(val totals: TrafficTotals, val rows: List<TrafficRow>)

class SettingsViewModel(private val repository: DeviceRepository) : ViewModel() {
    val monitoringEnabled = repository.monitoringEnabled.state(false)
    val alarmCheckSeconds = repository.alarmCheckSeconds.state(DeviceRepository.DEFAULT_ALARM_CHECK_SECONDS)
    val alarmLogEnabled = repository.alarmLogEnabled.state(true)
    val refreshSeconds = repository.refreshSeconds.state(DeviceRepository.DEFAULT_REFRESH_SECONDS)
    val confirmRelays = repository.confirmRelays.state(false)
    val themeMode = repository.themeMode.state(ThemeMode.LIGHT)

    val traffic: StateFlow<TrafficView> = combine(repository.traffic.totals, repository.devices) { totals, devices ->
        val names = devices.associate { TrafficCounter.key(it.host, it.port) to repository.displayName(it) }
        val rows = totals.devices.map { (key, traffic) -> TrafficRow(names[key], key, traffic) }
            .sortedByDescending { it.traffic.receivedBytes + it.traffic.sentBytes }
        TrafficView(totals, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrafficView(repository.traffic.totals.value, emptyList()))

    /** Saved before the service starts, because the service stops itself when the setting is off. */
    suspend fun setMonitoringEnabled(enabled: Boolean) = repository.setMonitoringEnabled(enabled)

    fun setAlarmCheckSeconds(seconds: Int) = launch { repository.setAlarmCheckSeconds(seconds) }
    fun setAlarmLogEnabled(enabled: Boolean) = launch { repository.setAlarmLogEnabled(enabled) }
    fun setRefreshSeconds(seconds: Int) = launch { repository.setRefreshSeconds(seconds) }
    fun setConfirmRelays(enabled: Boolean) = launch { repository.setConfirmRelays(enabled) }
    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }

    fun resetTraffic() = repository.traffic.reset()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
