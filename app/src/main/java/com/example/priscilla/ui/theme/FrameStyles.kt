package com.example.priscilla.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.priscilla.R


data class FrameImageSet(
    @DrawableRes val cornerTopLeft: Int,
    @DrawableRes val cornerTopRight: Int,
    @DrawableRes val cornerBottomLeft: Int,
    @DrawableRes val cornerBottomRight: Int,
    @DrawableRes val edgeTop: Int,
    @DrawableRes val edgeBottom: Int,
    @DrawableRes val edgeLeft: Int,
    @DrawableRes val edgeRight: Int,
    @DrawableRes val decoTop: Int? = null,
    @DrawableRes val decoBottom: Int? = null,
    @DrawableRes val decoLeft: Int? = null,
    @DrawableRes val decoRight: Int? = null
)

/**
 * A data class that holds all the configuration for a specific frame style.
 * This is the "pre-made list of values" that drives the drawing modifier.
 * Offsets are ADDED to the base calculated position.
 * Use negative values to push elements "outward" from the component.
 * Use positive values to pull elements "inward".
 */
data class FrameStyle(
    val imageSet: FrameImageSet,


    // The optional resource IDs for the central decorations
    val hasTopDeco: Boolean = false,
    val hasBottomDeco: Boolean = false,
    val hasLeftDeco: Boolean = false,
    val hasRightDeco: Boolean = false,
    // The master scaling factor for the entire frame
    val scale: Float = 1.0f,

    // --- Precise Artistic Offset Controls ---

    // Corner Offsets
    val cornerTopLeftOffsetX: Dp = 0.dp,
    val cornerTopLeftOffsetY: Dp = 0.dp,
    val cornerTopRightOffsetX: Dp = 0.dp,
    val cornerTopRightOffsetY: Dp = 0.dp,
    val cornerBottomLeftOffsetX: Dp = 0.dp,
    val cornerBottomLeftOffsetY: Dp = 0.dp,
    val cornerBottomRightOffsetX: Dp = 0.dp,
    val cornerBottomRightOffsetY: Dp = 0.dp,

    // Edge Offsets (typically only one axis is needed)
    val edgeTopOffsetY: Dp = 0.dp,
    val edgeBottomOffsetY: Dp = 0.dp,
    val edgeLeftOffsetX: Dp = 0.dp,
    val edgeRightOffsetX: Dp = 0.dp,


    // Decoration Offsets (typically only one axis is needed)
    val decoTopOffsetY: Dp = 0.dp,
    val decoBottomOffsetY: Dp = 0.dp,
    val decoLeftOffsetX: Dp = 0.dp,
    val decoRightOffsetX: Dp = 0.dp
)

object FrameImageSets {
    val RED_JEWEL = FrameImageSet(
        // We use direct, type-safe R.drawable references. No more strings!
        cornerTopLeft = R.drawable.red_jewel_corner_tl,
        cornerTopRight = R.drawable.red_jewel_corner_tr,
        cornerBottomLeft = R.drawable.red_jewel_corner_bl,
        cornerBottomRight = R.drawable.red_jewel_corner_br,
        edgeTop = R.drawable.red_jewel_edge_top,
        edgeBottom = R.drawable.red_jewel_edge_bottom,
        edgeLeft = R.drawable.red_jewel_edge_left,
        edgeRight = R.drawable.red_jewel_edge_right,
        decoTop = R.drawable.red_jewel_deco_top,
        decoBottom = R.drawable.red_jewel_deco_bottom,
    )

    val SHARP_GREEN = FrameImageSet(
        cornerTopLeft = R.drawable.sharp_green_corner_tl,
        cornerTopRight = R.drawable.sharp_green_corner_tr,
        cornerBottomLeft = R.drawable.sharp_green_corner_bl,
        cornerBottomRight = R.drawable.sharp_green_corner_br,
        edgeTop = R.drawable.sharp_green_edge_top,
        edgeBottom = R.drawable.sharp_green_edge_bottom,
        edgeLeft = R.drawable.sharp_green_edge_left,
        edgeRight = R.drawable.sharp_green_edge_right,
        decoTop = null,
        decoBottom = null,
        decoLeft = null,
        decoRight = null
    )

