package com.example.priscilla.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.priscilla.ThemeViewModel
import com.example.priscilla.data.auth.BorderStyle
import com.example.priscilla.data.auth.ColorPalette
import com.example.priscilla.data.auth.ShapeStyle
import com.example.priscilla.data.auth.TypographyStyle
import com.example.priscilla.ui.theme.ImperialShapes
import com.example.priscilla.ui.theme.ModernShapes
import com.example.priscilla.ui.theme.GoldenFanLightColorScheme
import com.example.priscilla.ui.theme.ImperialCrimsonLightColorScheme
import com.example.priscilla.ui.theme.YangSwordLightColorScheme
import com.example.priscilla.ui.theme.YinEclipseLightColorScheme
import com.example.priscilla.ui.theme.AzureDreamLightColorScheme
import com.example.priscilla.ui.theme.CrimsonTwilightLightColorScheme
import com.example.priscilla.ui.theme.NobleCutShapes
import com.example.priscilla.ui.theme.PillowedShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
    themeViewModel: ThemeViewModel
) {
    val preferences by themeViewModel.draftPreferences.collectAsState()

    DisposableEffect(Unit) {
        // This block runs when the composable ENTERS the screen.
        themeViewModel.beginEditing()

        onDispose {
            // This block runs when the composable LEAVES the screen.
            // (e.g., user presses back without saving).
            themeViewModel.cancelEditing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Customization") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    themeViewModel.saveChanges()
                    navController.popBackStack()
                },
                icon = { Icon(Icons.Default.Done, "Save Changes") },
                text = { Text("Apply Changes") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            // Make space for the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                ColorPaletteSection(
                    selectedPalette = preferences.colorPalette,
                    onPaletteSelected = { themeViewModel.updateColorPalette(it) }
                )
            }
            item {
                ShapeSection(
                    selectedShape = preferences.shapeStyle,
                    onShapeSelected = { themeViewModel.updateShapeStyle(it) }
                )
            }
            item {
                TypographySection(
                    selectedTypography = preferences.typographyStyle,
                    onTypographySelected = { themeViewModel.updateTypographyStyle(it) }
                )
            }
            item {
                BorderSection(
                    selectedBorder = preferences.borderStyle,
                    onBorderSelected = { themeViewModel.updateBorderStyle(it) }
                )
            }
        }
    }
}

@Composable
private fun ThemeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ColorPaletteSection(
    selectedPalette: ColorPalette,
    onPaletteSelected: (ColorPalette) -> Unit
) {
    ThemeSection("Color Palette") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColorPreviewButton(
                    text = "Imperial Crimson",
                    isSelected = selectedPalette == ColorPalette.IMPERIAL_CRIMSON || selectedPalette == ColorPalette.SYSTEM,
                    onClick = { onPaletteSelected(ColorPalette.IMPERIAL_CRIMSON) },
                    modifier = Modifier.weight(1f),
                    colorScheme = ImperialCrimsonLightColorScheme
                )
                ColorPreviewButton(
                    text = "Golden Fan",
                    isSelected = selectedPalette == ColorPalette.GOLDEN_FAN,
                    onClick = { onPaletteSelected(ColorPalette.GOLDEN_FAN) },
                    modifier = Modifier.weight(1f),
                    colorScheme = GoldenFanLightColorScheme
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColorPreviewButton(
                    text = "Yang Sword",
                    isSelected = selectedPalette == ColorPalette.YANG_SWORD,
                    onClick = { onPaletteSelected(ColorPalette.YANG_SWORD) },
                    modifier = Modifier.weight(1f),
                    colorScheme = YangSwordLightColorScheme
                )
                ColorPreviewButton(
                    text = "Yin Eclipse",
                    isSelected = selectedPalette == ColorPalette.YIN_ECLIPSE,
                    onClick = { onPaletteSelected(ColorPalette.YIN_ECLIPSE) },
                    modifier = Modifier.weight(1f),
                    colorScheme = YinEclipseLightColorScheme
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColorPreviewButton(
                    text = "Azure Dream",
                    isSelected = selectedPalette == ColorPalette.AZURE_DREAM,
                    onClick = { onPaletteSelected(ColorPalette.AZURE_DREAM) },
                    modifier = Modifier.weight(1f),
                    colorScheme = AzureDreamLightColorScheme
                )
                ColorPreviewButton(
                    text = "Crimson Twilight",
                    isSelected = selectedPalette == ColorPalette.CRIMSON_TWILIGHT,
                    onClick = { onPaletteSelected(ColorPalette.CRIMSON_TWILIGHT) },
                    modifier = Modifier.weight(1f),
                    colorScheme = CrimsonTwilightLightColorScheme
                )
            }
        }
    }
}

