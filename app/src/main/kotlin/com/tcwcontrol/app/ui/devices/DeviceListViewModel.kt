package com.tcwcontrol.app.ui.devices

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcwcontrol.app.data.DeviceConfig
import com.tcwcontrol.app.data.DeviceRepository
import com.tcwcontrol.app.data.ImportResult
import com.tcwcontrol.app.data.QrCodec
import com.tcwcontrol.core.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Connection {
    data object Unknown : Connection
    data class Online(val status: DeviceStatus) : Connection
    data class Offline(val error: Throwable) : Connection
}

data class DeviceRow(val config: DeviceConfig, val connection: Connection) {
    val model: String get() = (connection as? Connection.Online)?.status?.model ?: config.lastModel

    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() ||
            listOf(config.description, config.host, config.group, model).any { it.contains(q, ignoreCase = true) }
    }
}

/** Named groups in the user's order, then the rest by name; the ungrouped devices ("") last. */
fun sortGroups(names: Collection<String>, order: List<String>): List<String> =
    names.distinct().sortedWith(
        compareBy<String>({ it.isEmpty() }, { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.lowercase() }),
    )

class DeviceListViewModel(private val repository: DeviceRepository) : ViewModel() {
    private val connections = MutableStateFlow<Map<String, Connection>>(emptyMap())

    val rows: StateFlow<List<DeviceRow>> = combine(repository.devices, connections) { devices, conn ->
        devices.map { DeviceRow(it, conn[it.id] ?: Connection.Unknown) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupOrder: StateFlow<List<String>> = repository.groupOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveOrder(ids: List<String>) {
        viewModelScope.launch { repository.reorderDevices(ids) }
    }

    fun setGroupOrder(groups: List<String>) {
        viewModelScope.launch { repository.setGroupOrder(groups) }
    }

    fun renameGroup(from: String, to: String) {
        viewModelScope.launch { repository.renameGroup(from, to) }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(text: String) {
        _query.value = text
    }

    /** Writes the device list to [uri] and returns how many devices were exported. */
    suspend fun exportTo(resolver: ContentResolver, uri: Uri, includePasswords: Boolean): Int {
        val (text, count) = repository.exportDevices(includePasswords)
        withContext(Dispatchers.IO) {
            checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
        }
        return count
    }

    /** QR-code text for the device list and the number of devices in it. */
    suspend fun qrText(includePasswords: Boolean): Pair<String, Int> {
        val (json, count) = repository.exportDevices(includePasswords, compact = true)
        return QrCodec.encode(json) to count
    }

    suspend fun importText(text: String): ImportResult = repository.importDevices(text)

    suspend fun importFrom(resolver: ContentResolver, uri: Uri): ImportResult {
        val text = withContext(Dispatchers.IO) {
            checkNotNull(resolver.openInputStream(uri)).use { it.readBytes().decodeToString() }
        }
        return repository.importDevices(text)
    }

    /**
     * Polls every device until the caller's scope is cancelled (the screen leaves the foreground).
     * Starts over at once when a device is added, removed or its address changes, or the interval
     * changes, so a new device does not wait a whole interval for its first status.
     */
    suspend fun pollForever() {
        val targets = repository.devices
            .map { list -> list.map { it.copy(description = "", group = "", lastModel = "") }.sortedBy { it.id } }
            .distinctUntilChanged()
        combine(targets, repository.refreshSeconds) { devices, seconds -> devices to seconds }
            .collectLatest { (devices, seconds) ->
                while (true) {
                    refreshAll(devices)
                    delay(seconds * 1000L)
                }
            }
    }

    private suspend fun refreshAll(devices: List<DeviceConfig>) = coroutineScope {
        devices.map { device ->
            async {
                val result = runCatching { repository.fetchStatus(device) }
                    .fold({ Connection.Online(it) }, { Connection.Offline(it) })
                connections.update { it + (device.id to result) }
            }
        }.awaitAll()
    }
}
