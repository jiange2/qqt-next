package com.cdndash.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 靛蓝为品牌色，与音乐客户端的暖橙区分；只做跟随系统的深浅两套（spec「Out of Scope」）。
// 深色全套手工调校（含 surfaceContainer 层次），不落回 M3 默认紫灰。
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F54EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF0B1E5B),
    secondary = Color(0xFF4A5E8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2F9),
    onSecondaryContainer = Color(0xFF152238),
    tertiary = Color(0xFF0B7285),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC3E8EE),
    onTertiaryContainer = Color(0xFF06272E),
    error = Color(0xFFDC362E),
    onError = Color.White,
    errorContainer = Color(0xFFFFEDEA),
    onErrorContainer = Color(0xFF5F1410),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF171A21),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFE4E7F0),
    onSurfaceVariant = Color(0xFF5A6070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F1F7),
    surfaceContainerHigh = Color(0xFFE9EBF2),
    surfaceContainerHighest = Color(0xFFE1E4EC),
    outline = Color(0xFFC6CBD8),
    outlineVariant = Color(0xFFE1E4EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF0B2A7A),
    primaryContainer = Color(0xFF24409F),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFB9C7E4),
    onSecondary = Color(0xFF233148),
    secondaryContainer = Color(0xFF394760),
    onSecondaryContainer = Color(0xFFD9E2F9),
    tertiary = Color(0xFF80D5DE),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004F58),
    onTertiaryContainer = Color(0xFFC3E8EE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE5E8F0),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE5E8F0),
    surfaceVariant = Color(0xFF232936),
    onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF141821),
    surfaceContainer = Color(0xFF181D28),
    surfaceContainerHigh = Color(0xFF1E2431),
    surfaceContainerHighest = Color(0xFF262D3B),
    outline = Color(0xFF3A4354),
    outlineVariant = Color(0xFF262D3B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** M3 角色之外的语义色：正常状态点、余量偏低的警示、Hero 卡渐变端色。 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightExtended = ExtendedColors(
    success = Color(0xFF1B8A4B),
    warning = Color(0xFFB25E09),
    heroStart = Color(0xFF2F54EB),
    heroEnd = Color(0xFF6C8CFF),
)

private val DarkExtended = ExtendedColors(
    success = Color(0xFF6FDC97),
    warning = Color(0xFFF2B35C),
    heroStart = Color(0xFF16265C),
    heroEnd = Color(0xFF2F54EB),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

@Composable
fun CdnDashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}
