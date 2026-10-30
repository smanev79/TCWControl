package com.tcwcontrol.app

import android.app.Application
import com.tcwcontrol.app.data.DeviceRepository

class TcwApp : Application() {
    val repository: DeviceRepository by lazy { DeviceRepository(this) }
}
