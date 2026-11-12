package io.github.shadowfax505.kuetbuslive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Brand.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EA),
    onPrimaryContainer = Color(0xFF00382B),
    secondary = Brand.Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F5),
    onSecondaryContainer = Brand.Navy,
    tertiary = Color(0xFFB7860B),
    tertiaryContainer = Color(0xFFFFEDC2),
    onTertiaryContainer = Color(0xFF3D2C00),
    error = Brand.Coral,
    background = Brand.Mist,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5B6576),
    surfaceContainer = Color(0xFFF7F8FB),
    surfaceContainerHigh = Color(0xFFF0F2F7),
    surfaceContainerHighest = Color(0xFFE8EBF2),
    outline = Color(0xFFCBD2DE),
    outlineVariant = Color(0xFFE3E7EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3FD6A8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0F5C4B),
    onPrimaryContainer = Color(0xFFC8F5E6),
    secondary = Color(0xFFB9C8EA),
    onSecondary = Brand.Navy,
    secondaryContainer = Brand.NavySoft,
    onSecondaryContainer = Color(0xFFDCE4F5),
    tertiary = Brand.Amber,
    tertiaryContainer = Color(0xFF4A3A0B),
    onTertiaryContainer = Color(0xFFFFE5A3),
    error = Color(0xFFFF8A80),
    background = Brand.NavyDeep,
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF0D1830),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF16243F),
    onSurfaceVariant = Color(0xFF9DABC4),
    surfaceContainer = Color(0xFF101D37),
    surfaceContainerHigh = Color(0xFF15233F),
    surfaceContainerHighest = Color(0xFF1B2B4B),
    outline = Color(0xFF2E3F60),
    outlineVariant = Color(0xFF223250),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Big, tabular numbers for ETAs ("7 min").
val EtaNumberStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)

@Composable
fun KuetBusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
