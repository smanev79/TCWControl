package com.tcwcontrol.app.data

import kotlinx.serialization.Serializable

/** File format for moving the device list between phones. */
@Serializable
data class DeviceListFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val devices: List<TransferDevice>,
) {
    companion object {
        const val FORMAT = "tcw-control-devices"
    }
}

@Serializable
data class TransferDevice(
    val host: String,
    val port: Int = 80,
    val description: String = "",
    val group: String = "",
    val username: String = "",
    /** Plain text; only present when the user chose to include passwords. */
    val password: String? = null,
    val isDemo: Boolean = false,
)

data class ImportResult(val added: Int, val skipped: Int)

class InvalidDeviceFileException : Exception("Not a TCW Control device list")
