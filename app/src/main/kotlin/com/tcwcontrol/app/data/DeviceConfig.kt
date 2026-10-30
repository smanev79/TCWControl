package com.tcwcontrol.app.data

import kotlinx.serialization.Serializable

/** A device the user added. The password is stored encrypted, see [SecretBox]. */
@Serializable
data class DeviceConfig(
    val id: String,
    val host: String,
    val port: Int = 80,
    val description: String = "",
    /** Site or room the device belongs to; empty when ungrouped. */
    val group: String = "",
    val username: String = "",
    val encryptedPassword: String = "",
    val isDemo: Boolean = false,
    val useHttps: Boolean = false,
    /** Accept the device's certificate even if it is self-signed. */
    val allowSelfSigned: Boolean = false,
    /** Model name from the last successful status.xml, shown before the next refresh. */
    val lastModel: String = "",
)

object DemoUnit {
    const val HOST = "212.25.45.120"
    const val PORT = 60241
    const val USERNAME = "admin"
    const val PASSWORD = "admin"
    const val DESCRIPTION = "TCW241, Teracom, Ruse"
}
