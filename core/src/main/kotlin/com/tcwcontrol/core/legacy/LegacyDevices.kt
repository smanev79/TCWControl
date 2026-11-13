package com.tcwcontrol.core.legacy

import com.tcwcontrol.core.parser.Xml
import com.tcwcontrol.core.parser.children
import com.tcwcontrol.core.parser.text
import kotlin.math.abs

/** A device saved by the previous TCW Control app (the .NET MAUI one, or the older Java one before it). */
data class LegacyDevice(
    /** MAUI only: the key of the device's password in the old secure storage ("device_password_" + id). */
    val id: String,
    val address: String,
    val port: Int,
    val description: String,
    val username: String,
    /** Plain-text password from old releases that kept it in the device list; empty otherwise. */
    val password: String,
    val isDemo: Boolean,
)

object LegacyDevices {
    /** Preferences key under which the MAUI app kept its device list. */
    const val DEVICES_KEY = "devices"

    /** Preferences key of the MAUI app's refresh setting, an index into [REFRESH_TIMES]. */
    const val REFRESH_KEY = "refresh"

    /** Secure storage key prefix of a device's password in the MAUI app. */
    const val PASSWORD_KEY_PREFIX = "device_password_"

    /** The MAUI app's refresh choices in seconds; 0 meant no automatic refresh. */
    val REFRESH_TIMES = listOf(0, 5, 10, 15, 20, 30, 60, 120, 180, 240, 300)

    /**
     * Reads the device list the MAUI app saved with .NET's XmlSerializer:
     * `<ArrayOfDevice><Device><Id/><IsDemoUnit/><Address/><Port/><Description/><Username/>...</Device></ArrayOfDevice>`.
     * Devices without an address are skipped. Returns an empty list when the text is not such a list.
     */
    fun parseMauiXml(xml: String): List<LegacyDevice> {
        // .NET writes encoding="utf-16" in the declaration although the text reaches us as a Java string.
        val body = xml.trim().replaceFirst(Regex("^<\\?xml[^>]*\\?>"), "").trim()
        if (body.isEmpty()) return emptyList()
        val root = runCatching { Xml.parse(body.byteInputStream(Charsets.UTF_8)) }.getOrNull() ?: return emptyList()
        if (root.tagName != "ArrayOfDevice") return emptyList()
        return root.children().filter { it.tagName == "Device" }.mapNotNull { d ->
            val address = d.text("Address")
            if (address.isEmpty()) return@mapNotNull null
            LegacyDevice(
                id = d.text("Id"),
                address = address,
                port = d.text("Port").toIntOrNull()?.takeIf { it in 1..65535 } ?: 80,
                description = d.text("Description"),
                username = d.text("Username"),
                password = d.text("Password"),
                isDemo = d.text("IsDemoUnit").equals("true", ignoreCase = true),
            )
        }
    }

    /**
     * The new app's refresh interval closest to the old setting [index], or null when the old app
     * did not refresh automatically or the index is unknown (the new default then applies).
     */
    fun refreshSeconds(index: Int, options: List<Int>): Int? {
        val seconds = REFRESH_TIMES.getOrNull(index)?.takeIf { it > 0 } ?: return null
        return options.minWithOrNull(compareBy<Int> { abs(it - seconds) }.thenByDescending { it })
    }
}
