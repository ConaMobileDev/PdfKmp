package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand

/**
 * Platform-agnostic 2D drawing surface for one PDF page.
 *
 * A [PdfCanvas] is obtained from [PdfDriver.beginPage] and is valid until the
 * matching [PdfDriver.endPage] call. All coordinates are in PDF points and
 * use a top-left origin (Y grows downward) — the platform implementations
 * take care of flipping where the native context uses bottom-left.
 *
 * The interface is deliberately small. New primitive operations should be
 * added here only when they cannot be expressed as a composition of
 * existing ones, and every addition must be implemented on every platform
 * backend.
 */
public interface PdfCanvas {

    /**
     * Draws a single line of [text] starting at the given top-left
     * position. Newline characters are not interpreted — pass already-
     * wrapped lines from the layout engine.
     */
    public fun drawText(
        text: String,
        x: Float,
        y: Float,
        style: TextStyle,
    )

    /** Fills an axis-aligned rectangle. */
    public fun drawRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
    )

    /**
     * Fills an axis-aligned rectangle whose four corners are rounded with
     * the given [cornerRadius]. A radius of zero is equivalent to
     * [drawRect].
     */
    public fun drawRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
    )

    /** Strokes an axis-aligned rectangle's outline. */
    public fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: PdfColor,
        thickness: Float,
    )

    /** Strokes an axis-aligned rounded-rectangle's outline. */
    public fun strokeRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: PdfColor,
        thickness: Float,
    )

    /**
     * Strokes a straight line between two points.
     *
     * @param style stroke pattern. [LineStyle.Solid] is the default and
     *   the only pattern that needs no setup. Dashed/dotted strokes
     *   produce repeating segments whose length is a multiple of
     *   [thickness] so the visual weight remains balanced as thickness
     *   scales.
     */
    public fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: PdfColor,
        thickness: Float,
        style: LineStyle = LineStyle.Solid,
    )

    /**
     * Pushes the current canvas state (transform, clip) onto an internal
     * stack. Pair every call with [restoreState]; mismatched pairs leak
     * state across draw calls and produce garbled output.
     */
    public fun saveState()

    /** Restores the canvas state most recently pushed with [saveState]. */
    public fun restoreState()

    /**
     * Constrains all subsequent drawing to the given rectangle. Effective
     * until the next [restoreState] call. Wrap calls in
     * [saveState] / [restoreState] to scope clipping to a region of code.
     */
    public fun clipRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    )

    /** Like [clipRect] but with rounded corners. */
    public fun clipRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    )

    /**
     * Constrains all subsequent drawing to the area inside [commands].
     * Used by container decoration when the corners cannot be expressed
     * as a single uniform [cornerRadius]. The path is closed implicitly;
     * winding rule is non-zero (the platform default).
     */
    public fun clipPath(commands: List<PathCommand>)

    /**
     * Draws a vector path defined by a sequence of [PathCommand]s.
     *
     * The platform translates each command into a native path and then
     * fills and / or strokes that path. The [fill] is a [PdfPaint] —
     * either a solid colour or a gradient. Pass `null` for [fill] to skip
     * filling and `null` for [strokeColor] (or `0f` for [strokeWidth]) to
     * skip stroking.
     */
    public fun drawPath(
        commands: List<PathCommand>,
        fill: PdfPaint?,
        strokeColor: PdfColor?,
        strokeWidth: Float,
    )

    /**
     * Records a hyperlink annotation covering the given rectangle and
     * pointing at [url].
     *
     * Implementations that produce real PDF annotations (iOS) attach a
     * clickable region to the page; implementations whose underlying
     * platform does not expose annotation APIs (Android `PdfDocument`)
     * default to a no-op so the surrounding visual styling still
     * conveys "this is a link" even if clicks fall through.
     */
    public fun linkAnnotation(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        url: String,
    ): Unit = Unit

    /** Vertically-sliced bitmap embed. See the deferred TODO inside the platform impls. */
    public fun drawImage(
        bytes: ByteArray,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        contentScale: ContentScale,
        sourceTop: Float = 0f,
        sourceBottom: Float = 1f,
    )
}
