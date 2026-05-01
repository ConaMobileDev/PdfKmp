package com.conamobile.pdfkmp.render

import kotlin.math.ceil

/**
 * Target DPI used by every platform backend when downscaling raster images
 * embedded through `image(bytes = …, allowDownScale = true)` (the default).
 *
 * 200 DPI is the sweet spot for an A4-targeted PDF: it stays crisp on
 * retina displays and laser printers, preserves fine line work in technical
 * drawings and sketches, and is roughly 4× cheaper than 300 DPI in heap and
 * file size. A full A4 page at 200 DPI is 1654×2339 px — partial-page
 * embeds (the common case) need far less. Override behaviour by passing
 * `allowDownScale = false` at the call site.
 */
internal const val DEFAULT_TARGET_DPI: Int = 200

/**
 * Returns the maximum source-pixel dimensions a raster image needs in
 * order to be drawn at [widthPoints] × [heightPoints] PDF points without
 * losing sharpness at [DEFAULT_TARGET_DPI]. Backends use this to decide
 * how aggressively to subsample the source bitmap before drawing.
 */
internal fun targetPixelDimensions(widthPoints: Float, heightPoints: Float): IntPair {
    val widthPx = ceil(widthPoints * DEFAULT_TARGET_DPI / 72f).toInt().coerceAtLeast(1)
    val heightPx = ceil(heightPoints * DEFAULT_TARGET_DPI / 72f).toInt().coerceAtLeast(1)
    return IntPair(widthPx, heightPx)
}

/**
 * Lightweight pair of ints — kept here instead of using [Pair] so the helper
 * does not allocate boxed `Integer` instances on each call from the hot
 * rendering path.
 */
internal data class IntPair(val first: Int, val second: Int)
