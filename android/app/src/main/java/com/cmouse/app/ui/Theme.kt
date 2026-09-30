package com.cmouse.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

private val CMouseTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = bodySmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

private val CMouseShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun CMouseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CMouseTypography,
        shapes = CMouseShapes,
        content = content
    )
}
