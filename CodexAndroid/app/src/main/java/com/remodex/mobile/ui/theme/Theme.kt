package com.remodex.mobile.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = (if (darkTheme) Night else Mist).toArgb()
            window.setBackgroundDrawable(ColorDrawable((if (darkTheme) Night else Mist).toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = remodexTypography(fontStyle),
        content = content
    )
}
