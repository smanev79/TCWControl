package com.tcwcontrol.app.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcwcontrol.app.data.DemoUnit
import com.tcwcontrol.app.data.DeviceConfig
import com.tcwcontrol.app.data.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditForm(
    val isDemo: Boolean = false,
    val host: String = "",
    val port: String = "80",
    val description: String = "",
    val group: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = false,
    val allowSelfSigned: Boolean = false,
) {
    val hostError: Boolean get() = host.isBlank()
    val portError: Boolean get() = port.toIntOrNull()?.let { it !in 1..65535 } ?: true
}

class EditDeviceViewModel(
    savedState: SavedStateHandle,
    private val repository: DeviceRepository,
) : ViewModel() {
    private val deviceId: String? = savedState["id"]
    private var existing: DeviceConfig? = null

    val isNew: Boolean get() = deviceId == null
    var form by mutableStateOf(EditForm())
        private set
    var showErrors by mutableStateOf(false)
        private set

    /** Groups already in use, offered as one-tap choices. */
    val existingGroups: StateFlow<List<String>> = repository.devices
        .map { list -> list.map { it.group }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (deviceId != null) {
            viewModelScope.launch {
                val device = repository.device(deviceId).first() ?: return@launch
                existing = device
                form = EditForm(
                    isDemo = device.isDemo,
                    host = device.host,
                    port = device.port.toString(),
                    description = device.description,
                    group = device.group,
                    username = device.username,
                    password = repository.password(device),
                    useHttps = device.useHttps,
                    allowSelfSigned = device.allowSelfSigned,
                )
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        form = transform(form)
    }

    /** Also swaps the standard port, so a device on the default port needs no second change. */
    fun setHttps(enabled: Boolean) {
        form = form.copy(
            useHttps = enabled,
            port = when {
                enabled && form.port == "80" -> "443"
                !enabled && form.port == "443" -> "80"
                else -> form.port
            },
        )
    }

    fun setDemo(enabled: Boolean) {
        form = if (enabled) {
            EditForm(
                group = form.group,
                isDemo = true,
                host = DemoUnit.HOST,
                port = DemoUnit.PORT.toString(),
                description = DemoUnit.DESCRIPTION,
                username = DemoUnit.USERNAME,
                password = DemoUnit.PASSWORD,
            )
        } else {
            EditForm()
        }
    }

    fun save(onSaved: () -> Unit) {
        val f = form
        if (f.hostError || f.portError) {
            showErrors = true
            return
        }
        viewModelScope.launch {
            val base = existing ?: DeviceConfig(id = "", host = "")
            repository.save(
                base.copy(
                    host = f.host.trim(),
                    port = f.port.toInt(),
                    description = f.description.trim(),
                    group = f.group.trim(),
                    username = f.username.trim(),
                    isDemo = f.isDemo,
                    useHttps = f.useHttps,
                    allowSelfSigned = f.useHttps && f.allowSelfSigned,
                ),
                plainPassword = f.password,
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = deviceId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }
}
