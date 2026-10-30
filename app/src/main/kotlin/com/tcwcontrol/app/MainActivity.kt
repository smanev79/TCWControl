package com.tcwcontrol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tcwcontrol.app.monitor.AlarmNotifier
import com.tcwcontrol.app.monitor.MonitorService
import com.tcwcontrol.app.ui.alarms.AlarmLogScreen
import com.tcwcontrol.app.ui.detail.DeviceDetailScreen
import com.tcwcontrol.app.ui.devices.DeviceListScreen
import com.tcwcontrol.app.ui.edit.EditDeviceScreen
import com.tcwcontrol.app.ui.settings.SettingsScreen
import com.tcwcontrol.app.ui.theme.TcwTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val DEVICES = "devices"
    const val DETAIL = "device/{id}"
    const val EDIT = "edit?id={id}"
    const val ALARMS = "alarms"
    const val SETTINGS = "settings"

    fun detail(id: String) = "device/$id"
    fun edit(id: String? = null) = if (id == null) "edit" else "edit?id=$id"
}

class MainActivity : ComponentActivity() {
    /** Device to open, set when the app is launched from an alarm notification. */
    private var openDeviceId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        AlarmNotifier(this).createChannels()
        lifecycleScope.launch {
            if ((application as TcwApp).repository.monitoringEnabled.first()) MonitorService.start(this@MainActivity)
        }
        setContent {
            TcwTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = Routes.DEVICES) {
                    composable(Routes.DEVICES) {
                        DeviceListScreen(
                            onOpen = { nav.navigate(Routes.detail(it)) },
                            onEdit = { nav.navigate(Routes.edit(it)) },
                            onAdd = { nav.navigate(Routes.edit()) },
                            onAlarmLog = { nav.navigate(Routes.ALARMS) },
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Routes.ALARMS) {
                        AlarmLogScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        DeviceDetailScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Routes.edit(it)) },
                        )
                    }
                    composable(
                        Routes.EDIT,
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) {
                        EditDeviceScreen(
                            onDone = { nav.popBackStack() },
                            onDeleted = { nav.popBackStack(Routes.DEVICES, inclusive = false) },
                        )
                    }
                }
                // After NavHost, so the navigation graph is set before navigating.
                LaunchedEffect(openDeviceId) {
                    openDeviceId?.let {
                        nav.popBackStack(Routes.DEVICES, inclusive = false)
                        nav.navigate(Routes.detail(it))
                        openDeviceId = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_DEVICE_ID)?.let { openDeviceId = it }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
    }
}
