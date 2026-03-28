package com.remodex.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class AppToneMode {
    SYSTEM,
    FORCE_LIGHT,
    FORCE_DARK
}

private val LightColors = lightColorScheme(
    primary = PlanAccent,
    secondary = CommandAccent,
    tertiary = InkMuted,
    background = Mist,
    surface = Cloud,
    surfaceVariant = Mist,
    outline = WarmLine,
    error = AlertRed,
    onPrimary = Cloud,
    onSecondary = Cloud,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted
)

private val DarkColors = darkColorScheme(
    primary = PlanAccentDark,
    secondary = CommandAccentDark,
    tertiary = NightMuted,
    background = Night,
    surface = NightCard,
    surfaceVariant = Night,
    outline = WarmLine,
    error = AlertRed,
    onPrimary = Night,
    onSecondary = Night,
    onBackground = Cloud,
    onSurface = Cloud,
    onSurfaceVariant = NightMuted
)

@Composable
fun RemodexTheme(
    fontStyle: AppFontStyle = AppFontStyle.SYSTEM,
    toneMode: AppToneMode = AppToneMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (toneMode) {
        AppToneMode.SYSTEM -> isSystemInDarkTheme()
        AppToneMode.FORCE_LIGHT -> false
        AppToneMode.FORCE_DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = remodexTypography(fontStyle),
        content = content
    )
}
