package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint

/**
 * Resolved, inheritable SVG presentation state.
 *
 * SVG paint properties (`fill`, `stroke`, opacities, …) cascade from a
 * `<g>` to its descendants. Modelling the resolved state as an immutable
 * value type lets a child cheaply derive its own state from the parent's
 * with [inherit] and makes the walk in [VectorParser] purely functional.
 *
 * A `null` paint/colour means "not set at this level, keep inheriting";
 * an explicit `none` keyword resolves to a [PaintSpec.None] so the child
 * can distinguish "inherit" from "explicitly no paint".
 */
internal data class SvgStyle(
    val fill: PaintSpec,
    val stroke: PaintSpec,
    val strokeWidth: Float?,
    val opacity: Float,
    val fillOpacity: Float,
    val strokeOpacity: Float,
) {

    /**
     * Combines this style with the attributes/inline-style declared on a
     * single element, producing the resolved style for that element and its
     * descendants. Values present on the element override the inherited
     * ones; absent values keep the parent's.
     */
    fun inherit(
        fill: PaintSpec?,
        stroke: PaintSpec?,
        strokeWidth: Float?,
        opacity: Float?,
        fillOpacity: Float?,
        strokeOpacity: Float?,
    ): SvgStyle = SvgStyle(
        fill = fill ?: this.fill,
        stroke = stroke ?: this.stroke,
        strokeWidth = strokeWidth ?: this.strokeWidth,
        // `opacity` is a group/element multiplier and does NOT inherit as a
        // raw value — but for the flat single-paint model we collapse it by
        // multiplying down the tree, which matches what users expect for
        // simple icons.
        opacity = this.opacity * (opacity ?: 1f),
        fillOpacity = fillOpacity ?: this.fillOpacity,
        strokeOpacity = strokeOpacity ?: this.strokeOpacity,
    )

    /**
     * Resolves the effective fill [PdfPaint] for a leaf shape, folding the
     * `fill-opacity` and element `opacity` into the colour's alpha. Returns
     * `null` when the fill is `none` or an unresolved gradient reference
     * could not be found.
     */
    fun resolveFill(gradients: Map<String, PdfPaint>): PdfPaint? = when (fill) {
        PaintSpec.None -> null
        is PaintSpec.Color -> PdfPaint.Solid(fill.color.scaledAlpha(fillOpacity * opacity))
        is PaintSpec.Ref -> gradients[fill.id]
        // Unset fill defaults to black per the SVG spec.
        PaintSpec.Unset -> PdfPaint.Solid(PdfColor.Black.scaledAlpha(fillOpacity * opacity))
    }

    /**
     * Resolves the effective stroke colour, folding `stroke-opacity` and
     * element `opacity` into the alpha. Returns `null` when there is no
     * stroke (the SVG default is `stroke: none`).
     */
    fun resolveStroke(): PdfColor? = when (stroke) {
        is PaintSpec.Color -> stroke.color.scaledAlpha(strokeOpacity * opacity)
        // Unset / None / gradient-ref strokes all render as no stroke: the
        // core model only supports solid stroke colours.
        else -> null
    }

    /** Stroke width to use, defaulting to the SVG default of `1` when stroking. */
    fun resolveStrokeWidth(): Float = strokeWidth ?: 1f

    companion object {
        /** SVG initial values: black fill, no stroke, full opacity. */
        val Root: SvgStyle = SvgStyle(
            fill = PaintSpec.Unset,
            stroke = PaintSpec.Unset,
            strokeWidth = null,
            opacity = 1f,
            fillOpacity = 1f,
            strokeOpacity = 1f,
        )
    }
}

/** Multiplies the alpha channel, clamped to `0f..1f`. */
private fun PdfColor.scaledAlpha(factor: Float): PdfColor =
    copy(alpha = (alpha * factor).coerceIn(0f, 1f))

