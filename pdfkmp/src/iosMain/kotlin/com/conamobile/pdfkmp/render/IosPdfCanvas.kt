package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddPath
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextClip
import platform.CoreGraphics.CGContextClipToRect
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextSetRGBStrokeColor
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextDrawPath
import platform.CoreGraphics.CGContextStrokeRectWithWidth
import platform.CoreGraphics.CGPathAddCurveToPoint
import platform.CoreGraphics.CGPathAddLineToPoint
import platform.CoreGraphics.CGPathAddQuadCurveToPoint
import platform.CoreGraphics.CGPathAddRoundedRect
import platform.CoreGraphics.CGPathCloseSubpath
import platform.CoreGraphics.CGPathCreateMutable
import platform.CoreGraphics.CGPathDrawingMode
import platform.CoreGraphics.CGPathMoveToPoint
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawLinearGradient
import platform.CoreGraphics.CGContextDrawRadialGradient
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGGradientRelease
import platform.CoreGraphics.CGPathRelease
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.kCGGradientDrawsAfterEndLocation
import platform.CoreGraphics.kCGGradientDrawsBeforeStartLocation
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPointMake
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIGraphicsSetPDFContextURLForRect
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSKernAttributeName
import platform.UIKit.UIColor
import platform.UIKit.drawAtPoint

