package com.tcwcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tcwcontrol.app.R

private val SplashBackground = Color.White
private val SplashText = Color(0xFF5B6776)

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
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), color = SplashText, style = MaterialTheme.typography.titleMedium)
    }
}
