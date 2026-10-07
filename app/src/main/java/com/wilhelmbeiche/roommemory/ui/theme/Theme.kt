package com.wilhelmbeiche.roommemory.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Brand colors taken from the launcher icon: teal background, amber storage box.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EFE8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBC),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF5FBF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF5FBF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E2),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inversePrimary = Color(0xFF84D5CB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84D5CB),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA0F2E7),
    secondary = Color(0xFFB0CCC7),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFFFFB86C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBC),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4947),
    inversePrimary = Color(0xFF0F766E)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * App theme. Uses the brand colors by default so the app matches its icon;
 * pass [dynamicColor] = true to follow the wallpaper colors on Android 12+ instead.
 */
@Composable
fun RoomMemoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, shapes = AppShapes, content = content)
}
