package com.tcwcontrol.core.model

/** Placeholder the devices use for a missing value. */
const val NO_VALUE = "---"

/** One measured value with its unit, e.g. 23.4 °C. */
data class Reading(
    val value: String,
    val unit: String,
    val alarm: Boolean = false,
) {
    val isPresent: Boolean get() = value.isNotBlank() && value != NO_VALUE
}

/** A 1-Wire sensor slot (S1..S8) with up to three readings (temperature, humidity, dew point...). */
data class Sensor(
    val index: Int,
    val name: String,
    val readings: List<Reading>,
) {
    val isConnected: Boolean get() = readings.any { it.isPresent }
    val alarm: Boolean get() = readings.any { it.alarm }
}

/** Analog input (AIn) or virtual input (VIn). */
data class AnalogInput(
    val index: Int,
    val name: String,
    val reading: Reading,
)

data class DigitalInput(
    val index: Int,
    val name: String,
    /** State text as reported by the device: OPEN, CLOSED or a user-defined label. */
    val state: String,
    val alarm: Boolean,
)

data class Relay(
    val index: Int,
    val name: String,
    val isOn: Boolean,
    /** Pulse duration in seconds as text, if the device reports it. */
    val pulseWidth: String? = null,
    /** Source that controls the relay automatically (e.g. an input), or null when it is manual. */
    val controlledBy: String? = null,
)

/** Which HTTP command syntax the device understands for relay control. */
enum class DeviceFamily {
    /** TCW241, TCW220, TCW210TH... (firmware 2.x, <DeviceInfo> in status.xml). */
    MODERN,
    /** TCW181B-CM, TCW122B-CM... (flat status.xml with <Device>). */
    CLASSIC,
}

/** Model-independent snapshot of a device's status.xml. */
data class DeviceStatus(
    val family: DeviceFamily,
    val model: String,
    val hostName: String = "",
    val firmware: String = "",
    val sensors: List<Sensor> = emptyList(),
    val analogInputs: List<AnalogInput> = emptyList(),
    val virtualInputs: List<AnalogInput> = emptyList(),
    val digitalInputs: List<DigitalInput> = emptyList(),
    val relays: List<Relay> = emptyList(),
) {
    val hasAlarm: Boolean
        get() = sensors.any { it.alarm } ||
            analogInputs.any { it.reading.alarm } ||
            virtualInputs.any { it.reading.alarm } ||
            digitalInputs.any { it.alarm }
}
