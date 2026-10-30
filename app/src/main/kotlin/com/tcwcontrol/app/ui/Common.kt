package com.tcwcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tcwcontrol.app.TcwApp
import com.tcwcontrol.app.data.DeviceRepository

/** Creates a ViewModel that needs the repository (and optionally the navigation arguments). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: CreationExtras.(DeviceRepository) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as TcwApp
    return viewModel<VM>(factory = viewModelFactory { initializer { create(app.repository) } })
}

/** Runs [block] while the screen is visible and cancels it when the screen goes to the background. */
@Composable
fun WhileVisible(block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
