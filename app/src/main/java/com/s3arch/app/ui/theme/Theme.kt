package com.s3arch.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette d'accent choisie dans les reglages quand les couleurs dynamiques sont
 * desactivees (ou sur un appareil anterieur a Android 12).
 */
data class AccentPalette(
    val id: String,
    val label: String,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
)

val AccentPalettes = listOf(
    AccentPalette(
        id = "azure",
        label = "Azur",
        lightPrimary = Color(0xFF00639B),
        lightSecondary = Color(0xFF5A4FB8),
        darkPrimary = Color(0xFF8FD3FF),
        darkSecondary = Color(0xFFCFC2FF),
    ),
    AccentPalette(
        id = "violet",
        label = "Violet",
        lightPrimary = Color(0xFF6B4BC4),
        lightSecondary = Color(0xFF8A4FA8),
        darkPrimary = Color(0xFFCFC2FF),
        darkSecondary = Color(0xFFE8C9FF),
    ),
    AccentPalette(
        id = "emeraude",
        label = "Émeraude",
        lightPrimary = Color(0xFF1F6F4A),
        lightSecondary = Color(0xFF2E7D5B),
        darkPrimary = Color(0xFF7DE2BC),
        darkSecondary = Color(0xFFA7E8CF),
    ),
    AccentPalette(
        id = "ambre",
        label = "Ambre",
        lightPrimary = Color(0xFF8A5300),
        lightSecondary = Color(0xFF7A5A00),
        darkPrimary = Color(0xFFFFC46B),
        darkSecondary = Color(0xFFF0D48A),
    ),
    AccentPalette(
        id = "framboise",
        label = "Framboise",
        lightPrimary = Color(0xFFA12D5E),
        lightSecondary = Color(0xFF7A4BB5),
        darkPrimary = Color(0xFFFFB0CC),
        darkSecondary = Color(0xFFE0C7FF),
    ),
    AccentPalette(
        id = "ardoise",
        label = "Ardoise",
        lightPrimary = Color(0xFF4A5568),
        lightSecondary = Color(0xFF5B6472),
        darkPrimary = Color(0xFFB8C4D6),
        darkSecondary = Color(0xFFCBD5E1),
    ),
)

fun accentPalette(id: String?): AccentPalette =
    AccentPalettes.firstOrNull { it.id == id } ?: AccentPalettes.first()

/**
 * Applique la palette choisie sur le schema de base en gardant les tons de fond.
 * Les conteneurs sont derives pour rester lisibles en clair comme en sombre.
 */
private fun ColorScheme.withAccent(palette: AccentPalette, dark: Boolean): ColorScheme {
    val accent = if (dark) palette.darkPrimary else palette.lightPrimary
    val second = if (dark) palette.darkSecondary else palette.lightSecondary
    val third = lerp(accent, second, 0.5f)
    fun on(color: Color): Color =
        if (color.luminance() > 0.55f) Color(0xFF08131C) else Color(0xFFFFFFFF)
    return copy(
        primary = accent,
        onPrimary = on(accent),
        primaryContainer = lerp(surface, accent, if (dark) 0.30f else 0.18f),
        onPrimaryContainer = if (dark) Color(0xFFEAF4FF) else Color(0xFF0A2233),
        secondary = second,
        onSecondary = on(second),
        secondaryContainer = lerp(surface, second, if (dark) 0.28f else 0.16f),
        onSecondaryContainer = if (dark) Color(0xFFF3ECFF) else Color(0xFF1C1233),
        tertiary = third,
        onTertiary = on(third),
        tertiaryContainer = lerp(surface, third, if (dark) 0.26f else 0.15f),
        onTertiaryContainer = if (dark) Color(0xFFF7EEFF) else Color(0xFF211427),
    )
}

// Palette de repli (utilisee avant Android 12 ou si la couleur dynamique est desactivee).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3FF),
    onPrimary = Color(0xFF00344E),
    primaryContainer = Color(0xFF0B4F6E),
    onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFFCFC2FF),
    onSecondary = Color(0xFF2A1D5E),
    secondaryContainer = Color(0xFF3F3273),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFFFFB2CB),
    onTertiary = Color(0xFF5A1136),
    tertiaryContainer = Color(0xFF7A2A50),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF0A0B10),
    onBackground = Color(0xFFE9E7F0),
    surface = Color(0xFF0D0F16),
    onSurface = Color(0xFFE9E7F0),
    surfaceVariant = Color(0xFF1C1F2B),
    onSurfaceVariant = Color(0xFFB8BAC9),
    surfaceContainerLowest = Color(0xFF08090D),
    surfaceContainerLow = Color(0xFF10121A),
    surfaceContainer = Color(0xFF141722),
    surfaceContainerHigh = Color(0xFF1A1E2B),
    surfaceContainerHighest = Color(0xFF202533),
    outline = Color(0xFF4A4F63),
    outlineVariant = Color(0xFF2A2F3F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D31),
    secondary = Color(0xFF5A4FB8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DEFF),
    onSecondaryContainer = Color(0xFF160066),
    tertiary = Color(0xFF9C3B63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF3D0021),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FB),
    surfaceContainer = Color(0xFFEFEDF6),
    surfaceContainerHigh = Color(0xFFE9E7F0),
    surfaceContainerHighest = Color(0xFFE3E1EA),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun S3ARCHTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: String = "azure",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val palette = accentPalette(accentColor)
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors.withAccent(palette, dark = true)
        else -> LightColors.withAccent(palette, dark = false)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
