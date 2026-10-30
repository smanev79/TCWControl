package com.tcwcontrol.app.data

import android.content.Context
import com.tcwcontrol.core.model.ActiveAlarm
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.model.activeAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AlarmEvent(
    val time: Long,
    val deviceId: String,
    val deviceName: String,
    val text: String,
    /** true when the alarm started, false when it cleared. */
    val started: Boolean,
)

/**
 * Remembers which alarms are active on each device and records when each one starts and clears.
 * Every successful status read goes through [observe], whether it came from a screen or from the
 * background monitor, so the log is the same whichever of them saw the change first.
 */
class AlarmLog(context: Context) {
    @Serializable
    private data class Stored(
        val events: List<AlarmEvent> = emptyList(),
        /** Device id -> alarm key -> alarm text, as of the last read. */
        val active: Map<String, Map<String, String>> = emptyMap(),
    )

    private val file = File(context.filesDir, "alarm_log.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var state: Stored? = null
    private val _events = MutableStateFlow<List<AlarmEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<AlarmEvent>> = _events.asStateFlow()

    suspend fun load() {
        mutex.withLock { loaded() }
    }

    /**
     * Records alarms that started or cleared since the last read of [device] and returns the started ones.
     * With [record] off only the active alarms are tracked, so notifications still work without a log.
     */
    suspend fun observe(
        device: DeviceConfig,
        deviceName: String,
        status: DeviceStatus,
        record: Boolean = true,
    ): List<ActiveAlarm> =
        mutex.withLock {
            val current = loaded()
            val now = System.currentTimeMillis()
            val alarms = status.activeAlarms()
            val before = current.active[device.id].orEmpty()
            val after = alarms.associate { it.key to it.text }
            val started = alarms.filter { it.key !in before }
            val cleared = before.filterKeys { it !in after }
            if (started.isEmpty() && cleared.isEmpty()) return@withLock emptyList()

            val newEvents = if (record) {
                started.map { AlarmEvent(now, device.id, deviceName, it.text, started = true) } +
                    cleared.values.map { AlarmEvent(now, device.id, deviceName, it, started = false) }
            } else {
                emptyList()
            }
            save(
                current.copy(
                    events = (newEvents + current.events).take(MAX_EVENTS),
                    active = current.active + (device.id to after),
                ),
            )
            started
        }

    suspend fun clear() = mutex.withLock { save(loaded().copy(events = emptyList())) }

    /** Forgets a deleted device's active alarms; its past events stay in the log. */
    suspend fun forget(deviceId: String) = mutex.withLock {
        val current = loaded()
        save(current.copy(active = current.active - deviceId))
    }

    private suspend fun loaded(): Stored = state ?: withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(Stored.serializer(), file.readText()) }.getOrDefault(Stored())
    }.also {
        state = it
        _events.value = it.events
    }

    private suspend fun save(new: Stored) {
        state = new
        _events.value = new.events
        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(Stored.serializer(), new)) }
    }

    private companion object {
        const val MAX_EVENTS = 1000
    }
}
