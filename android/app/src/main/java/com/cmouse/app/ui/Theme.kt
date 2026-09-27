package com.cmouse.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// macOS 设计语言配色：iOS 系统灰 + Apple 蓝
val BgLight = Color(0xFFF5F5F7)
val BgDark = Color(0xFF1E1E20)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF2C2C2E)
val Accent = Color(0xFF007AFF)
val TextPrimary = Color(0xFF1D1D1F)
val TextSecondary = Color(0xFF6E6E73)
val TextPrimaryDark = Color(0xFFF5F5F7)
val TextSecondaryDark = Color(0xFF98989D)
val Separator = Color(0xFFE5E5EA)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = BgLight,
    onBackground = TextPrimary,
    surface = CardLight,
    onSurface = TextPrimary,
    surfaceVariant = Separator,
    onSurfaceVariant = TextSecondary,
    outline = Separator
)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF48484A)
)

@Composable
fun CMouseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
