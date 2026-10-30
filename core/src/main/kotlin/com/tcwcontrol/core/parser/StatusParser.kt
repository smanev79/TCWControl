package com.tcwcontrol.core.parser

import com.tcwcontrol.core.model.DeviceStatus
import java.io.ByteArrayInputStream
import java.io.InputStream

class UnsupportedDeviceException(message: String) : Exception(message)

/**
 * Entry point: detects which status.xml layout the device returned and delegates to the
 * matching parser. Both layouts share the <Monitor> root:
 *  - modern devices (TCW241, TCW220, TCW210TH...) have a <DeviceInfo> block and S/AI/VI/DI/R groups;
 *  - firmware 3.x devices (TCW242, TCW260...) have <DeviceInfo> and a generic <CH> channel list;
 *  - classic CM devices (TCW181B-CM, TCW122B-CM...) have a flat list starting with <Device>.
 */
object StatusParser {
    fun parse(input: InputStream): DeviceStatus {
        val root = Xml.parse(input)
        if (root.tagName != "Monitor") {
            throw UnsupportedDeviceException("Unexpected root element <${root.tagName}>")
        }
        return when {
            root.child("DeviceInfo") != null && root.child("CH") != null -> ChannelParser.parse(root)
            root.child("DeviceInfo") != null -> ModernParser.parse(root)
            root.child("Device") != null -> ClassicParser.parse(root)
            else -> throw UnsupportedDeviceException("Unknown status.xml layout")
        }
    }

    fun parse(bytes: ByteArray): DeviceStatus = parse(ByteArrayInputStream(bytes))
}
