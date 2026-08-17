package com.conamobile.pdfkmp.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.image.PdfImagePolicy
import com.conamobile.pdfkmp.image.decodeSampleFactorFor
import com.conamobile.pdfkmp.pdfwriter.PdfBookmark
import com.conamobile.pdfkmp.pdfwriter.PdfDestination
import com.conamobile.pdfkmp.pdfwriter.PdfLink
import com.conamobile.pdfkmp.pdfwriter.PdfNavigation
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand

/**
 * [PdfCanvas] backed by an Android [Canvas].
 *
 * The canvas is supplied by [android.graphics.pdf.PdfDocument] inside its
 * `PdfDocument.Page` object. Drawing through this implementation produces
 * vector text and vector shapes in the resulting PDF — exactly what we want
 * so output stays sharp at any zoom level.
 *
 * Coordinates from PdfKmp use a top-left origin with Y increasing downward,
 * which already matches Android's canvas convention, so no Y flip is needed.
 *
 * Android's `PdfDocument` can draw vector content but cannot write the info
 * dictionary or any interactive feature (links, named destinations, the
 * outline). Those calls are therefore *recorded* into [navigation] keyed by
 * [pageIndex]; [AndroidPdfDriver.finish] feeds the collection to the
 * post-processor, which patches them into the finished bytes. Coordinates are
 * stored in PdfKmp's top-left-origin space — the patcher flips Y once it knows
 * each page's height.
 *
 * One [AndroidPdfCanvas] is created per page; do not reuse a canvas across
 * pages.
 */
