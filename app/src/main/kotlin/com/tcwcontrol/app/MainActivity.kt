package com.tcwcontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tcwcontrol.app.ui.detail.DeviceDetailScreen
import com.tcwcontrol.app.ui.devices.DeviceListScreen
import com.tcwcontrol.app.ui.edit.EditDeviceScreen
import com.tcwcontrol.app.ui.theme.TcwTheme

object Routes {
    const val DEVICES = "devices"
    const val DETAIL = "device/{id}"
    const val EDIT = "edit?id={id}"

    fun detail(id: String) = "device/$id"
    fun edit(id: String? = null) = if (id == null) "edit" else "edit?id=$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TcwTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = Routes.DEVICES) {
                    composable(Routes.DEVICES) {
                        DeviceListScreen(
                            onOpen = { nav.navigate(Routes.detail(it)) },
                            onEdit = { nav.navigate(Routes.edit(it)) },
                            onAdd = { nav.navigate(Routes.edit()) },
                        )
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
            }
        }
    }
}
