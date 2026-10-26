package com.s3arch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cyan = Color(0xFF6FE3FF)
val Violet = Color(0xFFB9A7FF)
val Pink = Color(0xFFFF9EB8)
val DeepSpace = Color(0xFF06080F)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00323D),
    primaryContainer = Color(0xFF00505F),
    onPrimaryContainer = Color(0xFFBDF2FF),
    secondary = Violet,
    onSecondary = Color(0xFF221A54),
    secondaryContainer = Color(0xFF342C68),
    onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Pink,
    onTertiary = Color(0xFF5A1230),
    tertiaryContainer = Color(0xFF7C2547),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = DeepSpace,
    onBackground = Color(0xFFE7E9F3),
    surface = Color(0xFF0A0E18),
    onSurface = Color(0xFFE7E9F3),
    surfaceVariant = Color(0xFF1A2032),
    onSurfaceVariant = Color(0xFFB2B8CC),
    surfaceContainerLowest = Color(0xFF05070D),
    surfaceContainerLow = Color(0xFF0C111C),
    surfaceContainer = Color(0xFF101625),
    surfaceContainerHigh = Color(0xFF151C2D),
    surfaceContainerHighest = Color(0xFF1A2235),
    outline = Color(0xFF3B4560),
    outlineVariant = Color(0xFF232C42),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEEBFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF5B4FBE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DFFF),
    onSecondaryContainer = Color(0xFF160066),
    tertiary = Color(0xFFA03A5C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E0018),
    background = Color(0xFFF7F8FD),
    onBackground = Color(0xFF10131C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10131C),
    surfaceVariant = Color(0xFFE4E7F2),
    onSurfaceVariant = Color(0xFF454B5E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4FC),
    surfaceContainer = Color(0xFFEDF0F9),
    surfaceContainerHigh = Color(0xFFE7EAF5),
    surfaceContainerHighest = Color(0xFFE1E5F1),
    outline = Color(0xFF757B8F),
    outlineVariant = Color(0xFFC5C9D8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Degrade de marque utilise dans les en-tetes. */
val BrandGradient: Brush
    @Composable get() = if (isSystemInDarkTheme()) {
        Brush.linearGradient(listOf(Color(0xFF0E3B52), Color(0xFF2A1F5E), Color(0xFF0A0E18)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFB9F0FF), Color(0xFFDCD6FF), Color(0xFFF7F8FD)))
    }

@Composable
fun S3ARCHTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
