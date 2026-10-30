package com.tcwcontrol.core.parser

import com.tcwcontrol.core.model.AnalogInput
import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.model.DigitalInput
import com.tcwcontrol.core.model.Reading
import com.tcwcontrol.core.model.Relay
import com.tcwcontrol.core.model.Sensor
import org.w3c.dom.Element

/** Firmware 2.x layout: <DeviceInfo>, <S><S1><item1>..., <AI>, <VI>, <DI>, <R>. */
internal object ModernParser {
    fun parse(root: Element): DeviceStatus {
        val info = root.child("DeviceInfo")!!
        return DeviceStatus(
            family = DeviceFamily.MODERN,
            model = info.text("DeviceName"),
            hostName = info.text("HostName"),
            firmware = info.text("FwVer"),
            sensors = root.child("S")?.numbered("S").orEmpty().map { (i, s) ->
                Sensor(
                    index = i,
                    name = s.text("description").ifEmpty { "S$i" },
                    readings = s.numbered("item").map { (_, item) -> item.reading() },
                )
            },
            analogInputs = root.child("AI")?.numbered("AI").orEmpty().map { (i, a) -> a.analog(i, "AI") },
            virtualInputs = root.child("VI")?.numbered("VI").orEmpty().map { (i, a) -> a.analog(i, "VI") },
            digitalInputs = root.child("DI")?.numbered("DI").orEmpty().map { (i, d) ->
                DigitalInput(
                    index = i,
                    name = d.text("description").ifEmpty { "DI$i" },
                    state = d.text("value"),
                    alarm = d.text("alarm") == "1",
                )
            },
            relays = root.child("R")?.numbered("R").orEmpty().map { (i, r) ->
                Relay(
                    index = i,
                    name = r.text("description").ifEmpty { "R$i" },
                    isOn = r.text("valuebin") == "1" || r.text("value").equals("ON", ignoreCase = true),
                    pulseWidth = r.text("pulseWidth").ifEmpty { null },
                    controlledBy = r.text("control").takeUnless { it.isEmpty() || it == "0" },
                )
            },
        )
    }

    private fun Element.reading() = Reading(
        value = text("value"),
        unit = text("unit"),
        alarm = text("alarm") == "1",
    )

    private fun Element.analog(index: Int, prefix: String) = AnalogInput(
        index = index,
        name = text("description").ifEmpty { "$prefix$index" },
        reading = reading(),
    )
}
