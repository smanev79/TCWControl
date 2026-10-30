package com.tcwcontrol.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.net.Endpoint
import com.tcwcontrol.core.net.RelayAction
import com.tcwcontrol.core.net.TeracomClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "tcw_control")

class DeviceRepository(context: Context, private val client: TeracomClient = TeracomClient()) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val devices: Flow<List<DeviceConfig>> = store.data.map { prefs ->
        prefs[DEVICES]?.let { runCatching { json.decodeFromString<List<DeviceConfig>>(it) }.getOrNull() }
            ?: emptyList()
    }

    val refreshSeconds: Flow<Int> = store.data.map { it[REFRESH_SECONDS] ?: DEFAULT_REFRESH_SECONDS }

    val monitoringEnabled: Flow<Boolean> = store.data.map { it[MONITORING] ?: false }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { it[MONITORING] = enabled }
    }

    fun device(id: String): Flow<DeviceConfig?> = devices.map { list -> list.firstOrNull { it.id == id } }

    suspend fun setRefreshSeconds(seconds: Int) {
        store.edit { it[REFRESH_SECONDS] = seconds }
    }

    /** Inserts a new device when [DeviceConfig.id] is empty, otherwise replaces the existing one. */
    suspend fun save(device: DeviceConfig, plainPassword: String): String {
        val id = device.id.ifEmpty { UUID.randomUUID().toString() }
        val stored = device.copy(id = id, encryptedPassword = SecretBox.encrypt(plainPassword))
        update { list ->
            if (list.any { it.id == id }) list.map { if (it.id == id) stored else it } else list + stored
        }
        return id
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun password(device: DeviceConfig): String = SecretBox.decrypt(device.encryptedPassword)

    suspend fun fetchStatus(device: DeviceConfig): DeviceStatus = withContext(Dispatchers.IO) {
        client.fetchStatus(device.endpoint()).also { rememberModel(device.id, it.model) }
    }

    suspend fun controlRelay(device: DeviceConfig, status: DeviceStatus, relayIndex: Int, action: RelayAction) =
        withContext(Dispatchers.IO) {
            client.controlRelay(device.endpoint(), status.family, relayIndex, action)
        }

    private fun DeviceConfig.endpoint() = Endpoint(host, port, username, password(this))

    private suspend fun rememberModel(id: String, model: String) {
        if (devices.first().firstOrNull { it.id == id }?.lastModel == model) return
        update { list -> list.map { if (it.id == id) it.copy(lastModel = model) else it } }
    }

    private suspend fun update(transform: (List<DeviceConfig>) -> List<DeviceConfig>) {
        store.edit { prefs ->
            val current = prefs[DEVICES]
                ?.let { runCatching { json.decodeFromString<List<DeviceConfig>>(it) }.getOrNull() }
                ?: emptyList()
            prefs[DEVICES] = json.encodeToString(transform(current))
        }
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 5
        val REFRESH_OPTIONS = listOf(2, 5, 10, 30, 60)
        private val DEVICES = stringPreferencesKey("devices")
        private val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        private val MONITORING = booleanPreferencesKey("monitoring_enabled")
    }
}
