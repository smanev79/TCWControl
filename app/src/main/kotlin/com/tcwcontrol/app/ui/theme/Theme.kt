package com.tcwcontrol.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Notification accent; notifications do not follow the app theme. */
val NotificationRed = Color(0xFFC62828)

/** Colours beyond the Material scheme, one set per theme. */
private data class StatusColors(
    val alarm: Color,
    val online: Color,
    val relayOn: Color,
    val pulse: Color,
    val offline: Color,
    val indicatorOff: Color,
    val relayButtonBg: Color,
    val relayButtonText: Color,
)

private val LightStatus = StatusColors(
    alarm = Color(0xFFC62828),
    online = Color(0xFF1E8E3E),
    relayOn = Color(0xFF1E8E3E),
    pulse = Color(0xFFE08600),
    offline = Color(0xFF5B6776),
    indicatorOff = Color(0xFFC3CCD8),
    relayButtonBg = Color(0xFFE8EEF6),
    relayButtonText = Color(0xFF0B5CAD),
)

private val DarkStatus = StatusColors(
    alarm = Color(0xFFFF6B6B),
    online = Color(0xFF4CC26A),
    relayOn = Color(0xFF4CC26A),
    pulse = Color(0xFFFFB13B),
    offline = Color(0xFF9AA7B6),
    indicatorOff = Color(0xFF3C4856),
    relayButtonBg = Color(0xFF223040),
    relayButtonText = Color(0xFF8CC4FF),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val AlarmRed: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.alarm
val OnlineGreen: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.online
val RelayOnGreen: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.relayOn
val PulseAmber: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.pulse
val OfflineGrey: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.offline
val IndicatorOff: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.indicatorOff
val RelayButtonBg: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.relayButtonBg
val RelayButtonText: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.relayButtonText

private val LightColors = lightColorScheme(
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
    error = LightStatus.alarm,
    onError = Color.White,
    outlineVariant = Color(0xFFE1E6EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CC4FF),
    onPrimary = Color(0xFF00315E),
    secondary = Color(0xFF3D8BDB),
    onSecondary = Color.White,
    background = Color(0xFF0F161E),
    surface = Color(0xFF17212C),
    surfaceVariant = Color(0xFF223040),
    onBackground = Color(0xFFE3E9F0),
    onSurface = Color(0xFFE3E9F0),
    onSurfaceVariant = Color(0xFF9AA7B6),
    error = DarkStatus.alarm,
    onError = Color(0xFF3B0000),
    outlineVariant = Color(0xFF2A3644),
)

@Composable
fun TcwTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
