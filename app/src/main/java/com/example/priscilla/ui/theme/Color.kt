package com.example.priscilla.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- 1. Imperial Crimson (Inspired by her gown) ---
val CrimsonRed = Color(0xFFC70039)
val CrimsonGold = Color(0xFFDAA520)
val CrimsonBlack = Color(0xFF1C1C1E)
val CrimsonParchment = Color(0xFFFAF0E6)

val ImperialCrimsonLightColorScheme = lightColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    secondary = CrimsonGold,
    onSecondary = Color.Black,
    background = CrimsonParchment,
    onBackground = CrimsonBlack,
    surface = Color(0xFFFDF7F0),
    onSurface = CrimsonBlack
)

val ImperialCrimsonDarkColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    secondary = CrimsonGold,
    onSecondary = Color.Black,
    background = CrimsonBlack,
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF28282A),
    onSurface = Color(0xFFEAEAEA)
)

// --- 2. Golden Fan (Sun Princess) ---
val SunGold = Color(0xFFFFD700)
val FanCoral = Color(0xFFFF7F50)
val FanCream = Color(0xFFFFFDD0)
val FanBrown = Color(0xFF3D2B1F)

val GoldenFanLightColorScheme = lightColorScheme(
    primary = SunGold,
    onPrimary = Color.Black,
    secondary = FanCoral,
    onSecondary = Color.White,
    background = FanCream,
    onBackground = FanBrown,
    surface = Color(0xFFFFFFFF),
    onSurface = FanBrown
)

val GoldenFanDarkColorScheme = darkColorScheme(
    primary = SunGold,
    onPrimary = Color.Black,
    secondary = FanCoral,
    onSecondary = Color.White,
    background = FanBrown,
    onBackground = FanCream,
    surface = Color(0xFF4E382A),
    onSurface = FanCream
)

// --- 3. Yang Sword Dominion (Fiery) ---
val SolarOrange = Color(0xFFFF4500)
val FieryRed = Color(0xFFDC143C)
val CharcoalSlate = Color(0xFF2F4F4F)
val BrightContrast = Color(0xFFF0F0F0) // A bright, off-white for high contrast

// For this theme, we want the light and dark versions to be very similar
// to maintain the "fiery" aesthetic against a dark background.

val YangSwordLightColorScheme = lightColorScheme(
    primary = SolarOrange,
    onPrimary = Color.White,
    secondary = FieryRed,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = BrightContrast,

    // Use a dark slate for surfaces like the navigation bar
    surface = CharcoalSlate,
    onSurface = BrightContrast, // High contrast text/icons on the surface
    surfaceVariant = CharcoalSlate, // Navigation bar uses this
    onSurfaceVariant = Color(0xFFB0B0B0) // A dimmer, but still readable gray for unselected icons
)

val YangSwordDarkColorScheme = darkColorScheme(
    primary = SolarOrange,
    onPrimary = Color.White,
    secondary = FieryRed,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = BrightContrast,

    surface = CharcoalSlate,
    onSurface = BrightContrast,
    surfaceVariant = CharcoalSlate,
    onSurfaceVariant = Color(0xFFB0B0B0)
)

// --- 4. Yin Eclipse (Moonlit) ---
val MoonlightSilver = Color(0xFFC0C0C0)
val RoyalPurple = Color(0xFF4B0082)
val MidnightBlue = Color(0xFF000033)

val YinEclipseLightColorScheme = lightColorScheme(
    primary = MoonlightSilver,
    onPrimary = Color.Black,
    secondary = RoyalPurple,
    onSecondary = Color.White,
    background = Color(0xFFE8E8F8),
    onBackground = MidnightBlue,
    surface = Color.White,
    onSurface = MidnightBlue
)

val YinEclipseDarkColorScheme = darkColorScheme(
    primary = MoonlightSilver,
    onPrimary = Color.Black,
    secondary = Color(0xFFD8BFD8), // Lighter purple for dark theme
    onSecondary = Color.Black,
    background = MidnightBlue,
    onBackground = Color(0xFFE8E8F8),
    surface = Color(0xFF00004D),
    onSurface = Color(0xFFE8E8F8)
)

// --- 5. Azure Dream (Clear Blue/White Theme) ---
val AzureBlue = Color(0xFF007BFF)
val SkyBlue = Color(0xFF87CEEB)
val LightCoolGray = Color(0xFFF0F4F8)
val DarkCoolGray = Color(0xFF1A1C1E)

val AzureDreamLightColorScheme = lightColorScheme(
    primary = AzureBlue,
    onPrimary = Color.White,
    secondary = SkyBlue,
    onSecondary = Color.Black,
    background = Color.White,
    onBackground = DarkCoolGray,
    surface = LightCoolGray,
    onSurface = DarkCoolGray
)

val AzureDreamDarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color.Black,
    secondary = AzureBlue,
    onSecondary = Color.White,
    background = DarkCoolGray,
    onBackground = LightCoolGray,
    surface = Color(0xFF2F3032),
    onSurface = LightCoolGray
)

// --- 6. Crimson Twilight (Dramatic Dark Theme) ---
val TwilightSilver = Color(0xFFE0E0E0)
val BloodRed = Color(0xFF8B0000)
val DeepCharcoal = Color(0xFF121212)

val CrimsonTwilightLightColorScheme = lightColorScheme(
    primary = BloodRed,
    onPrimary = Color.White,
    secondary = Color(0xFFB00020), // A slightly brighter red for secondary
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black
)

val CrimsonTwilightDarkColorScheme = darkColorScheme(
    primary = TwilightSilver,
    onPrimary = Color.Black,
    secondary = BloodRed,
    onSecondary = Color.White,
    background = DeepCharcoal,
    onBackground = TwilightSilver,
    surface = Color(0xFF1E1E1E),
    onSurface = TwilightSilver
)