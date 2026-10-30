package com.tcwcontrol.core.net

import com.tcwcontrol.core.model.DeviceFamily

enum class RelayAction { ON, OFF, PULSE }

/**
 * Builds the status.xml query that controls a relay. The two device families use different syntax:
 *  - modern (TCW241 manual, section 8.2.3): ron/rof/rpl=n where n is a bit mask (R1=1, R2=2, R3=4, R4=8);
 *  - classic CM: rN=1 / rN=0 / plN=1.
 */
object RelayCommand {
    fun query(family: DeviceFamily, relayIndex: Int, action: RelayAction): String {
        require(relayIndex >= 1) { "Relay index starts at 1" }
        return when (family) {
            DeviceFamily.MODERN -> {
                val mask = 1 shl (relayIndex - 1)
                when (action) {
                    RelayAction.ON -> "ron=$mask"
                    RelayAction.OFF -> "rof=$mask"
                    RelayAction.PULSE -> "rpl=$mask"
                }
            }
            DeviceFamily.CLASSIC -> when (action) {
                RelayAction.ON -> "r$relayIndex=1"
                RelayAction.OFF -> "r$relayIndex=0"
                RelayAction.PULSE -> "pl$relayIndex=1"
            }
        }
    }
}
