package com.tcwcontrol.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AlarmRed = Color(0xFFE57373)
val OnlineGreen = Color(0xFF7CC576)
val RelayOnGreen = Color(0xFF76E05A)
val ButtonGrey = Color(0xFF9E9E9E)

private val Colors = darkColorScheme(
    primary = Color(0xFF8FA3C2),
    onPrimary = Color(0xFF0E1622),
    secondary = Color(0xFF6B7B95),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF262626),
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = AlarmRed,
    outlineVariant = Color(0xFF3A3A3A),
)

@Composable
fun TcwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