/**
 * [PdfCanvas] backed by the [CGContextRef] supplied by `UIGraphics*PDF`.
 *
 * Drawing through this implementation produces a vector PDF: text becomes
 * glyph references, rectangles and lines become path operations. iOS's PDF
 * context preserves the vector intent of every Core Graphics call, so
 * zooming the result stays sharp at any magnification.
 *
 * PdfKmp coordinates use a top-left origin with Y growing downward.
 * `UIGraphicsBeginPDFContextToData` already ships with a flipped CTM that
 * matches UIKit's drawing convention, so callers do not see the underlying
 * Core Graphics bottom-left origin.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPdfCanvas(
    private val ctx: CGContextRef,
    private val fonts: IosFontRegistry,
) : PdfCanvas {

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        val font = fonts.fontFor(style)
        val color = UIColor.colorWithRed(
            red = style.color.red.toDouble(),
            green = style.color.green.toDouble(),
            blue = style.color.blue.toDouble(),
            alpha = style.color.alpha.toDouble(),
        )
        val attributes = mutableMapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color,
        )
        if (style.letterSpacing.value != 0f) {
            attributes[NSKernAttributeName] = style.letterSpacing.value.toDouble()
        }
        @Suppress("CAST_NEVER_SUCCEEDS") // Kotlin/Native String is bridged with NSString at runtime.
        val nsString = text as NSString
        nsString.drawAtPoint(
            point = CGPointMake(x.toDouble(), y.toDouble()),
            withAttributes = attributes,
        )
    }

    override fun drawRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
    ) {
        CGContextSetRGBFillColor(
            ctx,
            color.red.toDouble(),
            color.green.toDouble(),
            color.blue.toDouble(),
            color.alpha.toDouble(),
        )
        CGContextFillRect(ctx, CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()))
    }

    override fun drawRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
    ) {
        CGContextSetRGBFillColor(
            ctx,
            color.red.toDouble(),
            color.green.toDouble(),
            color.blue.toDouble(),
            color.alpha.toDouble(),
        )
        val path = CGPathCreateMutable() ?: return
        try {
            CGPathAddRoundedRect(
                path = path,
                transform = null,
                rect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
                cornerWidth = cornerRadius.toDouble(),
                cornerHeight = cornerRadius.toDouble(),
            )
            CGContextAddPath(ctx, path)
            CGContextFillPath(ctx)
        } finally {
            CGPathRelease(path)
        }
    }

    override fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
        thickness: Float,
    ) {
        CGContextSetRGBStrokeColor(
            ctx,
            color.red.toDouble(),
            color.green.toDouble(),
            color.blue.toDouble(),
            color.alpha.toDouble(),
        )
        CGContextStrokeRectWithWidth(
            ctx,
            CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
            thickness.toDouble(),
        )
    }

    override fun strokeRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
        thickness: Float,
    ) {
        CGContextSetRGBStrokeColor(
            ctx,
            color.red.toDouble(),
            color.green.toDouble(),
            color.blue.toDouble(),
            color.alpha.toDouble(),
        )
        CGContextSetLineWidth(ctx, thickness.toDouble())
        val path = CGPathCreateMutable() ?: return
        try {
            CGPathAddRoundedRect(
                path = path,
                transform = null,
                rect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
                cornerWidth = cornerRadius.toDouble(),
                cornerHeight = cornerRadius.toDouble(),
            )
            CGContextAddPath(ctx, path)
            CGContextStrokePath(ctx)
        } finally {
            CGPathRelease(path)
        }
    }

    override fun saveState() {
        CGContextSaveGState(ctx)
    }

    override fun restoreState() {
        CGContextRestoreGState(ctx)
    }

    override fun clipRect(x: Float, y: Float, width: Float, height: Float) {
        CGContextClipToRect(
            ctx,
            CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
        )
    }

    override fun clipRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        val path = CGPathCreateMutable() ?: return
        try {
            CGPathAddRoundedRect(
                path = path,
                transform = null,
                rect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
                cornerWidth = cornerRadius.toDouble(),
                cornerHeight = cornerRadius.toDouble(),
            )
            CGContextAddPath(ctx, path)
            CGContextClip(ctx)
        } finally {
            CGPathRelease(path)
        }
    }

    override fun clipPath(commands: List<PathCommand>) {
        if (commands.isEmpty()) return
        val path = CGPathCreateMutable() ?: return
        try {
            for (cmd in commands) {
                when (cmd) {
                    is PathCommand.MoveTo -> CGPathMoveToPoint(
                        path = path, m = null, x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.LineTo -> CGPathAddLineToPoint(
                        path = path, m = null, x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.CubicTo -> CGPathAddCurveToPoint(
                        path = path, m = null,
                        cp1x = cmd.c1x.toDouble(), cp1y = cmd.c1y.toDouble(),
                        cp2x = cmd.c2x.toDouble(), cp2y = cmd.c2y.toDouble(),
                        x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.QuadTo -> CGPathAddQuadCurveToPoint(
                        path = path, m = null,
                        cpx = cmd.cx.toDouble(), cpy = cmd.cy.toDouble(),
                        x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    PathCommand.Close -> CGPathCloseSubpath(path)
                }
            }
            CGContextAddPath(ctx, path)
            CGContextClip(ctx)
        } finally {
            CGPathRelease(path)
        }
    }

    override fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: PdfColor,
        thickness: Float,
        style: LineStyle,
    ) {
        CGContextSetRGBStrokeColor(
            ctx,
            color.red.toDouble(),
            color.green.toDouble(),
            color.blue.toDouble(),
            color.alpha.toDouble(),
        )
        CGContextSetLineWidth(ctx, thickness.toDouble())
        applyLineDash(style, thickness)
        CGContextBeginPath(ctx)
        CGContextMoveToPoint(ctx, x1.toDouble(), y1.toDouble())
        CGContextAddLineToPoint(ctx, x2.toDouble(), y2.toDouble())
        CGContextStrokePath(ctx)
        // Reset back to solid for subsequent strokes that didn't request a pattern.
        CGContextSetLineDash(ctx, 0.0, null, 0u)
        CGContextSetLineCap(ctx, CGLineCap.kCGLineCapButt)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun applyLineDash(style: LineStyle, thickness: Float) {
        when (style) {
            LineStyle.Solid -> {
                CGContextSetLineDash(ctx, 0.0, null, 0u)
                CGContextSetLineCap(ctx, CGLineCap.kCGLineCapButt)
            }
            LineStyle.Dashed -> {
                val pattern = doubleArrayOf((thickness * 4f).toDouble(), (thickness * 2f).toDouble())
                pattern.usePinned { pinned ->
                    CGContextSetLineDash(ctx, 0.0, pinned.addressOf(0), pattern.size.toULong())
                }
                CGContextSetLineCap(ctx, CGLineCap.kCGLineCapButt)
            }
            LineStyle.Dotted -> {
                // Zero-length dash + round cap renders as circular dots
                // whose diameter equals the stroke width.
                val pattern = doubleArrayOf(0.0, (thickness * 2f).toDouble())
                pattern.usePinned { pinned ->
                    CGContextSetLineDash(ctx, 0.0, pinned.addressOf(0), pattern.size.toULong())
                }
                CGContextSetLineCap(ctx, CGLineCap.kCGLineCapRound)
            }
        }
    }

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) {
        if (commands.isEmpty()) return
        val hasFill = fill != null
        val hasStroke = strokeColor != null && strokeWidth > 0f
        if (!hasFill && !hasStroke) return

        val path = CGPathCreateMutable() ?: return
        try {
            for (cmd in commands) {
                when (cmd) {
                    is PathCommand.MoveTo -> CGPathMoveToPoint(
                        path = path, m = null, x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.LineTo -> CGPathAddLineToPoint(
                        path = path, m = null, x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.CubicTo -> CGPathAddCurveToPoint(
                        path = path, m = null,
                        cp1x = cmd.c1x.toDouble(), cp1y = cmd.c1y.toDouble(),
                        cp2x = cmd.c2x.toDouble(), cp2y = cmd.c2y.toDouble(),
                        x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    is PathCommand.QuadTo -> CGPathAddQuadCurveToPoint(
                        path = path, m = null,
                        cpx = cmd.cx.toDouble(), cpy = cmd.cy.toDouble(),
                        x = cmd.x.toDouble(), y = cmd.y.toDouble(),
                    )
                    PathCommand.Close -> CGPathCloseSubpath(path)
                }
            }

            if (fill is PdfPaint.LinearGradient || fill is PdfPaint.RadialGradient) {
                drawGradientFill(path, fill)
            } else if (fill is PdfPaint.Solid) {
                CGContextSetRGBFillColor(
                    ctx,
                    fill.color.red.toDouble(),
                    fill.color.green.toDouble(),
                    fill.color.blue.toDouble(),
                    fill.color.alpha.toDouble(),
                )
                CGContextAddPath(ctx, path)
                CGContextDrawPath(ctx, CGPathDrawingMode.kCGPathFill)
            }

            if (hasStroke) {
                strokeColor!!
                CGContextSetRGBStrokeColor(
                    ctx,
                    strokeColor.red.toDouble(),
                    strokeColor.green.toDouble(),
                    strokeColor.blue.toDouble(),
                    strokeColor.alpha.toDouble(),
                )
                CGContextSetLineWidth(ctx, strokeWidth.toDouble())
                CGContextAddPath(ctx, path)
                CGContextDrawPath(ctx, CGPathDrawingMode.kCGPathStroke)
            }
        } finally {
            CGPathRelease(path)
        }
    }

    /**
     * Strokes / fills a gradient inside [path]. CoreGraphics has no first-
     * class "fill with gradient" call — the documented technique is to
     * clip the context to the path then draw the gradient over the
     * clipped region. We wrap that in `saveGState` / `restoreGState` so
     * the clip doesn't leak out of this function.
     */
    private fun drawGradientFill(path: platform.CoreGraphics.CGMutablePathRef, paint: PdfPaint?) {
        if (paint !is PdfPaint.LinearGradient && paint !is PdfPaint.RadialGradient) return
        CGContextSaveGState(ctx)
        try {
            CGContextAddPath(ctx, path)
            CGContextClip(ctx)
            when (paint) {
                is PdfPaint.LinearGradient -> {
                    val cgGradient = createCGGradient(paint.stops) ?: return
                    try {
                        val flags = (kCGGradientDrawsBeforeStartLocation or kCGGradientDrawsAfterEndLocation).toUInt()
                        CGContextDrawLinearGradient(
                            c = ctx,
                            gradient = cgGradient,
                            startPoint = CGPointMake(paint.startX.toDouble(), paint.startY.toDouble()),
                            endPoint = CGPointMake(paint.endX.toDouble(), paint.endY.toDouble()),
                            options = flags,
                        )
                    } finally {
                        CGGradientRelease(cgGradient)
                    }
                }
                is PdfPaint.RadialGradient -> {
                    val cgGradient = createCGGradient(paint.stops) ?: return
                    try {
                        val flags = (kCGGradientDrawsBeforeStartLocation or kCGGradientDrawsAfterEndLocation).toUInt()
                        CGContextDrawRadialGradient(
                            c = ctx,
                            gradient = cgGradient,
                            startCenter = CGPointMake(paint.centerX.toDouble(), paint.centerY.toDouble()),
                            startRadius = 0.0,
                            endCenter = CGPointMake(paint.centerX.toDouble(), paint.centerY.toDouble()),
                            endRadius = paint.radius.toDouble(),
                            options = flags,
                        )
                    } finally {
                        CGGradientRelease(cgGradient)
                    }
                }
                else -> Unit
            }
        } finally {
            CGContextRestoreGState(ctx)
        }
    }

    /**
     * Builds a [CGGradient][platform.CoreGraphics.CGGradientRef] from the
     * supplied stops. Components are packed as the contiguous RGBA quads
     * that `CGGradientCreateWithColorComponents` expects, and stops are
     * placed at their offsets along the gradient.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createCGGradient(stops: List<com.conamobile.pdfkmp.style.GradientStop>): platform.CoreGraphics.CGGradientRef? {
        if (stops.isEmpty()) return null
        // CGGradientCreateWithColorComponents expects CGFloat (which is
        // Double on 64-bit Apple platforms). Pack everything as DoubleArray.
        val components = DoubleArray(stops.size * 4)
        val locations = DoubleArray(stops.size)
        for ((i, stop) in stops.withIndex()) {
            components[i * 4] = stop.color.red.toDouble()
            components[i * 4 + 1] = stop.color.green.toDouble()
            components[i * 4 + 2] = stop.color.blue.toDouble()
            components[i * 4 + 3] = stop.color.alpha.toDouble()
            locations[i] = stop.offset.toDouble()
        }
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        return components.usePinned { compPin ->
            locations.usePinned { locPin ->
                CGGradientCreateWithColorComponents(
                    space = colorSpace,
                    components = compPin.addressOf(0),
                    locations = locPin.addressOf(0),
                    count = stops.size.toULong(),
                )
            }
        }
    }

    override fun linkAnnotation(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        url: String,
    ) {
        if (width <= 0f || height <= 0f) return
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIGraphicsSetPDFContextURLForRect(
            url = nsUrl,
            rect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
        )
    }

    override fun drawImage(
        bytes: ByteArray,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        contentScale: ContentScale,
        sourceTop: Float,
        sourceBottom: Float,
    ) {
        if (bytes.isEmpty() || width <= 0f || height <= 0f) return
        val cgImage = decodeCGImage(bytes) ?: return
        try {
            val srcWidth = CGImageGetWidth(cgImage).toDouble()
            val srcHeight = CGImageGetHeight(cgImage).toDouble()
            val srcTopPx = (srcHeight * sourceTop.coerceIn(0f, 1f).toDouble())
            val srcBottomPx = (srcHeight * sourceBottom.coerceIn(0f, 1f).toDouble())
                .coerceAtLeast(srcTopPx + 1.0)
            val sliceHeight = srcBottomPx - srcTopPx
            val sliceRect = CGRectMake(0.0, srcTopPx, srcWidth, sliceHeight)
            val sliced = CGImageCreateWithImageInRect(cgImage, sliceRect) ?: return
            try {
                val finalImage = if (contentScale == ContentScale.Crop) {
                    cropImageToFill(sliced, dstWidth = width.toDouble(), dstHeight = height.toDouble())
                        ?: sliced
                } else {
                    sliced
                }
                val dstRect = applyContentScale(
                    scale = contentScale,
                    srcWidth = srcWidth,
                    srcHeight = sliceHeight,
                    dstX = x.toDouble(),
                    dstY = y.toDouble(),
                    dstWidth = width.toDouble(),
                    dstHeight = height.toDouble(),
                )
                drawImageRespectingFlippedContext(finalImage, dstRect)
                if (finalImage != sliced) CGImageRelease(finalImage)
            } finally {
                CGImageRelease(sliced)
            }
        } finally {
            CGImageRelease(cgImage)
        }
    }

    /**
     * Draws [image] inside [dstRect] while compensating for the flipped CTM
     * that `UIGraphicsBeginPDFContextToData` installs.
     *
     * The PDF context flips the Y axis so UIKit drawing reads naturally
     * (top-left origin). [CGContextDrawImage] however interprets its
     * destination rectangle in the *current* coordinate system — which is
     * already flipped — and then flips again when reading the image, ending
     * up with an upside-down image. Saving the state, applying a local
     * vertical flip translated to [dstRect], and drawing into a clean unit
     * rect cancels the second flip and produces the expected orientation.
     */
    private fun drawImageRespectingFlippedContext(image: platform.CoreGraphics.CGImageRef, dstRect: CValue<CGRect>) {
        CGContextSaveGState(ctx)
        try {
            dstRect.useContents {
                // Standard Apple pattern for drawing a CGImage into a
                // top-left-origin (UIKit-flipped) context: move the
                // origin to the bottom-left of the destination, flip the
                // Y axis locally, then draw at (0, 0, w, h). The local
                // flip cancels the context-level flip exactly inside the
                // image rectangle, leaving everything outside untouched.
                CGContextTranslateCTM(ctx, origin.x, origin.y + size.height)
                CGContextScaleCTM(ctx, 1.0, -1.0)
                CGContextDrawImage(
                    ctx,
                    CGRectMake(0.0, 0.0, size.width, size.height),
                    image,
                )
            }
        } finally {
            CGContextRestoreGState(ctx)
        }
    }

    /**
     * Decodes a PNG/JPEG/HEIF byte array into a [platform.CoreGraphics.CGImageRef].
     *
     * Uses a [platform.CoreGraphics.CGDataProvider] over the pinned bytes
     * rather than going through `NSData` → `CFData` toll-free bridging,
     * which Kotlin/Native's runtime refuses to cast (`_NSInlineData
     * cannot be cast to CPointer`). The provider holds the bytes for the
     * lifetime of the [platform.ImageIO.CGImageSource], so we keep the
     * pinned scope alive across the source creation.
     */
    private fun decodeCGImage(bytes: ByteArray): platform.CoreGraphics.CGImageRef? {
        if (bytes.isEmpty()) return null
        return bytes.usePinned { pinned ->
            val provider = platform.CoreGraphics.CGDataProviderCreateWithData(
                info = null,
                data = pinned.addressOf(0),
                size = bytes.size.toULong(),
                releaseData = null,
            ) ?: return@usePinned null
            try {
                val source = platform.ImageIO.CGImageSourceCreateWithDataProvider(provider, null)
                    ?: return@usePinned null
                try {
                    CGImageSourceCreateImageAtIndex(source, 0u, null)
                } finally {
                    CFRelease(source)
                }
            } finally {
                platform.CoreGraphics.CGDataProviderRelease(provider)
            }
        }
    }
}

