package com.tcwcontrol.core

import com.tcwcontrol.core.legacy.LegacyDevice
import com.tcwcontrol.core.legacy.LegacyDevices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyDevicesTest {
    // As written by .NET XmlSerializer for List<Device> in the MAUI app (3.0.x).
    private val mauiXml = """
        <?xml version="1.0" encoding="utf-16"?>
        <ArrayOfDevice xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <Device>
            <Id>3f2a9c0e5b7d4e1f8a6b2c3d4e5f6a7b</Id>
            <UserConfiguration>
              <MonitorType>MonitorTCW241</MonitorType>
              <IOSIdentifiers><string>s1</string></IOSIdentifiers>
              <IOSHidden><boolean>false</boolean></IOSHidden>
              <LocalDescriptions><string>Boiler</string></LocalDescriptions>
            </UserConfiguration>
            <IsDemoUnit>true</IsDemoUnit>
            <Address>212.25.45.120</Address>
            <Port>60241</Port>
            <Description>TCW241, Teracom, Ruse</Description>
            <Username>admin</Username>
          </Device>
          <Device>
            <Id>b1</Id>
            <IsDemoUnit>false</IsDemoUnit>
            <Address>office.example.com</Address>
            <Port>8080</Port>
            <Description>Офис &amp; склад</Description>
            <Username>user</Username>
            <Password>old-secret</Password>
          </Device>
          <Device>
            <Id>c1</Id>
            <IsDemoUnit>false</IsDemoUnit>
            <Address />
            <Port>80</Port>
          </Device>
          <Device>
            <IsDemoUnit>false</IsDemoUnit>
            <Address>10.0.0.5</Address>
            <Port>0</Port>
          </Device>
        </ArrayOfDevice>
    """.trimIndent()

    @Test
    fun readsMauiDeviceList() {
        val devices = LegacyDevices.parseMauiXml(mauiXml)
        assertEquals(3, devices.size)
        assertEquals(
            LegacyDevice("3f2a9c0e5b7d4e1f8a6b2c3d4e5f6a7b", "212.25.45.120", 60241, "TCW241, Teracom, Ruse", "admin", "", true),
            devices[0],
        )
        assertEquals(LegacyDevice("b1", "office.example.com", 8080, "Офис & склад", "user", "old-secret", false), devices[1])
        assertEquals(LegacyDevice("", "10.0.0.5", 80, "", "", "", false), devices[2])
    }

    @Test
    fun ignoresAnythingElse() {
        assertTrue(LegacyDevices.parseMauiXml("").isEmpty())
        assertTrue(LegacyDevices.parseMauiXml("not xml").isEmpty())
        assertTrue(LegacyDevices.parseMauiXml("<ArrayOfString><string>x</string></ArrayOfString>").isEmpty())
        assertTrue(LegacyDevices.parseMauiXml("<?xml version=\"1.0\" encoding=\"utf-16\"?><ArrayOfDevice />").isEmpty())
    }

    @Test
    fun mapsRefreshSetting() {
        val options = listOf(2, 5, 10, 30, 60)
        assertNull(LegacyDevices.refreshSeconds(0, options))
        assertNull(LegacyDevices.refreshSeconds(42, options))
        assertEquals(5, LegacyDevices.refreshSeconds(1, options))
        assertEquals(10, LegacyDevices.refreshSeconds(3, options)) // 15 s
        assertEquals(30, LegacyDevices.refreshSeconds(4, options)) // 20 s
        assertEquals(60, LegacyDevices.refreshSeconds(10, options)) // 300 s
    }
}
