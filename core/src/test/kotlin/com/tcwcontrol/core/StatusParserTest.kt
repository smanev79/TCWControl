package com.tcwcontrol.core

import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.net.RelayAction
import com.tcwcontrol.core.net.RelayCommand
import com.tcwcontrol.core.parser.StatusParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusParserTest {
    private fun load(name: String) =
        StatusParser.parse(javaClass.getResourceAsStream("/$name")!!.readBytes())

    @Test
    fun tcw241() {
        val s = load("tcw241.xml")
        assertEquals(DeviceFamily.MODERN, s.family)
        assertEquals("TCW241", s.model)
        assertEquals(8, s.sensors.size)
        assertEquals("222", s.sensors[0].name)
        assertEquals("25.688", s.sensors[0].readings[0].value)
        assertEquals("°C", s.sensors[0].readings[0].unit)
        assertTrue(s.sensors[1].readings[0].alarm)
        assertEquals(listOf("Baterias", "ups", "REF", "CONSUMO"), s.analogInputs.map { it.name })
        assertTrue(s.analogInputs[0].reading.alarm)
        assertEquals(4, s.virtualInputs.size)
        assertEquals("ESTADO PUERTA", s.digitalInputs[1].name)
        assertEquals("FAULT", s.digitalInputs[1].state)
        assertTrue(s.digitalInputs[1].alarm)
        assertEquals(4, s.relays.size)
        assertFalse(s.relays[0].isOn)
        assertNull(s.relays[0].controlledBy)
        assertTrue(s.hasAlarm)
    }

    @Test
    fun tcw220WithBom() {
        val s = load("tcw220.xml")
        assertEquals("TCW220", s.model)
        assertFalse(s.sensors[6].isConnected)
        assertEquals(listOf("VolGerador", "SensorPorta"), s.analogInputs.map { it.name })
        assertEquals(2, s.relays.size)
        assertEquals("Virtual Input 2", s.relays[0].controlledBy)
        assertEquals("2.0", s.relays[0].pulseWidth)
    }

    @Test
    fun tcw210th() {
        val s = load("tcw210th.xml")
        assertEquals("TCW210TH", s.model)
        val s1 = s.sensors[0]
        assertEquals(listOf("°C", "%RH", "°C"), s1.readings.map { it.unit })
        assertEquals("64.813", s1.readings[1].value)
        assertTrue(s.relays.isEmpty())
        assertFalse(s.hasAlarm)
    }

    @Test
    fun tcw181bCm() {
        val s = load("tcw181b-cm.xml")
        assertEquals(DeviceFamily.CLASSIC, s.family)
        assertEquals("TCW181B-CM", s.model)
        assertEquals("TCW181B-CM-LD", s.hostName)
        assertEquals(1, s.digitalInputs.size)
        assertEquals("Digital In", s.digitalInputs[0].name)
        assertEquals("OPEN", s.digitalInputs[0].state)
        assertEquals(listOf("BLACK", "ELLADA", "GALAXY", "AC", "DOOR 1", "6", "7", "8"), s.relays.map { it.name })
        assertEquals("5.0", s.relays[1].pulseWidth)
    }

    @Test
    fun tcw122bCm() {
        val s = load("tcw122b-cm.xml")
        assertEquals("TCW122B-CM", s.model)
        assertEquals(listOf("Decon", "Fire"), s.digitalInputs.map { it.name })
        assertTrue(s.digitalInputs[0].alarm)
        assertEquals("12.1", s.analogInputs[0].reading.value)
        assertEquals("V", s.analogInputs[0].reading.unit)
        val t = s.sensors[0].readings
        assertEquals("26.2", t[0].value)
        assertEquals("°C", t[0].unit)
        assertTrue(t[0].alarm)
        assertEquals("%RH", t[1].unit)
        assertFalse(s.sensors[1].isConnected)
        assertTrue(s.relays.all { it.isOn })
        assertEquals("D1", s.relays[0].controlledBy)
        assertEquals("1", s.relays[1].pulseWidth)
    }

    @Test
    fun relayCommands() {
        assertEquals("ron=4", RelayCommand.query(DeviceFamily.MODERN, 3, RelayAction.ON))
        assertEquals("rof=8", RelayCommand.query(DeviceFamily.MODERN, 4, RelayAction.OFF))
        assertEquals("rpl=1", RelayCommand.query(DeviceFamily.MODERN, 1, RelayAction.PULSE))
        assertEquals("r2=1", RelayCommand.query(DeviceFamily.CLASSIC, 2, RelayAction.ON))
        assertEquals("pl5=1", RelayCommand.query(DeviceFamily.CLASSIC, 5, RelayAction.PULSE))
    }
}