/**
 * Computes the destination rectangle that respects [scale] for an image of
 * intrinsic size `srcWidth × srcHeight` drawn into
 * `(dstX, dstY, dstWidth, dstHeight)`.
 */
@OptIn(ExperimentalForeignApi::class)
private fun applyContentScale(
    scale: ContentScale,
    srcWidth: Double,
    srcHeight: Double,
    dstX: Double,
    dstY: Double,
    dstWidth: Double,
    dstHeight: Double,
): CValue<CGRect> {
    if (srcWidth <= 0.0 || srcHeight <= 0.0) {
        return CGRectMake(dstX, dstY, dstWidth, dstHeight)
    }
    val srcAspect = srcWidth / srcHeight
    val dstAspect = if (dstHeight == 0.0) srcAspect else dstWidth / dstHeight
    return when (scale) {
        ContentScale.FillBounds, ContentScale.Crop -> CGRectMake(dstX, dstY, dstWidth, dstHeight)
        ContentScale.Fit -> if (srcAspect > dstAspect) {
            val drawHeight = dstWidth / srcAspect
            val offset = (dstHeight - drawHeight) / 2.0
            CGRectMake(dstX, dstY + offset, dstWidth, drawHeight)
        } else {
            val drawWidth = dstHeight * srcAspect
            val offset = (dstWidth - drawWidth) / 2.0
            CGRectMake(dstX + offset, dstY, drawWidth, dstHeight)
        }
    }
}

