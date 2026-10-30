package com.tcwcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tcwcontrol.app.R

private val SplashBackground = Color.White

/** Teracom logo shown briefly on launch; always on white, as the logo is made for a light background. */
@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().background(SplashBackground),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.teracom_logo),
            contentDescription = "Teracom",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.75f).widthIn(max = 360.dp),
        )
    }
}
