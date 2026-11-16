package io.github.shadowfax505.kuetbuslive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shadowfax505.kuetbuslive.R

// Colours follow the iOS app and iOS's own grouped lists: a light grey (or black) page,
// white (or dark grey) rows on top of it, grey secondary text and hairline separators.
private val LightColors = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5EA),
    onPrimaryContainer = Brand.Ink,
    secondary = Color(0xFF3C3C43),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Brand.Ink,
    tertiary = Brand.Seats,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Brand.Ink,
    error = Brand.Danger,
    onError = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F7),
    surfaceContainerHighest = Color(0xFFE9E9EB),
    surfaceBright = Color.White,
    inverseSurface = Brand.Ink,
    inverseOnSurface = Color.White,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFD8D8DC),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF3A3A3C),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFEBEBF5),
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF3A3A3C),
    onSecondaryContainer = Color.White,
    tertiary = Brand.Seats,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3A3A3C),
    onTertiaryContainer = Color.White,
    error = Color(0xFFFF6369),
    onError = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF161618),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceBright = Color(0xFF2C2C2E),
    inverseSurface = Color.White,
    inverseOnSurface = Brand.Ink,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    scrim = Color.Black,
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// Inter's display cut, tighter for the big titles, the way SF Pro Display is on iOS.
val InterDisplay = FontFamily(Font(R.font.inter_display_bold, FontWeight.Bold))

private fun style(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp, family: FontFamily = Inter) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking)

// The iOS text styles: Large Title 34, Title 1 28, Title 2 22, Title 3 20, Headline 17,
// Body 17, Callout 16, Subheadline 15, Footnote 13, Caption 12 and 11.
private val AppTypography = Typography(
    displayLarge = style(48, 54, FontWeight.Bold, (-1).sp, InterDisplay),
    displayMedium = style(40, 46, FontWeight.Bold, (-0.8).sp, InterDisplay),
    displaySmall = style(34, 41, FontWeight.Bold, (-0.6).sp, InterDisplay),
    headlineLarge = style(34, 41, FontWeight.Bold, (-0.6).sp, InterDisplay),
    headlineMedium = style(28, 34, FontWeight.Bold, (-0.5).sp, InterDisplay),
    headlineSmall = style(22, 28, FontWeight.Bold, (-0.3).sp),
    titleLarge = style(20, 25, FontWeight.SemiBold, (-0.3).sp),
    titleMedium = style(17, 22, FontWeight.SemiBold, (-0.2).sp),
    titleSmall = style(15, 20, FontWeight.SemiBold, (-0.1).sp),
    bodyLarge = style(17, 22, FontWeight.Normal, (-0.2).sp),
    bodyMedium = style(15, 20, FontWeight.Normal, (-0.1).sp),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold, (-0.1).sp),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 13, FontWeight.Medium, 0.1.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Big numbers for ETAs ("7 min").
val EtaNumberStyle = TextStyle(fontFamily = InterDisplay, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)

@Composable
fun KuetBusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
        // Text that isn't inside a Surface still follows the theme, so nothing turns
        // black-on-black in dark mode.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
