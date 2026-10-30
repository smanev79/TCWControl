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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

class DeviceListViewModel(private val repository: DeviceRepository) : ViewModel() {
    private val connections = MutableStateFlow<Map<String, Connection>>(emptyMap())

    val rows: StateFlow<List<DeviceRow>> = combine(repository.devices, connections) { devices, conn ->
        devices.map { DeviceRow(it, conn[it.id] ?: Connection.Unknown) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Polls every device until the caller's scope is cancelled (the screen leaves the foreground). */
    suspend fun pollForever() {
        while (true) {
            refreshAll()
            delay(repository.refreshSeconds.first() * 1000L)
        }
    }

    private suspend fun refreshAll() = coroutineScope {
        repository.devices.first().map { device ->
            async {
                val result = runCatching { repository.fetchStatus(device) }
                    .fold({ Connection.Online(it) }, { Connection.Offline(it) })
                connections.update { it + (device.id to result) }
            }
        }.awaitAll()
    }
}
