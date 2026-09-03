package com.qqt.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeSoft,
    onPrimaryContainer = BrandOrangeDeep,
    secondary = BrandOrangeDeep,
    onSecondary = Color.White,
    background = WarmBackground,
    onBackground = InkPrimary,
    surface = WarmSurface,
    onSurface = InkPrimary,
    surfaceVariant = PlaceholderBg,
    onSurfaceVariant = InkSecondary,
    outline = Hairline,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangeTint,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeDeep,
    onPrimaryContainer = Color.White,
    secondary = BrandOrange,
    onSecondary = Color.White,
)

@Composable
fun QQTMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
