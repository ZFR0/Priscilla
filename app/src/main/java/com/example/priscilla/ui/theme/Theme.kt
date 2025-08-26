package com.example.priscilla.ui.theme


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.priscilla.MainViewModel
import com.example.priscilla.data.auth.ColorPalette
import com.example.priscilla.data.auth.ShapeStyle
import com.example.priscilla.data.auth.TypographyStyle

@Composable
fun PriscillaTheme(
    mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    content: @Composable () -> Unit
) {
    // Get the dynamic preferences state directly from the ViewModel
    val visualPreferences by mainViewModel.visualPreferences.collectAsState()

    // 1. Determine the ColorScheme based on the new visualPreferences object
    val colorScheme = when (visualPreferences.colorPalette) {
        ColorPalette.SYSTEM, ColorPalette.IMPERIAL_CRIMSON -> {
            if (isSystemInDarkTheme()) ImperialCrimsonDarkColorScheme else ImperialCrimsonLightColorScheme
        }
        ColorPalette.GOLDEN_FAN -> {
            if (isSystemInDarkTheme()) GoldenFanDarkColorScheme else GoldenFanLightColorScheme
        }
        ColorPalette.AZURE_DREAM -> {
            if (isSystemInDarkTheme()) AzureDreamDarkColorScheme else AzureDreamLightColorScheme
        }
        ColorPalette.YANG_SWORD -> {
            YangSwordDarkColorScheme
        }
        ColorPalette.CRIMSON_TWILIGHT -> {
            CrimsonTwilightDarkColorScheme
        }
        ColorPalette.YIN_ECLIPSE -> {
            YinEclipseDarkColorScheme
        }
    }

    // 2. Determine the Typography based on the new visualPreferences object
    val typography = when (visualPreferences.typographyStyle) {
        TypographyStyle.SERVANT_STANDARD -> Typography
        TypographyStyle.ROYAL_DECREE -> RoyalDecreeTypography
        TypographyStyle.WITCHS_SCRIPT -> WitchsScriptTypography
        TypographyStyle.SUBARUS_JOURNAL -> SubarusJournalTypography
    }

    // 3. Determine the Shapes based on the new visualPreferences object
    val shapes = when (visualPreferences.shapeStyle) {
        ShapeStyle.MODERN -> ModernShapes
        ShapeStyle.IMPERIAL -> ImperialShapes
        ShapeStyle.NOBLE_CUT -> NobleCutShapes
        ShapeStyle.PILLOWED -> PillowedShapes
    }

    // 4. Apply all the chosen styles to the MaterialTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}