package com.example.priscilla.data.auth

/**
 * A data class that holds all user-configurable visual settings.
 * This structure is designed to be easily saved to and loaded from Firestore.
 */
data class VisualPreferences(
    val colorPalette: ColorPalette = ColorPalette.SYSTEM,
    val typographyStyle: TypographyStyle = TypographyStyle.ROYAL_DECREE,
    val shapeStyle: ShapeStyle = ShapeStyle.IMPERIAL,
    val loaderStyle: LoaderStyle = LoaderStyle.CHIBI,
    val borderStyle: BorderStyle = BorderStyle.DEFAULT
) {
    // Companion object to provide a default instance.
    // This is useful for guests or when loading fails.
    companion object {
        val DEFAULT = VisualPreferences()
    }
}