package com.tcwcontrol.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcwcontrol.app.monitor.AlarmNotifier
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

class DeviceRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    val traffic = TrafficCounter(context.applicationContext)
    private val client = TeracomClient(onTraffic = traffic::record)
    val alarmLog = AlarmLog(context.applicationContext)
    private val notifier = AlarmNotifier(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val exportJson = Json { prettyPrint = true; encodeDefaults = true }
    private val compactJson = Json { explicitNulls = false }

    val devices: Flow<List<DeviceConfig>> = store.data.map { prefs ->
        prefs[DEVICES]?.let { runCatching { json.decodeFromString<List<DeviceConfig>>(it) }.getOrNull() }
            ?: emptyList()
    }

    val refreshSeconds: Flow<Int> = store.data.map { it[REFRESH_SECONDS] ?: DEFAULT_REFRESH_SECONDS }

    val monitoringEnabled: Flow<Boolean> = store.data.map { it[MONITORING] ?: false }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { it[MONITORING] = enabled }
    }

    val alarmCheckSeconds: Flow<Int> = store.data.map { it[ALARM_CHECK_SECONDS] ?: DEFAULT_ALARM_CHECK_SECONDS }

    suspend fun setAlarmCheckSeconds(seconds: Int) {
        store.edit { it[ALARM_CHECK_SECONDS] = seconds }
    }

    val alarmLogEnabled: Flow<Boolean> = store.data.map { it[ALARM_LOG] ?: true }

    suspend fun setAlarmLogEnabled(enabled: Boolean) {
        store.edit { it[ALARM_LOG] = enabled }
    }

    /** Ask before a relay is switched or pulsed. */
    val confirmRelays: Flow<Boolean> = store.data.map { it[CONFIRM_RELAYS] ?: false }

    suspend fun setConfirmRelays(enabled: Boolean) {
        store.edit { it[CONFIRM_RELAYS] = enabled }
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

    suspend fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
        alarmLog.forget(id)
    }

    fun password(device: DeviceConfig): String = SecretBox.decrypt(device.encryptedPassword)

    /**
     * Reads the device's status, logs alarms that started or cleared and, when alarm notifications
     * are on, notifies about the ones that started.
     */
    suspend fun fetchStatus(device: DeviceConfig): DeviceStatus {
        val status = withContext(Dispatchers.IO) { client.fetchStatus(device.endpoint()) }
        rememberModel(device.id, status.model)
        val name = displayName(device, status.model)
        val started = alarmLog.observe(device, name, status, record = alarmLogEnabled.first())
        if (started.isNotEmpty() && monitoringEnabled.first()) notifier.showAlarms(name, device.id, started)
        return status
    }

    suspend fun controlRelay(device: DeviceConfig, status: DeviceStatus, relayIndex: Int, action: RelayAction) =
        withContext(Dispatchers.IO) {
            client.controlRelay(device.endpoint(), status.family, relayIndex, action)
        }

    /** The device list as JSON; passwords are written in plain text only when [includePasswords] is set. */
    suspend fun exportDevices(includePasswords: Boolean, compact: Boolean = false): Pair<String, Int> {
        val list = devices.first()
        val file = DeviceListFile(
            devices = list.map {
                TransferDevice(
                    host = it.host,
                    port = it.port,
                    description = it.description,
                    group = it.group,
                    username = it.username,
                    password = if (includePasswords) password(it) else null,
                    isDemo = it.isDemo,
                )
            },
        )
        val format = if (compact) compactJson else exportJson
        return format.encodeToString(DeviceListFile.serializer(), file) to list.size
    }

    /** Adds the devices from an exported list (a file's text or a scanned QR code), skipping ones already present (same host and port). */
    suspend fun importDevices(text: String): ImportResult {
        val file = runCatching { json.decodeFromString(DeviceListFile.serializer(), QrCodec.decode(text.trim())) }
            .getOrNull()
            ?.takeIf { it.format == DeviceListFile.FORMAT }
            ?: throw InvalidDeviceFileException()
        val existing = devices.first().map { it.host.lowercase() to it.port }.toMutableSet()
        var added = 0
        file.devices.forEach { d ->
            if (d.host.isBlank() || !existing.add(d.host.lowercase() to d.port)) return@forEach
            save(
                DeviceConfig(
                    id = "",
                    host = d.host,
                    port = d.port,
                    description = d.description,
                    group = d.group,
                    username = d.username,
                    isDemo = d.isDemo,
                ),
                plainPassword = d.password.orEmpty(),
            )
            added++
        }
        return ImportResult(added = added, skipped = file.devices.size - added)
    }

    /** The name shown for a device in notifications and the alarm log. */
    fun displayName(device: DeviceConfig, model: String = device.lastModel): String =
        device.description.ifEmpty { model.ifEmpty { device.host } }

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
        const val DEFAULT_ALARM_CHECK_SECONDS = 60
        val ALARM_CHECK_OPTIONS = listOf(30, 60, 120, 300, 600, 900, 1800)
        private val DEVICES = stringPreferencesKey("devices")
        private val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        private val MONITORING = booleanPreferencesKey("monitoring_enabled")
        private val ALARM_CHECK_SECONDS = intPreferencesKey("alarm_check_seconds")
        private val ALARM_LOG = booleanPreferencesKey("alarm_log_enabled")
        private val CONFIRM_RELAYS = booleanPreferencesKey("confirm_relays")
    }
}
