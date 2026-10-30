package com.tcwcontrol.core.parser

import com.tcwcontrol.core.model.AnalogInput
import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.model.DigitalInput
import com.tcwcontrol.core.model.NO_VALUE
import com.tcwcontrol.core.model.Reading
import com.tcwcontrol.core.model.Relay
import com.tcwcontrol.core.model.Sensor
import org.w3c.dom.Element

/**
 * Flat CM layout, e.g. <Relay1Description>, <Relay1>, <pw1>, <DigitalInput1>, <Temperature1>.
 * Devices with a single input omit the number (<DigitalInput>, <DinAlarm>).
 */
internal object ClassicParser {
    private val valueWithUnit = Regex("""^(-?\d+(?:\.\d+)?)\s*(.*)$""")

    fun parse(root: Element): DeviceStatus {
        val tags = root.children().associate { it.tagName to it.textContent.trim() }
        fun tag(name: String) = tags[name].orEmpty()
        fun indices(regex: Regex) = tags.keys.mapNotNull { regex.matchEntire(it)?.groupValues?.get(1) }
            .map { it.toIntOrNull() ?: 0 }.distinct().sorted()
        fun suffix(i: Int) = if (i == 0) "" else i.toString()

        val digitalInputs = indices(Regex("""DigitalInput(\d*)""")).map { i ->
            val n = suffix(i)
            DigitalInput(
                index = maxOf(i, 1),
                name = tag("DigitalInput${n}Description").ifEmpty { "Digital input ${maxOf(i, 1)}" },
                state = tag("DigitalInput$n"),
                alarm = tag("DinAlarm$n") == "1",
            )
        }

        val analogInputs = indices(Regex("""AnalogInput(\d+)""")).map { i ->
            AnalogInput(
                index = i,
                name = tag("AnalogInput${i}Description").ifEmpty { "Analog input $i" },
                reading = split(tag("AnalogInput$i"), tag("AinAlarm$i") == "1"),
            )
        }

        val sensors = indices(Regex("""Sensor(\d+)Description""")).map { i ->
            val readings = buildList {
                if ("Temperature$i" in tags) add(split(tag("Temperature$i"), tag("TempAlarm$i") == "1"))
                if ("Humidity$i" in tags) add(split(tag("Humidity$i"), tag("HumAlarm$i") == "1"))
            }
            Sensor(index = i, name = tag("Sensor${i}Description").ifEmpty { "S$i" }, readings = readings)
        }

        val commonPulse = tag("pulseWidth").ifEmpty { null }
        val relays = indices(Regex("""Relay(\d+)""")).map { i ->
            Relay(
                index = i,
                name = tag("Relay${i}Description").ifEmpty { "Relay $i" },
                isOn = tag("Relay$i").equals("ON", ignoreCase = true),
                pulseWidth = tag("pw$i").ifEmpty { commonPulse },
                controlledBy = tag("Relay${i}Control").takeUnless { it.isEmpty() || it == "0" },
            )
        }

        return DeviceStatus(
            family = DeviceFamily.CLASSIC,
            model = tag("Device"),
            hostName = tag("Hostname"),
            firmware = tag("FW"),
            sensors = sensors,
            analogInputs = analogInputs,
            digitalInputs = digitalInputs,
            relays = relays,
        )
    }

    /** "26.2°C" -> ("26.2", "°C"); "---" stays as is. */
    private fun split(raw: String, alarm: Boolean): Reading {
        if (raw.isEmpty() || raw == NO_VALUE) return Reading(NO_VALUE, "", alarm)
        val m = valueWithUnit.matchEntire(raw) ?: return Reading(raw, "", alarm)
        return Reading(m.groupValues[1], m.groupValues[2].trim(), alarm)
    }
}
