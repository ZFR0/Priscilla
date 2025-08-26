package com.example.priscilla.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity

fun Modifier.drawCustomFrame(
    // The function now takes a single, clean FrameStyle object
    style: FrameStyle
): Modifier = composed {
    val images = style.imageSet

    val cornerTopLeft = painterResource(id = images.cornerTopLeft)
    val cornerTopRight = painterResource(id = images.cornerTopRight)
    val cornerBottomLeft = painterResource(id = images.cornerBottomLeft)
    val cornerBottomRight = painterResource(id = images.cornerBottomRight)
    val edgeTop = painterResource(id = images.edgeTop)
    val edgeBottom = painterResource(id = images.edgeBottom)
    val edgeLeft = painterResource(id = images.edgeLeft)
    val edgeRight = painterResource(id = images.edgeRight)

    // Load decorations only if the ID is not null.
    val decoTop = images.decoTop?.let { painterResource(id = it) }
    val decoBottom = images.decoBottom?.let { painterResource(id = it) }
    val decoLeft = images.decoLeft?.let { painterResource(id = it) }
    val decoRight = images.decoRight?.let { painterResource(id = it) }

    // Get the density to convert Dp offsets to Px
    val density = LocalDensity.current

    this.drawWithContent {
        drawContent()

        // Read the master scale from the style object
        val scale = style.scale
        val componentSize = this.size

        val overlapPx = ((edgeTop.intrinsicSize.height * scale) / 2.0f).roundToInt()

        val ctlSize = (cornerTopLeft.intrinsicSize * style.scale).toIntSize()
        val ctrSize = (cornerTopRight.intrinsicSize * style.scale).toIntSize()
        val cblSize = (cornerBottomLeft.intrinsicSize * style.scale).toIntSize()
        val cbrSize = (cornerBottomRight.intrinsicSize * style.scale).toIntSize()
        val etSize = (edgeTop.intrinsicSize * style.scale).toIntSize()
        val ebSize = (edgeBottom.intrinsicSize * style.scale).toIntSize()
        val elSize = (edgeLeft.intrinsicSize * style.scale).toIntSize()
        val erSize = (edgeRight.intrinsicSize * style.scale).toIntSize()

        // --- Draw Corners with Tuned Offsets ---
        val ctlOffsetX = with(density) { style.cornerTopLeftOffsetX.toPx() }.roundToInt()
        val ctlOffsetY = with(density) { style.cornerTopLeftOffsetY.toPx() }.roundToInt()
        val ctrOffsetX = with(density) { style.cornerTopRightOffsetX.toPx() }.roundToInt()
        val ctrOffsetY = with(density) { style.cornerTopRightOffsetY.toPx() }.roundToInt()
        val cblOffsetX = with(density) { style.cornerBottomLeftOffsetX.toPx() }.roundToInt()
        val cblOffsetY = with(density) { style.cornerBottomLeftOffsetY.toPx() }.roundToInt()
        val cbrOffsetX = with(density) { style.cornerBottomRightOffsetX.toPx() }.roundToInt()
        val cbrOffsetY = with(density) { style.cornerBottomRightOffsetY.toPx() }.roundToInt()
        val edgeTopOffsetYPx = with(density) { style.edgeTopOffsetY.toPx() }.roundToInt()
        val edgeBottomOffsetYPx = with(density) { style.edgeBottomOffsetY.toPx() }.roundToInt()
        val edgeLeftOffsetX = with(density) { style.edgeLeftOffsetX.toPx() }.roundToInt()
        val edgeRightOffsetX = with(density) { style.edgeRightOffsetX.toPx() }.roundToInt()

        translate(left = (-overlapPx + ctlOffsetX).toFloat(), top = (-overlapPx + ctlOffsetY).toFloat()) { cornerTopLeft.drawScaled(this, style.scale) }
        translate(left = (componentSize.width - ctrSize.width + overlapPx + ctrOffsetX), top = (-overlapPx + ctrOffsetY).toFloat()) { cornerTopRight.drawScaled(this, style.scale) }
        translate(left = (-overlapPx + cblOffsetX).toFloat(), top = (componentSize.height - cblSize.height + overlapPx + cblOffsetY)) { cornerBottomLeft.drawScaled(this, style.scale) }
        translate(left = (componentSize.width - cbrSize.width + overlapPx + cbrOffsetX), top = (componentSize.height - cbrSize.height + overlapPx + cbrOffsetY)) { cornerBottomRight.drawScaled(this, style.scale) }

        // --- Tile edges with gaps and offsets ---
        // Top Edge
        if (decoTop != null) {
            val decoSize = (decoTop.intrinsicSize * style.scale).toIntSize()
            val leftSegmentWidth = ((componentSize.width - decoSize.width) / 2.0f).roundToInt() - ctlSize.width + overlapPx - ctlOffsetX
            val leftSegmentStartX = (ctlSize.width - overlapPx + ctlOffsetX)
            translate(left = leftSegmentStartX.toFloat(), top = (-overlapPx + edgeTopOffsetYPx).toFloat()) {
                drawTiled(edgeTop, style.scale, IntSize(leftSegmentWidth, etSize.height), tileHorizontally = true)
            }
            val rightSegmentWidth = ((componentSize.width - decoSize.width) / 2.0f).roundToInt() - ctrSize.width + overlapPx + ctrOffsetX
            val rightSegmentStartX = ((componentSize.width + decoSize.width) / 2.0f).roundToInt()
            translate(left = rightSegmentStartX.toFloat(), top = (-overlapPx + edgeTopOffsetYPx).toFloat()) {
                drawTiled(edgeTop, style.scale, IntSize(rightSegmentWidth, etSize.height), tileHorizontally = true, reverse = true)
            }
        } else { // No top deco
            val totalWidth = componentSize.width.roundToInt() - ctlSize.width - ctrSize.width + (2 * overlapPx) - ctlOffsetX + ctrOffsetX
            translate(left = (ctlSize.width - overlapPx + ctlOffsetX).toFloat(), top = (-overlapPx + edgeTopOffsetYPx).toFloat()) {
                drawTiled(edgeTop, style.scale, IntSize(totalWidth, etSize.height), tileHorizontally = true)
            }
        }

        // Bottom Edge
        if (decoBottom != null) {
            val decoSize = (decoBottom.intrinsicSize * style.scale).toIntSize()
            val startY = (componentSize.height - ebSize.height + overlapPx).roundToInt()
            val leftSegmentWidth = ((componentSize.width - decoSize.width) / 2.0f).roundToInt() - cblSize.width + overlapPx - cblOffsetX
            translate(left = (cblSize.width - overlapPx + cblOffsetX).toFloat(), top = (startY + edgeBottomOffsetYPx).toFloat()) {
                drawTiled(edgeBottom, style.scale, IntSize(leftSegmentWidth, ebSize.height), tileHorizontally = true)
            }
            val rightSegmentWidth = ((componentSize.width - decoSize.width) / 2.0f).roundToInt() - cbrSize.width + overlapPx + cbrOffsetX
            val rightSegmentStartX = ((componentSize.width + decoSize.width) / 2.0f).roundToInt()
            translate(left = rightSegmentStartX.toFloat(), top = (startY + edgeBottomOffsetYPx).toFloat()) {
                drawTiled(edgeBottom, style.scale, IntSize(rightSegmentWidth, ebSize.height), tileHorizontally = true, reverse = true)
            }
        } else { // No bottom deco
            val totalWidth = componentSize.width.roundToInt() - cblSize.width - cbrSize.width + (2 * overlapPx) - cblOffsetX + cbrOffsetX
            translate(left = (cblSize.width - overlapPx + cblOffsetX).toFloat(), top = (componentSize.height - ebSize.height + overlapPx + edgeBottomOffsetYPx)) {
                drawTiled(edgeBottom, style.scale, IntSize(totalWidth, ebSize.height), tileHorizontally = true)
            }
        }

        // Left Edge
        if (decoLeft != null) {
            val decoSize = (decoLeft.intrinsicSize * style.scale).toIntSize()
            val topSegmentHeight = ((componentSize.height - decoSize.height) / 2.0f).roundToInt() - ctlSize.height + overlapPx - ctlOffsetY
            val topSegmentStartY = (ctlSize.height - overlapPx + ctlOffsetY)
            translate(left = (-overlapPx + edgeLeftOffsetX).toFloat(), top = topSegmentStartY.toFloat()) {
                drawTiled(edgeLeft, style.scale, IntSize(elSize.width, topSegmentHeight), tileHorizontally = false)
            }
            val bottomSegmentHeight = ((componentSize.height - decoSize.height) / 2.0f).roundToInt() - cblSize.height + overlapPx + cblOffsetY
            val bottomSegmentStartY = ((componentSize.height + decoSize.height) / 2.0f).roundToInt()
            translate(left = (-overlapPx + edgeLeftOffsetX).toFloat(), top = bottomSegmentStartY.toFloat()) {
                drawTiled(edgeLeft, style.scale, IntSize(elSize.width, bottomSegmentHeight), tileHorizontally = false, reverse = true)
            }
        } else { // No left deco
            val totalHeight = componentSize.height.roundToInt() - ctlSize.height - cblSize.height + (2 * overlapPx) - ctlOffsetY + cblOffsetY
            if (totalHeight > 0) {
                translate(left = (-overlapPx + edgeLeftOffsetX).toFloat(), top = (ctlSize.height - overlapPx + ctlOffsetY).toFloat()) {
                    drawTiled(edgeLeft, style.scale, IntSize(elSize.width, totalHeight), tileHorizontally = false)
                }
            }
        }

        // Right Edge
        if (decoRight != null) {
            val decoSize = (decoRight.intrinsicSize * style.scale).toIntSize()
            val topSegmentHeight = ((componentSize.height - decoSize.height) / 2.0f).roundToInt() - ctrSize.height + overlapPx - ctrOffsetY
            val topSegmentStartY = (ctrSize.height - overlapPx + ctrOffsetY)
            translate(left = (componentSize.width - erSize.width + overlapPx + edgeRightOffsetX), top = topSegmentStartY.toFloat()) {
                drawTiled(edgeRight, style.scale, IntSize(erSize.width, topSegmentHeight), tileHorizontally = false)
            }
            val bottomSegmentHeight = ((componentSize.height - decoSize.height) / 2.0f).roundToInt() - cbrSize.height + overlapPx + cbrOffsetY
            val bottomSegmentStartY = ((componentSize.height + decoSize.height) / 2.0f).roundToInt()
            translate(left = (componentSize.width - erSize.width + overlapPx + edgeRightOffsetX), top = bottomSegmentStartY.toFloat()) {
                drawTiled(edgeRight, style.scale, IntSize(erSize.width, bottomSegmentHeight), tileHorizontally = false, reverse = true)
            }
        } else { // No right deco
            val totalHeight = componentSize.height.roundToInt() - ctrSize.height - cbrSize.height + (2 * overlapPx) - ctrOffsetY + cbrOffsetY
            if (totalHeight > 0) {
                translate(left = (componentSize.width - erSize.width + overlapPx + edgeRightOffsetX), top = (ctrSize.height - overlapPx + ctrOffsetY).toFloat()) {
                    drawTiled(edgeRight, style.scale, IntSize(erSize.width, totalHeight), tileHorizontally = false)
                }
            }
        }

        // Left Edge
        val leftEdgeHeight = componentSize.height.roundToInt() - ctlSize.height - cblSize.height + (2 * overlapPx) - ctlOffsetY + cblOffsetY
        if (leftEdgeHeight > 0) {
            translate(left = (-overlapPx + edgeLeftOffsetX).toFloat(), top = (ctlSize.height - overlapPx + ctlOffsetY).toFloat()) {
                drawTiled(edgeLeft, style.scale, IntSize(elSize.width, leftEdgeHeight), tileHorizontally = false)
            }
        }

        // Right Edge
        val rightEdgeHeight = componentSize.height.roundToInt() - ctrSize.height - cbrSize.height + (2 * overlapPx) - ctrOffsetY + cbrOffsetY
        if (rightEdgeHeight > 0) {
            translate(left = (componentSize.width - erSize.width + overlapPx + edgeRightOffsetX), top = (ctrSize.height - overlapPx + ctrOffsetY).toFloat()) {
                drawTiled(edgeRight, style.scale, IntSize(erSize.width, rightEdgeHeight), tileHorizontally = false)
            }
        }

        // Draw decorations (with their own offsets)
        val decoTopOffsetYPx = with(density) { style.decoTopOffsetY.toPx() }.roundToInt()
        decoTop?.let { painter ->
            val decoSize = (painter.intrinsicSize * style.scale).toIntSize()
            val horizontalOffset = ((componentSize.width - decoSize.width) / 2.0f).roundToInt()
            val verticalOffset = (-decoSize.height / 2.0f).roundToInt()
            translate(left = horizontalOffset.toFloat(), top = (verticalOffset + decoTopOffsetYPx).toFloat()) {
                painter.drawScaled(this, style.scale)
            }
        }

        val decoBottomOffsetYPx = with(density) { style.decoBottomOffsetY.toPx() }.roundToInt()
        decoBottom?.let { painter ->
            val decoSize = (painter.intrinsicSize * style.scale).toIntSize()
            val horizontalOffset = ((componentSize.width - decoSize.width) / 2.0f).roundToInt()
            val verticalOffset = (componentSize.height - (decoSize.height / 2.0f)).roundToInt()
            translate(left = horizontalOffset.toFloat(), top = (verticalOffset + decoBottomOffsetYPx).toFloat()) {
                painter.drawScaled(this, style.scale)
            }
        }

        // Draw Left and Right decorations
        val decoLeftOffsetXpx = with(density) { style.decoLeftOffsetX.toPx() }.roundToInt()
        decoLeft?.let { painter ->
            val decoSize = (painter.intrinsicSize * style.scale).toIntSize()
            val horizontalOffset = (-decoSize.width / 2.0f).roundToInt()
            val verticalOffset = ((componentSize.height - decoSize.height) / 2.0f).roundToInt()
            translate(left = (horizontalOffset + decoLeftOffsetXpx).toFloat(), top = verticalOffset.toFloat()) {
                painter.drawScaled(this, style.scale)
            }
        }

        val decoRightOffsetXpx = with(density) { style.decoRightOffsetX.toPx() }.roundToInt()
        decoRight?.let { painter ->
            val decoSize = (painter.intrinsicSize * style.scale).toIntSize()
            val horizontalOffset = (componentSize.width - (decoSize.width / 2.0f)).roundToInt()
            val verticalOffset = ((componentSize.height - decoSize.height) / 2.0f).roundToInt()
            translate(left = (horizontalOffset + decoRightOffsetXpx).toFloat(), top = verticalOffset.toFloat()) {
                painter.drawScaled(this, style.scale)
            }
        }
    }
}

