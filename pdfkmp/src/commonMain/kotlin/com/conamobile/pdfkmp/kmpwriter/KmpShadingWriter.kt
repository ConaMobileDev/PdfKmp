package com.conamobile.pdfkmp.kmpwriter

/**
 * Serialises a [KmpShadingDef] into a PDF shading dictionary — the pure-Kotlin
 * analogue of the PdfBox `PDShadingType2` / `PDShadingType3` the JVM backend
 * builds, hand-written so it needs no PDF library.
 *
 * The colour transition is a PDF function embedded inline in the shading dict:
 *
 * - **Two stops** → a single type-2 exponential interpolation (linear, N=1).
 * - **Three or more stops** → a type-3 stitching function chaining one type-2
 *   segment per adjacent stop pair, with constant leading/trailing clamp
 *   segments when the first/last offsets aren't 0/1. This reproduces
 *   `Shader.TileMode.CLAMP` (Android) / `kCGGradientDrawsBefore/After` (iOS) so
 *   a gradient authored with non-0/1 endpoints renders identically everywhere,
 *   matching the established JVM `buildColorFunction` behaviour.
 *
 * `/Extend [true true]` paints the clamp colours beyond the axis endpoints.
 * Stops are treated as opaque; a uniform per-stop alpha is folded into a
 * constant graphics-state alpha by the canvas before the shading is painted.
 */
internal object KmpShadingWriter {

    fun serialize(def: KmpShadingDef): String {
        val function = colorFunction(def.colors, def.offsets)
        val coords = def.coords.joinToString(" ") { PdfSyntax.formatNumber(it) }
        return if (def.axial) {
            "<< /ShadingType 2 /ColorSpace /DeviceRGB /Coords [$coords] " +
                "/Function $function /Extend [true true] >>"
        } else {
            // Radial: a two-circle (type-3) shading from a zero-radius circle at
            // the centre to the full-radius circle, matching the JVM backend.
            val cx = PdfSyntax.formatNumber(def.coords[0])
            val cy = PdfSyntax.formatNumber(def.coords[1])
            val r = PdfSyntax.formatNumber(def.coords[2])
            "<< /ShadingType 3 /ColorSpace /DeviceRGB /Coords [$cx $cy 0 $cx $cy $r] " +
                "/Function $function /Extend [true true] >>"
        }
    }

    /**
     * Builds the colour function `t ∈ [0,1] → RGB`. A single stop collapses to a
     * constant; two stops use a lone type-2 segment; more use a type-3 stitch.
     */
    private fun colorFunction(colors: List<FloatArray>, offsets: FloatArray): String {
        if (colors.isEmpty()) return type2(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f))
        if (colors.size == 1) return type2(colors[0], colors[0])
        if (colors.size == 2 && offsets.first() <= 0f && offsets.last() >= 1f) {
            return type2(colors[0], colors[1])
        }

        val first = offsets.first().coerceIn(0f, 1f)
        val last = offsets.last().coerceIn(0f, 1f)

        val functions = ArrayList<String>()
        val bounds = ArrayList<Float>()
        val encode = ArrayList<Float>()

        fun addSegment(c0: FloatArray, c1: FloatArray) {
            functions.add(type2(c0, c1))
            encode.add(0f)
            encode.add(1f)
        }

        if (first > 0f) {
            // Constant clamp from t=0 to the first stop.
            addSegment(colors.first(), colors.first())
            bounds.add(first)
        }
        for (i in 0 until colors.size - 1) {
            addSegment(colors[i], colors[i + 1])
            if (i < colors.size - 2) bounds.add(offsets[i + 1].coerceIn(0f, 1f))
        }
        if (last < 1f) {
            bounds.add(last)
            addSegment(colors.last(), colors.last())
        }

        val funcArray = functions.joinToString(" ")
        val boundsArray = bounds.joinToString(" ") { PdfSyntax.formatNumber(it) }
        val encodeArray = encode.joinToString(" ") { PdfSyntax.formatNumber(it) }
        return "<< /FunctionType 3 /Domain [0 1] /Functions [$funcArray] " +
            "/Bounds [$boundsArray] /Encode [$encodeArray] >>"
    }

    /** A type-2 exponential interpolation function from [c0] to [c1] (linear, N=1). */
    private fun type2(c0: FloatArray, c1: FloatArray): String {
        val c0s = c0.joinToString(" ") { PdfSyntax.formatNumber(it) }
        val c1s = c1.joinToString(" ") { PdfSyntax.formatNumber(it) }
        return "<< /FunctionType 2 /Domain [0 1] /C0 [$c0s] /C1 [$c1s] /N 1 >>"
    }
}