/**
 * For [ContentScale.Crop], returns a sub-image taken from the centre of the
 * source so the cropped image fills the destination while preserving its
 * own aspect ratio.
 */
@OptIn(ExperimentalForeignApi::class)
private fun cropImageToFill(
    source: platform.CoreGraphics.CGImageRef,
    dstWidth: Double,
    dstHeight: Double,
): platform.CoreGraphics.CGImageRef? {
    val srcWidth = CGImageGetWidth(source).toDouble()
    val srcHeight = CGImageGetHeight(source).toDouble()
    if (srcWidth <= 0.0 || srcHeight <= 0.0 || dstHeight <= 0.0) return null
    val srcAspect = srcWidth / srcHeight
    val dstAspect = dstWidth / dstHeight
    val cropRect: CValue<CGRect> = if (srcAspect > dstAspect) {
        val targetWidth = srcHeight * dstAspect
        val padding = (srcWidth - targetWidth) / 2.0
        CGRectMake(padding, 0.0, targetWidth, srcHeight)
    } else {
        val targetHeight = srcWidth / dstAspect
        val padding = (srcHeight - targetHeight) / 2.0
        CGRectMake(0.0, padding, srcWidth, targetHeight)
    }
    return CGImageCreateWithImageInRect(source, cropRect)
}