/**
 * A `fill` / `stroke` value: an explicit colour, a gradient reference, the
 * `none` keyword, or "not specified at this level".
 */
internal sealed interface PaintSpec {
    /** No value declared — keep inheriting / fall back to the SVG default. */
    data object Unset : PaintSpec

    /** Explicit `none` / `transparent` — paint nothing. */
    data object None : PaintSpec

    /** A resolved solid colour. */
    data class Color(val color: PdfColor) : PaintSpec

    /** A `url(#id)` reference, resolved against the gradient table at draw time. */
    data class Ref(val id: String) : PaintSpec
}

/**
 * Parses a `fill` / `stroke` attribute (or inline-style value) into a
 * [PaintSpec]. Returns `null` when [value] is absent so the caller can keep
 * inheriting.
 */
internal fun parsePaintSpec(value: String?): PaintSpec? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    if (lower == "none" || lower == "transparent") return PaintSpec.None
    if (lower.startsWith("url(")) {
        val end = trimmed.indexOf(')')
        if (end < 0) return null
        var ref = trimmed.substring(4, end).trim()
        if (ref.startsWith("#")) ref = ref.substring(1)
        return if (ref.isEmpty()) null else PaintSpec.Ref(ref)
    }
    return SvgColor.parse(trimmed)?.let(PaintSpec::Color)
}

/**
 * Parses an inline `style="prop:val; prop:val"` CSS string into a flat
 * property map (lower-cased keys, trimmed values). Empty / malformed
 * fragments are skipped rather than rejected — browsers do the same.
 */
internal fun parseInlineStyle(style: String?): Map<String, String> {
    if (style.isNullOrBlank()) return emptyMap()
    val out = mutableMapOf<String, String>()
    for (declaration in style.split(';')) {
        val colon = declaration.indexOf(':')
        if (colon <= 0) continue
        val key = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
    }
    return out
}

/**
 * Reads a presentation property from an element, giving an inline
 * `style="…"` declaration precedence over the matching presentation
 * attribute (this mirrors CSS specificity, where inline style wins).
 */
internal fun XmlElement.presentation(name: String, inlineStyle: Map<String, String>): String? =
    inlineStyle[name] ?: attribute(name)

/**
 * Builds the resolved [SvgStyle] for [element] by layering its own
 * presentation attributes / inline style over the [parent] style.
 */
internal fun resolveStyle(element: XmlElement, parent: SvgStyle): SvgStyle {
    val inline = parseInlineStyle(element.attribute("style"))
    return parent.inherit(
        fill = parsePaintSpec(element.presentation("fill", inline)),
        stroke = parsePaintSpec(element.presentation("stroke", inline)),
        strokeWidth = element.presentation("stroke-width", inline)?.let(::parseLengthOrNull),
        opacity = element.presentation("opacity", inline)?.let(::parseOpacity),
        fillOpacity = element.presentation("fill-opacity", inline)?.let(::parseOpacity),
        strokeOpacity = element.presentation("stroke-opacity", inline)?.let(::parseOpacity),
    )
}

/** Parses an opacity value (`0..1`), clamped; `null` when unparseable. */
private fun parseOpacity(value: String): Float? {
    val trimmed = value.trim()
    val raw = if (trimmed.endsWith("%")) {
        (trimmed.dropLast(1).toFloatOrNull() ?: return null) / 100f
    } else {
        trimmed.toFloatOrNull() ?: return null
    }
    return raw.coerceIn(0f, 1f)
}

/** Parses a length, stripping a trailing unit; `null` when there is no number. */
internal fun parseLengthOrNull(value: String): Float? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val numberEnd = trimmed.indexOfFirst { !it.isDigit() && it != '.' && it != '-' && it != '+' && it != 'e' && it != 'E' }
    val numericPart = if (numberEnd < 0) trimmed else trimmed.substring(0, numberEnd)
    return numericPart.toFloatOrNull()
}
