package com.tcwcontrol.core.parser

import com.tcwcontrol.core.model.AnalogInput
import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.model.DigitalInput
import com.tcwcontrol.core.model.Reading
import com.tcwcontrol.core.model.Relay
import org.w3c.dom.Element

/**
 * Firmware 3.x layout: <DeviceInfo>, then <CH><CH1>...<CH24> where every channel carries its own
 * description, value and unit, plus <R> relays. Alarms are graded by <alarmbin>:
 * 0 = none, 1 = Normal, 2 = no data ("---"), 3 = Warning, 4 = Minor, 5 = Major, 6 = Critical.
 */
internal object ChannelParser {
    private const val TYPE_DIGITAL = "2"
    private const val SOURCE_UNASSIGNED = "0"
    private const val FIRST_ALARM_LEVEL = 3

    fun parse(root: Element): DeviceStatus {
        val info = root.child("DeviceInfo")!!
        val channels = root.child("CH")?.numbered("CH").orEmpty()
            .filterNot { (_, ch) -> ch.text("selch") == SOURCE_UNASSIGNED }

        val digital = channels.filter { (_, ch) -> ch.text("type") == TYPE_DIGITAL }
        val values = channels.filterNot { (_, ch) -> ch.text("type") == TYPE_DIGITAL }

        return DeviceStatus(
            family = DeviceFamily.CHANNELS,
            model = info.text("DeviceName"),
            hostName = info.text("HostName"),
            firmware = info.text("FwVer"),
            analogInputs = values.map { (i, ch) ->
                AnalogInput(
                    index = i,
                    name = ch.text("description").ifEmpty { "CH$i" },
                    reading = Reading(
                        value = ch.text("value"),
                        unit = ch.text("unit"),
                        alarm = ch.isAlarm(),
                        alarmLabel = ch.alarmLabel(),
                    ),
                )
            },
            digitalInputs = digital.map { (i, ch) ->
                DigitalInput(
                    index = i,
                    name = ch.text("description").ifEmpty { "CH$i" },
                    state = ch.text("value").uppercase(),
                    alarm = ch.isAlarm(),
                    alarmLabel = ch.alarmLabel(),
                )
            },
            relays = root.child("R")?.numbered("R").orEmpty().map { (i, r) ->
                Relay(
                    index = i,
                    name = r.text("description").ifEmpty { "R$i" },
                    isOn = r.text("valuebin") == "1",
                    pulseWidth = r.text("pulseWidth").ifEmpty { null },
                    controlledBy = r.text("control").takeUnless { it.isEmpty() || it == "0" },
                )
            },
        )
    }

    private fun Element.isAlarm() = (text("alarmbin").toIntOrNull() ?: 0) >= FIRST_ALARM_LEVEL

    /** The level text when it is an actual alarm (Warning and above); Normal and "---" are not shown. */
    private fun Element.alarmLabel() = text("alarm").takeIf { isAlarm() && it.isNotEmpty() }
}