@Composable
private fun ShapeSection(
    selectedShape: ShapeStyle,
    onShapeSelected: (ShapeStyle) -> Unit
) {
    ThemeSection("Component Shapes") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // First Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShapePreviewButton(
                    text = "Imperial",
                    isSelected = selectedShape == ShapeStyle.IMPERIAL,
                    onClick = { onShapeSelected(ShapeStyle.IMPERIAL) },
                    shape = ImperialShapes.medium,
                    modifier = Modifier.weight(1f)
                )
                ShapePreviewButton(
                    text = "Modern",
                    isSelected = selectedShape == ShapeStyle.MODERN,
                    onClick = { onShapeSelected(ShapeStyle.MODERN) },
                    shape = ModernShapes.medium,
                    modifier = Modifier.weight(1f)
                )
            }
            // Second Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShapePreviewButton(
                    text = "Noble Cut",
                    isSelected = selectedShape == ShapeStyle.NOBLE_CUT,
                    onClick = { onShapeSelected(ShapeStyle.NOBLE_CUT) },
                    shape = NobleCutShapes.medium,
                    modifier = Modifier.weight(1f)
                )
                ShapePreviewButton(
                    text = "Pillowed",
                    isSelected = selectedShape == ShapeStyle.PILLOWED,
                    onClick = { onShapeSelected(ShapeStyle.PILLOWED) },
                    shape = PillowedShapes.medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TypographySection(
    selectedTypography: TypographyStyle,
    onTypographySelected: (TypographyStyle) -> Unit
) {
    ThemeSection("Typography") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onTypographySelected(TypographyStyle.ROYAL_DECREE) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedTypography == TypographyStyle.ROYAL_DECREE) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Royal Decree", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.priscilla.ui.theme.RoyalDecreeTypography.bodyLarge.fontFamily))
            }
            OutlinedButton(
                onClick = { onTypographySelected(TypographyStyle.SERVANT_STANDARD) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedTypography == TypographyStyle.SERVANT_STANDARD) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Servant's Standard", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.priscilla.ui.theme.Typography.bodyLarge.fontFamily))
            }
            OutlinedButton(
                onClick = { onTypographySelected(TypographyStyle.WITCHS_SCRIPT) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedTypography == TypographyStyle.WITCHS_SCRIPT) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Witch's Script", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.priscilla.ui.theme.WitchsScriptTypography.bodyLarge.fontFamily, fontSize = 18.sp))
            }
            OutlinedButton(
                onClick = { onTypographySelected(TypographyStyle.SUBARUS_JOURNAL) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedTypography == TypographyStyle.SUBARUS_JOURNAL) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Subaru's Journal", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.priscilla.ui.theme.SubarusJournalTypography.bodyLarge.fontFamily))
            }
        }
    }
}


// --- Preview Button Composables ---

@Composable
fun ColorPreviewButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: ColorScheme
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = if (isSelected) BorderStroke(2.dp, colorScheme.primary) else ButtonDefaults.outlinedButtonBorder,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colorScheme.background,
            contentColor = colorScheme.onBackground
        )
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun ShapePreviewButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = (shape as? RoundedCornerShape)?.copy(all = CornerSize(4.dp)) ?: shape,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 24.dp)
                .background(MaterialTheme.colorScheme.secondary, shape = shape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BorderSection(
    selectedBorder: BorderStyle,
    onBorderSelected: (BorderStyle) -> Unit
) {
    ThemeSection("Borders") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Option 1: Default
            OutlinedButton(
                onClick = { onBorderSelected(BorderStyle.DEFAULT) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedBorder == BorderStyle.DEFAULT) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Default")
            }
            // Option 2: Red Jewel
            OutlinedButton(
                onClick = { onBorderSelected(BorderStyle.RED_JEWEL) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedBorder == BorderStyle.RED_JEWEL) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Red Jewel")
            }
            // Option 3: Sharp Green (Placeholder)
            OutlinedButton(
                onClick = { onBorderSelected(BorderStyle.SHARP_GREEN) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedBorder == BorderStyle.SHARP_GREEN) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Sharp Green")
            }
            // Option 4: Blue Droplet (Placeholder)
            OutlinedButton(
                onClick = { onBorderSelected(BorderStyle.BLUE_DROPLET) },
                modifier = Modifier.fillMaxWidth(),
                border = if (selectedBorder == BorderStyle.BLUE_DROPLET) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Blue Droplet")
            }
        }
    }
}