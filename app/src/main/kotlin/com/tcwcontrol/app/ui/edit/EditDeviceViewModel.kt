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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditForm(
    val isDemo: Boolean = false,
    val host: String = "",
    val port: String = "80",
    val description: String = "",
    val username: String = "",
    val password: String = "",
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
                    username = device.username,
                    password = repository.password(device),
                )
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        form = transform(form)
    }

    fun setDemo(enabled: Boolean) {
        form = if (enabled) {
            EditForm(
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
                    username = f.username.trim(),
                    isDemo = f.isDemo,
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