internal class AndroidPdfCanvas(
    private val canvas: Canvas,
    private val fontMetrics: AndroidFontMetrics,
    private val pageIndex: Int = 0,
    private val navigation: PdfNavigation = PdfNavigation(),
) : PdfCanvas {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        fontMetrics.applyStyle(textPaint, style)
        // PdfKmp positions text by its top-left corner; Android's drawText
        // takes a baseline. Offset by the font ascent to convert.
        val ascent = -textPaint.fontMetrics.ascent
        canvas.drawText(text, x, y + ascent, textPaint)
    }

    override fun drawRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
    ) {
        fillPaint.color = color.toArgb()
        canvas.drawRect(x, y, x + width, y + height, fillPaint)
    }

    override fun drawRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
    ) {
        fillPaint.color = color.toArgb()
        canvas.drawRoundRect(x, y, x + width, y + height, cornerRadius, cornerRadius, fillPaint)
    }

    override fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
        thickness: Float,
    ) {
        strokePaint.color = color.toArgb()
        strokePaint.strokeWidth = thickness
        canvas.drawRect(x, y, x + width, y + height, strokePaint)
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
        strokePaint.color = color.toArgb()
        strokePaint.strokeWidth = thickness
        canvas.drawRoundRect(x, y, x + width, y + height, cornerRadius, cornerRadius, strokePaint)
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
        strokePaint.color = color.toArgb()
        strokePaint.strokeWidth = thickness
        strokePaint.pathEffect = dashEffectFor(style, thickness)
        strokePaint.strokeCap = if (style == LineStyle.Dotted) Paint.Cap.ROUND else Paint.Cap.BUTT
        canvas.drawLine(x1, y1, x2, y2, strokePaint)
        // Reset so subsequent strokes default to solid.
        strokePaint.pathEffect = null
        strokePaint.strokeCap = Paint.Cap.BUTT
    }

    private fun dashEffectFor(style: LineStyle, thickness: Float): DashPathEffect? = when (style) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> DashPathEffect(floatArrayOf(thickness * 4f, thickness * 2f), 0f)
        // For dotted we draw zero-length segments with a round cap, which
        // produces actual circular dots rather than tiny rectangles.
        LineStyle.Dotted -> DashPathEffect(floatArrayOf(0f, thickness * 2f), 0f)
    }

    override fun rotate(degrees: Float, pivotX: Float, pivotY: Float) {
        canvas.rotate(degrees, pivotX, pivotY)
    }

    override fun beginTransparencyGroup(alpha: Float) {
        // saveLayerAlpha composites everything drawn until the matching
        // restore() at the group alpha — true group transparency.
        canvas.saveLayerAlpha(null, (alpha.coerceIn(0f, 1f) * 255f).toInt())
    }

    override fun endTransparencyGroup() {
        canvas.restore()
    }

    override fun saveState() {
        canvas.save()
    }

    override fun restoreState() {
        canvas.restore()
    }

    override fun clipRect(x: Float, y: Float, width: Float, height: Float) {
        canvas.clipRect(x, y, x + width, y + height)
    }

    override fun clipRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        val path = android.graphics.Path()
        path.addRoundRect(
            android.graphics.RectF(x, y, x + width, y + height),
            cornerRadius,
            cornerRadius,
            android.graphics.Path.Direction.CW,
        )
        canvas.clipPath(path)
    }

    override fun clipPath(commands: List<PathCommand>) {
        if (commands.isEmpty()) return
        val path = android.graphics.Path()
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> path.moveTo(cmd.x, cmd.y)
                is PathCommand.LineTo -> path.lineTo(cmd.x, cmd.y)
                is PathCommand.CubicTo -> path.cubicTo(cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
                is PathCommand.QuadTo -> path.quadTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
                PathCommand.Close -> path.close()
            }
        }
        canvas.clipPath(path)
    }

    override fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    ) {
        if (commands.isEmpty()) return
        if (fill == null && (strokeColor == null || strokeWidth <= 0f)) return
        val path = android.graphics.Path()
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> path.moveTo(cmd.x, cmd.y)
                is PathCommand.LineTo -> path.lineTo(cmd.x, cmd.y)
                is PathCommand.CubicTo -> path.cubicTo(cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
                is PathCommand.QuadTo -> path.quadTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
                PathCommand.Close -> path.close()
            }
        }
        if (fill != null) {
            applyFillPaint(fillPaint, fill)
            canvas.drawPath(path, fillPaint)
            fillPaint.shader = null
        }
        if (strokeColor != null && strokeWidth > 0f) {
            strokePaint.color = strokeColor.toArgb()
            strokePaint.strokeWidth = strokeWidth
            canvas.drawPath(path, strokePaint)
        }
    }

    /**
     * Configures [paint]'s colour or shader to match the requested
     * [PdfPaint]. Solid paints use [Paint.color]; gradients construct an
     * Android [Shader] sized to the path's coordinate space.
     */
    private fun applyFillPaint(paint: Paint, fill: PdfPaint) {
        when (fill) {
            is PdfPaint.Solid -> {
                paint.shader = null
                paint.color = fill.color.toArgb()
            }
            is PdfPaint.LinearGradient -> {
                paint.color = android.graphics.Color.BLACK
                paint.shader = LinearGradient(
                    fill.startX, fill.startY,
                    fill.endX, fill.endY,
                    fill.stops.map { it.color.toArgb() }.toIntArray(),
                    fill.stops.map { it.offset }.toFloatArray(),
                    Shader.TileMode.CLAMP,
                )
            }
            is PdfPaint.RadialGradient -> {
                paint.color = android.graphics.Color.BLACK
                paint.shader = RadialGradient(
                    fill.centerX, fill.centerY, fill.radius,
                    fill.stops.map { it.color.toArgb() }.toIntArray(),
                    fill.stops.map { it.offset }.toFloatArray(),
                    Shader.TileMode.CLAMP,
                )
            }
        }
    }

    override fun linkAnnotation(x: Float, y: Float, width: Float, height: Float, url: String) {
        if (width <= 0f || height <= 0f) return
        navigation.links += PdfLink(pageIndex, x, y, width, height, url = url)
    }

    override fun namedDestination(name: String, y: Float) {
        navigation.destinations += PdfDestination(name, pageIndex, y)
    }

    override fun linkToDestination(name: String, x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        // Forward references are fine: the patcher resolves anchor names against
        // the full destination list after every page has rendered.
        navigation.links += PdfLink(pageIndex, x, y, width, height, anchor = name)
    }

    override fun bookmark(title: String, level: Int, y: Float) {
        navigation.bookmarks += PdfBookmark(title, level, pageIndex, y)
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
        allowDownScale: Boolean,
        // Android's PdfDocument writes no tagged structure, so the
        // accessibility description has nowhere to go — accepted but ignored.
        altText: String?,
    ) {
        val bitmap = decodeBitmap(
            bytes = bytes,
            dstWidthPoints = width,
            dstHeightPoints = height,
            allowDownScale = allowDownScale,
        )
        if (bitmap == null) {
            PdfLog.warn("drawImage skipped: ${bytes.size}-byte payload is not a decodable image (Android backend)")
            return
        }
        val srcTopPx = (bitmap.height * sourceTop.coerceIn(0f, 1f)).toInt()
        val srcBottomPx = (bitmap.height * sourceBottom.coerceIn(0f, 1f)).toInt()
            .coerceAtLeast(srcTopPx + 1)
        val srcSliceWidth = bitmap.width
        val srcSliceHeight = srcBottomPx - srcTopPx
        val srcRect = Rect(0, srcTopPx, srcSliceWidth, srcBottomPx)
        val dstRect = applyContentScale(
            scale = contentScale,
            srcWidth = srcSliceWidth.toFloat(),
            srcHeight = srcSliceHeight.toFloat(),
            dstX = x,
            dstY = y,
            dstWidth = width,
            dstHeight = height,
        )
        if (contentScale == ContentScale.Crop) {
            // Crop reduces the visible source to keep aspect ratio while
            // still filling the destination. Recompute srcRect to match.
            val cropped = cropSourceForFill(srcRect, dstRect)
            canvas.drawBitmap(bitmap, cropped, dstRect, imagePaint)
        } else {
            canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
        }
    }
}

