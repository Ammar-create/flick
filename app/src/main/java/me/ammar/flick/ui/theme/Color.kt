package me.ammar.flick.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// MetroList Plum & AMOLED Dark Palette
val PlumBackground = Color(0xFF140E14)
val PlumSurface = Color(0xFF161214)
val PlumSurfaceContainer = Color(0xFF1F1820)
val PlumSurfaceHigh = Color(0xFF2B202C)
val PlumSurfaceHighest = Color(0xFF382C36)

val PlumPrimary = Color(0xFFD8B4D8)
val PlumOnPrimary = Color(0xFF3A2338)
val PlumPrimaryContainer = Color(0xFF4A3448)
val PlumOnPrimaryContainer = Color(0xFFF3DEF3)

val PlumSecondary = Color(0xFFC7B6C8)
val PlumOnSecondary = Color(0xFF352936)
val PlumSecondaryContainer = Color(0xFF4C3E4D)
val PlumOnSecondaryContainer = Color(0xFFE5D7E6)

val PlumTertiary = Color(0xFFDFBFB2)
val PlumOnTertiary = Color(0xFF432C23)
val PlumTertiaryContainer = Color(0xFF5B4238)
val PlumOnTertiaryContainer = Color(0xFFFDDCD0)

val PlumTextPrimary = Color(0xFFF5EEF5)
val PlumTextMuted = Color(0xFFA898A6)
val PlumOutline = Color(0xFF4E3E4C)
val PlumOutlineVariant = Color(0xFF382B37)

val StatusGreen = Color(0xFF81C784)
val StatusGreenContainer = Color(0xFF1E3A20)
val StatusOrange = Color(0xFFFFB74D)
val StatusOrangeContainer = Color(0xFF3E2914)
val StatusRed = Color(0xFFE57373)
val StatusRedContainer = Color(0xFF3E1818)

val FlickDarkColorScheme = darkColorScheme(
    primary = PlumPrimary,
    onPrimary = PlumOnPrimary,
    primaryContainer = PlumPrimaryContainer,
    onPrimaryContainer = PlumOnPrimaryContainer,
    secondary = PlumSecondary,
    onSecondary = PlumOnSecondary,
    secondaryContainer = PlumSecondaryContainer,
    onSecondaryContainer = PlumOnSecondaryContainer,
    tertiary = PlumTertiary,
    onTertiary = PlumOnTertiary,
    tertiaryContainer = PlumTertiaryContainer,
    onTertiaryContainer = PlumOnTertiaryContainer,
    background = PlumBackground,
    onBackground = PlumTextPrimary,
    surface = PlumSurface,
    onSurface = PlumTextPrimary,
    surfaceVariant = PlumSurfaceHigh,
    onSurfaceVariant = PlumTextMuted,
    surfaceContainerLowest = PlumBackground,
    surfaceContainerLow = PlumSurface,
    surfaceContainer = PlumSurfaceContainer,
    surfaceContainerHigh = PlumSurfaceHigh,
    surfaceContainerHighest = PlumSurfaceHighest,
    outline = PlumOutline,
    outlineVariant = PlumOutlineVariant,
)
