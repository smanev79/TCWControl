package com.tcwcontrol.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AlarmRed = Color(0xFFC62828)
val OnlineGreen = Color(0xFF1E8E3E)
val RelayOnGreen = Color(0xFF1E8E3E)
val IndicatorOff = Color(0xFFC3CCD8)
val RelayButtonBg = Color(0xFFE8EEF6)
val RelayButtonText = Color(0xFF0B5CAD)

private val Colors = lightColorScheme(
    primary = Color(0xFF0B5CAD),
    onPrimary = Color.White,
    secondary = Color(0xFF0B5CAD),
    onSecondary = Color.White,
    background = Color(0xFFF3F5F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF6),
    onBackground = Color(0xFF16202C),
    onSurface = Color(0xFF16202C),
    onSurfaceVariant = Color(0xFF5B6776),
    error = AlarmRed,
    onError = Color.White,
    outlineVariant = Color(0xFFE1E6EC),
)

@Composable
fun TcwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