private operator fun Size.times(scale: Float): Size = Size(width * scale, height * scale)

private fun Size.toIntSize() = IntSize(width.roundToInt(), height.roundToInt())

private fun Painter.drawScaled(scope: DrawScope, scale: Float, srcSize: IntSize = intrinsicSize.toIntSize()) {
    with(scope) {
        val dstSize = IntSize((srcSize.width * scale).roundToInt(), (srcSize.height * scale).roundToInt())
        if (dstSize.width <= 0 || dstSize.height <= 0 || srcSize.width <= 0 || srcSize.height <= 0) return

        val croppedBitmap = toBitmap(srcSize)
        drawImage(
            image = croppedBitmap,
            dstSize = dstSize
        )
    }
}

private fun Painter.toBitmap(size: IntSize): ImageBitmap {
    if (size.width <= 0 || size.height <= 0) return ImageBitmap(1, 1) // Return a placeholder if size is invalid
    val bitmap = ImageBitmap(size.width, size.height)
    val canvas = Canvas(bitmap)
    val drawScope = CanvasDrawScope()
    drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(size.width.toFloat(), size.height.toFloat())) {
        with(this@toBitmap) {
            draw(this@draw.size)
        }
    }
    return bitmap
}

/**
 * Draws a painter tiled, cropping the final tile to fit the target size perfectly.
 * This version uses INTEGER math for positioning to prevent anti-aliasing seams.
 */
