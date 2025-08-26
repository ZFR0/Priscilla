package com.example.priscilla.data.auth

data class UserProfile(
    val photoUrl: String? = null,
    val visualPreferences: VisualPreferences = VisualPreferences.DEFAULT
)

// Represents the Color Palette choice
enum class ColorPalette {
    SYSTEM, // Will default to Imperial Crimson
    IMPERIAL_CRIMSON,
    GOLDEN_FAN,
    YANG_SWORD,
    YIN_ECLIPSE,
    AZURE_DREAM,
    CRIMSON_TWILIGHT
}
// Represents the font style choice
enum class TypographyStyle {
    SERVANT_STANDARD,
    ROYAL_DECREE,
    WITCHS_SCRIPT,
    SUBARUS_JOURNAL
}

// Represents the component shape (corner roundness) choice
enum class ShapeStyle {
    MODERN, // Rounded
    IMPERIAL, // Sharp
    NOBLE_CUT,
    PILLOWED
}

// Represents the loading indicator choice
enum class LoaderStyle {
    STANDARD, // CircularProgressIndicator
    CHIBI // Lottie Animation
}

enum class BorderStyle {
    DEFAULT, // The standard Material Design border
    RED_JEWEL,
    SHARP_GREEN, // Placeholder for your future style
    BLUE_DROPLET  // Placeholder for your future style
}