package com.tcwcontrol.core.model

/** One parameter that is currently in alarm, e.g. key "S2#0", text "S2: 26.000 °C". */
data class ActiveAlarm(val key: String, val text: String)

/** Lists every parameter of [status] that is in alarm, with a stable key for change detection. */
fun DeviceStatus.activeAlarms(): List<ActiveAlarm> = buildList {
    fun Reading.text() = listOfNotNull("$value $unit".trim(), alarmLabel?.let { "($it)" }).joinToString(" ")

    sensors.forEach { s ->
        s.readings.forEachIndexed { i, r -> if (r.alarm) add(ActiveAlarm("S${s.index}#$i", "${s.name}: ${r.text()}")) }
    }
    analogInputs.forEach { a -> if (a.reading.alarm) add(ActiveAlarm("AI${a.index}", "${a.name}: ${a.reading.text()}")) }
    virtualInputs.forEach { v -> if (v.reading.alarm) add(ActiveAlarm("VI${v.index}", "${v.name}: ${v.reading.text()}")) }
    digitalInputs.forEach { d ->
        if (d.alarm) {
            val label = d.alarmLabel?.let { " ($it)" }.orEmpty()
            add(ActiveAlarm("DI${d.index}", "${d.name}: ${d.state}$label"))
        }
    }
}