private fun DrawScope.drawTiled(
    painter: Painter,
    scale: Float,
    targetSize: IntSize,
    tileHorizontally: Boolean,
    reverse: Boolean = false
) {
    val scaledIntrinsic = (painter.intrinsicSize * scale).toIntSize()
    val tileDimension = if (tileHorizontally) scaledIntrinsic.width else scaledIntrinsic.height
    val targetDimension = if (tileHorizontally) targetSize.width else targetSize.height

    if (tileDimension <= 0 || targetDimension <= 0) return

    var currentPos = 0
    while (currentPos < targetDimension) {
        val remaining = targetDimension - currentPos
        val drawDimension = minOf(tileDimension, remaining)

        // This is the core logic for reversing the drawing position
        val pos = if (reverse) targetDimension - currentPos - drawDimension else currentPos

        val offset = if (tileHorizontally) Offset(pos.toFloat(), 0f) else Offset(0f, pos.toFloat())

        val srcSize = if (tileHorizontally) {
            IntSize(width = (drawDimension / scale).roundToInt(), height = painter.intrinsicSize.height.roundToInt())
        } else {
            IntSize(width = painter.intrinsicSize.width.roundToInt(), height = (drawDimension / scale).roundToInt())
        }

        translate(left = offset.x, top = offset.y) {
            painter.drawScaled(this, scale, srcSize = srcSize)
        }
        currentPos += tileDimension
    }
}