package com.tcwcontrol.app.data

import android.content.Context
import com.tcwcontrol.core.net.TrafficSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DeviceTraffic(
    val requests: Long = 0,
    val failures: Long = 0,
    val sentBytes: Long = 0,
    val receivedBytes: Long = 0,
    val lastRequest: Long = 0,
)

@Serializable
data class TrafficTotals(
    /** When counting started (first launch or the last reset). */
    val since: Long = System.currentTimeMillis(),
    /** Keyed by [TrafficCounter.key]. */
    val devices: Map<String, DeviceTraffic> = emptyMap(),
) {
    val requests: Long get() = devices.values.sumOf { it.requests }
    val failures: Long get() = devices.values.sumOf { it.failures }
    val sentBytes: Long get() = devices.values.sumOf { it.sentBytes }
    val receivedBytes: Long get() = devices.values.sumOf { it.receivedBytes }
}

/**
 * Counts the requests the app sends to devices and the bytes they move, per device, from screens
 * and the background monitor alike. Saved to a file a few seconds after the last change.
 */
class TrafficCounter(context: Context) {
    private val file = File(context.filesDir, "traffic.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)) // one write at a time
    private val lock = Any()
    private var pendingSave: Job? = null

    private val _totals = MutableStateFlow(
        runCatching { json.decodeFromString(TrafficTotals.serializer(), file.readText()) }.getOrDefault(TrafficTotals()),
    )
    val totals: StateFlow<TrafficTotals> = _totals.asStateFlow()

    /** Called by the HTTP client on its own thread after every request. */
    fun record(sample: TrafficSample) {
        synchronized(lock) {
            val current = _totals.value
            val key = key(sample.host, sample.port)
            val old = current.devices[key] ?: DeviceTraffic()
            val new = old.copy(
                requests = old.requests + 1,
                failures = old.failures + if (sample.success) 0 else 1,
                sentBytes = old.sentBytes + sample.sentBytes,
                receivedBytes = old.receivedBytes + sample.receivedBytes,
                lastRequest = System.currentTimeMillis(),
            )
            _totals.value = current.copy(devices = current.devices + (key to new))
            scheduleSave()
        }
    }

    fun reset() {
        synchronized(lock) {
            _totals.value = TrafficTotals()
            scheduleSave()
        }
    }

    private fun scheduleSave() {
        if (pendingSave?.isActive == true) return
        pendingSave = scope.launch {
            delay(SAVE_DELAY_MS)
            // Changes after this point schedule their own save.
            val snapshot = synchronized(lock) {
                pendingSave = null
                _totals.value
            }
            runCatching { file.writeText(json.encodeToString(TrafficTotals.serializer(), snapshot)) }
        }
    }

    companion object {
        private const val SAVE_DELAY_MS = 5_000L

        fun key(host: String, port: Int) = "${host.trim().lowercase()}:$port"
    }
}
