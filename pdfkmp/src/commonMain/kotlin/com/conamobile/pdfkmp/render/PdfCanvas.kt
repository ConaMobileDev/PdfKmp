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

    /**
     * Registers a named destination at the given vertical position on the
     * current page. Internal links created with [linkToDestination]
     * (possibly on other pages, before or after this one) jump here.
     *
     * Backends without navigation support (Android `PdfDocument`) default
     * to a no-op.
     */
    public fun namedDestination(name: String, y: Float): Unit = Unit

    /**
     * Records an internal go-to link covering the given rectangle and
     * jumping to the [namedDestination] registered under [name]. Forward
     * references are fine — backends resolve names when the document is
     * finished. Links whose name is never registered are silently inert.
     */
    public fun linkToDestination(
        name: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ): Unit = Unit

    /**
     * Adds an entry to the document's outline (the bookmark sidebar in
     * PDF readers) pointing at the given vertical position on the current
     * page. [level] nests entries: `0` is a top-level chapter, `1` a
     * section inside the previous level-0 entry, and so on.
     *
     * Backends without outline support (Android `PdfDocument`) default to
     * a no-op.
     */
    public fun bookmark(title: String, level: Int, y: Float): Unit = Unit

    /**
     * Rotates all subsequent drawing by [degrees] (clockwise, in the
     * top-left coordinate space) around the pivot point. Effective until
     * the next [restoreState] — always wrap in [saveState] /
     * [restoreState] pairs, which is what the renderer does for rotated
     * containers.
     */
    public fun rotate(degrees: Float, pivotX: Float, pivotY: Float): Unit = Unit

    /**
     * Starts a transparency group: subsequent drawing is composited at
     * [alpha] opacity until the matching [endTransparencyGroup]. Backends
     * without group support default to a no-op (content draws opaque).
     */
    public fun beginTransparencyGroup(alpha: Float): Unit = Unit

    /** Closes the group opened by [beginTransparencyGroup]. */
    public fun endTransparencyGroup(): Unit = Unit

    /**
     * Draws a bitmap, optionally embedding only a vertical window of the
     * source — `sourceTop` / `sourceBottom` are normalized (0..1) offsets
     * used by the page-break slicer to continue a tall image across pages.
     *
     * @param allowDownScale when `true` (default), the backend subsamples
     *   the source bitmap so its pixel dimensions roughly match the
     *   destination at 200 DPI before drawing. Pass `false` to feed every
     *   source pixel through the platform decoder.
     * @param altText accessibility description of the image. When non-null
     *   and the backend writes tagged structure (the JVM backend wraps the
     *   draw in a `/Figure` marked-content sequence carrying `/Alt`), screen
     *   readers can describe the picture. Backends without tagging ignore it.
     */
    public fun drawImage(
        bytes: ByteArray,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        contentScale: ContentScale,
        sourceTop: Float = 0f,
        sourceBottom: Float = 1f,
        allowDownScale: Boolean = true,
        altText: String? = null,
    )

    /**
     * Records an interactive AcroForm text input field covering the given
     * rectangle.
     *
     * Only backends with an AcroForm API honour this (the JVM/Desktop
     * PdfBox backend creates a real `PDTextField`). Android and iOS default
     * to a no-op — the renderer has already drawn a static visual fallback
     * so the box is still visible there, just not editable.
     *
     * @param fontSizePt font size, in points, for the field's default
     *   appearance — matches the static fallback so the two look alike.
     */
    public fun formTextField(
        name: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        value: String,
        multiline: Boolean,
        fontSizePt: Float,
    ): Unit = Unit

    /**
     * Records an interactive AcroForm checkbox covering the given square.
     *
     * Like [formTextField], only AcroForm-capable backends (JVM/Desktop)
     * create a real `PDCheckBox`; Android and iOS default to a no-op and
     * rely on the static visual square the renderer drew.
     */
    public fun formCheckBox(
        name: String,
        x: Float,
        y: Float,
        size: Float,
        checked: Boolean,
    ): Unit = Unit
}
