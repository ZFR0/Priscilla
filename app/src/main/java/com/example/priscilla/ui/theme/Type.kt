package com.example.priscilla.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.priscilla.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)


// 1. Define the custom font family
private val RoyalFontFamily = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold)
)

// 2. Define the new Typography object using the custom font
val RoyalDecreeTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = RoyalFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = RoyalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    // Define other styles as needed, otherwise they fall back to defaults
)

// 3. Define the "Witch's Script" font family
private val WitchsScriptFontFamily = FontFamily(
    Font(R.font.dancing_script_regular, FontWeight.Normal),
    Font(R.font.dancing_script_bold, FontWeight.Bold)
)

// 4. Define the "Subaru's Journal" font family
private val SubarusJournalFontFamily = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_bold, FontWeight.Bold)
)

// 3. Define the Typography object for Witch's Script
val WitchsScriptTypography = Typography(
    // Use with caution for very dense UIs.
    headlineMedium = TextStyle(
        fontFamily = WitchsScriptFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp // Script fonts often need to be a bit larger
    ),
    bodyLarge = TextStyle(
        fontFamily = WitchsScriptFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp, // Larger for readability
        lineHeight = 28.sp
    ),
    labelLarge = TextStyle(
        fontFamily = WitchsScriptFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
)

// 4. Define the Typography object for Subaru's Journal
val SubarusJournalTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SubarusJournalFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SubarusJournalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SubarusJournalFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )
)