/**
 * Decodes [bytes] into a [Bitmap], subsampling the source so its pixel
 * dimensions stay in line with the rendered size at 200 DPI *and* inside
 * [PdfImagePolicy.maxDecodePixels].
 *
 * The two-pass `inJustDecodeBounds` + `inSampleSize` flow lets the platform
 * decoder skip every other 2/4/8 row at decode time — so a 4000×3000
 * smartphone photo destined for a 2-inch thumbnail decodes straight into a
 * ~250×190 buffer instead of allocating ~48 MB up front.
 *
 * Every decode goes through the bounds pass first: a request that needs no
 * DPI-driven reduction still gets the budget's reduction, so a hostile header
 * can never reach the decoder unsampled. Only an image whose header the
 * platform cannot size at all falls through unsampled — nothing is known about
 * it to sample by, and the decoder is then the last line of defense.
 */
internal fun decodeBitmap(
    bytes: ByteArray,
    dstWidthPoints: Float,
    dstHeightPoints: Float,
    allowDownScale: Boolean,
): Bitmap? {
    if (bytes.isEmpty()) return null
    val sizeOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sizeOnly)
    val srcW = sizeOnly.outWidth
    val srcH = sizeOnly.outHeight
    if (srcW <= 0 || srcH <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    var sample = 1
    if (allowDownScale && dstWidthPoints > 0f && dstHeightPoints > 0f) {
        val target = targetPixelDimensions(dstWidthPoints, dstHeightPoints)
        while (srcW / (sample * 2) >= target.first && srcH / (sample * 2) >= target.second) {
            sample *= 2
        }
    }
    // A huge draw size, a disabled down-scale, or a hostile header can each
    // leave the allocation above the budget — take whichever reduction is
    // stronger, the DPI-driven one or the budget's. Only the budget's is worth
    // reporting: DPI sampling is the normal, intended path for every photo.
    val budgetSample = decodeSampleFactorFor(srcW, srcH)
    if (budgetSample > 1) {
        PdfLog.warn(
            "drawImage: ${srcW}x$srcH source sampled down by $budgetSample to fit the " +
                "${PdfImagePolicy.maxDecodePixels}-pixel decode budget (Android backend)",
        )
    }
    sample = maxOf(sample, budgetSample)
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    // A sampled decode can fail on a format the unsampled one handles. Retrying
    // full-size is safe only when DPI, not the budget, asked for the reduction —
    // retrying a budget-driven sample would reopen the allocation it prevented.
    if (bitmap != null || budgetSample > 1) return bitmap
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

/**
 * Computes the destination [RectF] that respects [scale] for an image of
 * intrinsic size `srcWidth × srcHeight` drawn into
 * `(dstX, dstY, dstWidth, dstHeight)`.
 *
 * For `Fit` and `Crop` the returned rectangle is fully inside the requested
 * destination; for `FillBounds` it equals the destination as-is.
 */
private fun applyContentScale(
    scale: ContentScale,
    srcWidth: Float,
    srcHeight: Float,
    dstX: Float,
    dstY: Float,
    dstWidth: Float,
    dstHeight: Float,
): RectF {
    if (srcWidth <= 0f || srcHeight <= 0f) {
        return RectF(dstX, dstY, dstX + dstWidth, dstY + dstHeight)
    }
    val srcAspect = srcWidth / srcHeight
    val dstAspect = if (dstHeight == 0f) srcAspect else dstWidth / dstHeight
    return when (scale) {
        ContentScale.FillBounds, ContentScale.Crop ->
            RectF(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

        ContentScale.Fit -> if (srcAspect > dstAspect) {
            // Source is wider than destination — fit width, letterbox vertically.
            val drawHeight = dstWidth / srcAspect
            val offset = (dstHeight - drawHeight) / 2f
            RectF(dstX, dstY + offset, dstX + dstWidth, dstY + offset + drawHeight)
        } else {
            // Source is taller than destination — fit height, pillarbox horizontally.
            val drawWidth = dstHeight * srcAspect
            val offset = (dstWidth - drawWidth) / 2f
            RectF(dstX + offset, dstY, dstX + offset + drawWidth, dstY + dstHeight)
        }
    }
}

/**
 * For `ContentScale.Crop`, returns the source rectangle that, after being
 * stretched into [dst], fills the destination while preserving its aspect
 * ratio. The crop is taken from the centre of [src].
 */
private fun cropSourceForFill(src: Rect, dst: RectF): Rect {
    val srcAspect = src.width().toFloat() / src.height().toFloat()
    val dstAspect = if (dst.height() == 0f) srcAspect else dst.width() / dst.height()
    return if (srcAspect > dstAspect) {
        // Source is wider — crop horizontally.
        val targetWidth = (src.height() * dstAspect).toInt().coerceAtLeast(1)
        val padding = (src.width() - targetWidth) / 2
        Rect(src.left + padding, src.top, src.left + padding + targetWidth, src.bottom)
    } else {
        // Source is taller — crop vertically.
        val targetHeight = (src.width() / dstAspect).toInt().coerceAtLeast(1)
        val padding = (src.height() - targetHeight) / 2
        Rect(src.left, src.top + padding, src.right, src.top + padding + targetHeight)
    }
}