    val BLUE_DROPLET = FrameImageSet(
        cornerTopLeft = R.drawable.blue_droplet_corner_tl,
        cornerTopRight = R.drawable.blue_droplet_corner_tr,
        cornerBottomLeft = R.drawable.blue_droplet_corner_bl,
        cornerBottomRight = R.drawable.blue_droplet_corner_br,
        edgeTop = R.drawable.blue_droplet_edge_top,
        edgeBottom = R.drawable.blue_droplet_edge_bottom,
        edgeLeft = R.drawable.blue_droplet_edge_left,
        edgeRight = R.drawable.blue_droplet_edge_right,
        decoTop = R.drawable.blue_droplet_deco_top,
        decoBottom = R.drawable.blue_droplet_deco_bottom,
        decoLeft = R.drawable.blue_droplet_deco_left,
        decoRight = R.drawable.blue_droplet_deco_right,
    )
}

/**
 * A singleton object that holds our library of predefined frame styles.
 */
object FrameStyles {
    val NOBLE_RED_JEWEL = FrameStyle(
        hasTopDeco = true,
        hasBottomDeco = true,

        scale = 0.35f,

        cornerTopLeftOffsetX = (-0.75).dp,
        cornerTopLeftOffsetY = (-5).dp,
        cornerTopRightOffsetX = 0.75.dp,
        cornerTopRightOffsetY = (-5).dp,

        edgeTopOffsetY = (-5).dp,

        decoTopOffsetY = (-10).dp,
        decoBottomOffsetY = 14.5.dp,
        imageSet = FrameImageSets.RED_JEWEL
    )

    val SHARP_GREEN_JEWEL = FrameStyle(

        scale = 0.35f,

        cornerTopLeftOffsetX = (-10).dp,
        cornerTopLeftOffsetY = (-10).dp,
        cornerTopRightOffsetX = 10.dp,
        cornerTopRightOffsetY = (-10).dp,
        cornerBottomLeftOffsetX = (-10).dp,
        cornerBottomRightOffsetX = 10.dp,
        cornerBottomLeftOffsetY = 10.dp,
        cornerBottomRightOffsetY = 10.dp,

        edgeTopOffsetY = (-3).dp,
        edgeBottomOffsetY = 3.dp,
        edgeLeftOffsetX = (-1).dp,
        edgeRightOffsetX = 1.dp,

        imageSet = FrameImageSets.SHARP_GREEN,
    )

    val BLUE_DROPLET_FRAME = FrameStyle(
        hasTopDeco = true,
        hasBottomDeco = true,
        hasLeftDeco = true,
        hasRightDeco = true,

        scale = 0.35f,

        decoTopOffsetY = (0).dp,

        cornerTopLeftOffsetX = (-6.5).dp,
        cornerTopLeftOffsetY = (-6.5).dp,

        cornerTopRightOffsetX = 6.5.dp,
        cornerTopRightOffsetY = (-6.5).dp,

        cornerBottomLeftOffsetX = (-6.5).dp,
        cornerBottomLeftOffsetY = 6.dp,

        cornerBottomRightOffsetX = 6.5.dp,
        cornerBottomRightOffsetY = 6.dp,

        imageSet = FrameImageSets.BLUE_DROPLET,
    )


    val RED_JEWEL_TEXT_INPUT_FRAME = FrameStyle(
        hasTopDeco = true,
        hasBottomDeco = true,
        imageSet = FrameImageSets.RED_JEWEL,

        scale = 0.15f,
        decoBottomOffsetY = (6).dp,
        decoTopOffsetY = (-2).dp,
    )

    val SHARP_GREEN_TEXT_INPUT_FRAME = FrameStyle(
        imageSet = FrameImageSets.SHARP_GREEN,
        scale = 0.15f,

        edgeTopOffsetY = 3.dp,
        edgeBottomOffsetY = (-3).dp,
        edgeRightOffsetX = (-3).dp,
        edgeLeftOffsetX = 3.dp,
    )

    val BLUE_DROPLET_TEXT_INPUT_FRAME = FrameStyle(
        hasTopDeco = true,
        hasBottomDeco = true,
        hasLeftDeco = true,
        hasRightDeco = true,
        imageSet = FrameImageSets.BLUE_DROPLET,

        scale = 0.15f,

        cornerTopLeftOffsetY = 2.dp,
        cornerTopLeftOffsetX = (-3).dp,

        cornerTopRightOffsetY = 2.dp,
        cornerTopRightOffsetX = 3.dp,

        cornerBottomLeftOffsetY = (-2).dp,
        cornerBottomLeftOffsetX = (-3).dp,

        cornerBottomRightOffsetY = (-2).dp,
        cornerBottomRightOffsetX = 3.dp,
        edgeTopOffsetY = 5.dp,
        edgeBottomOffsetY = (-5).dp,
        decoTopOffsetY = 5.dp,
        decoBottomOffsetY = (-5).dp,
    )
}