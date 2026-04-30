package com.conamobile.pdfkmp.node

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.HorizontalArrangement
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.layout.VerticalArrangement
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableCellStyle
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.vector.VectorImage

/**
 * Resolved, immutable description of a document. Produced by the DSL builders
 * and consumed by the layout engine + renderer. Library users normally never
 * see this type — they go through `pdf { ... }` instead.
 */
public data class DocumentSpec(
    val metadata: PdfMetadata,
    val pages: List<PageSpec>,
    val customFonts: List<PdfFont.Custom>,
)

/**
 * One logical page in the source DSL. May expand into multiple physical PDF
 * pages at render time when content overflows.
 *
 * @property header optional builder invoked once per physical page; the
 *   resulting [ColumnNode] is rendered above the body content and
 *   excluded from the body's content area. Pure of side effects — the
 *   renderer may invoke it more than once.
 * @property footer same as [header] but rendered at the bottom of every
 *   physical page.
 * @property watermark optional [BoxNode] drawn behind every physical
 *   page's body content. Spans the full page area (not the content
 *   frame) so corner-anchored watermarks land in the page padding band
 *   rather than the content gutter.
 */
public data class PageSpec(
    val size: PageSize,
    val padding: Padding,
    val content: ColumnNode,
    val pageBreakStrategy: PageBreakStrategy,
    val header: ((PageContext) -> ColumnNode)? = null,
    val footer: ((PageContext) -> ColumnNode)? = null,
    val watermark: BoxNode? = null,
)

/**
 * Context passed to a page's header / footer builder. Lets the builder
 * vary the rendered content per physical page — typically to inject the
 * current page number or the document's total page count.
 *
 * @property pageNumber 1-based page number currently being rendered.
 * @property totalPages total number of physical pages produced by the
 *   document. The renderer determines this with a dry-run pass before the
 *   real render so this value is always accurate, even when pages slice
 *   into multiple physical pages.
 */
public data class PageContext(
    val pageNumber: Int,
    val totalPages: Int,
)

/**
 * Sealed hierarchy of layout nodes. Every DSL element produces one of these.
 *
 * New node types should be added here and given a corresponding branch in
 * [com.conamobile.pdfkmp.layout.LayoutEngine].
 */
public sealed interface PdfNode

public data class TextNode(
    val text: String,
    val style: TextStyle,
) : PdfNode

/**
 * Background, border, padding, and corner-radius decoration shared by
 * every container ([ColumnNode], [RowNode], [BoxNode]).
 *
 * Drawing order at render time is:
 *
 * 1. Fill the rectangle with [background] (if non-null).
 * 2. Place children inside the rectangle inset by [padding].
 * 3. Stroke the outer rectangle with [border] (if non-null).
 *
 * When [cornerRadius] is non-zero the rectangle becomes rounded — both
 * the fill and the border outline follow the curve, and the children are
 * clipped to the rounded shape so they don't poke past the corners.
 */
public data class ContainerDecoration(
    val background: PdfColor? = null,
    val cornerRadius: Dp = Dp.Zero,
    val padding: Padding = Padding.Zero,
    val border: BorderStroke? = null,
    /**
     * Per-corner override. When non-null takes precedence over [cornerRadius].
     * Use [com.conamobile.pdfkmp.style.CornerRadius.Companion.top] etc. for
     * common shapes.
     */
    val cornerRadiusEach: com.conamobile.pdfkmp.style.CornerRadius? = null,
    /**
     * Per-side override. When non-null takes precedence over [border]. Each
     * side is independent — leave any `null` to skip it.
     */
    val borderEach: com.conamobile.pdfkmp.style.BorderSides? = null,
    /**
     * Optional gradient or paint fill. When non-null takes precedence over
     * [background]. Gradient coordinates are in the container's local
     * coordinate space — `(0, 0)` is the top-left corner, the container's
     * `(width, height)` is the bottom-right.
     */
    val backgroundPaint: com.conamobile.pdfkmp.style.PdfPaint? = null,
    /**
     * When `true`, the renderer clips drawing to the container's
     * rectangle. Useful for fixed-size containers (e.g. `box(width =
     * 200.dp, height = 80.dp)`) where overflowing children should not
     * paint past the edges. Containers with a non-zero corner radius
     * already clip to their rounded shape regardless of this flag.
     */
    val clipToBounds: Boolean = false,
) {
    public companion object {
        public val None: ContainerDecoration = ContainerDecoration()
    }
}

public data class ColumnNode(
    val children: List<PdfNode>,
    val spacing: Dp = Dp.Zero,
    val verticalArrangement: VerticalArrangement = VerticalArrangement.Top,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : PdfNode

public data class RowNode(
    val children: List<PdfNode>,
    val spacing: Dp = Dp.Zero,
    val horizontalArrangement: HorizontalArrangement = HorizontalArrangement.Start,
    val verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : PdfNode

/**
 * Z-stacking container. Children are drawn in source order — first added
 * appears at the bottom, last on top. Each child is positioned at one of
 * the nine [BoxAlignment] anchor points within the box's interior.
 *
 * Boxes can be sized explicitly via [width] / [height] or wrap their
 * largest child when those are `null`. They support the same decoration
 * vocabulary as columns and rows (background, border, corner radius,
 * padding).
 */
public data class BoxNode(
    val children: List<BoxChild>,
    val width: Dp? = null,
    val height: Dp? = null,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : PdfNode

/** A single child inside a [BoxNode] together with its anchor point. */
public data class BoxChild(
    val node: PdfNode,
    val alignment: BoxAlignment,
)

/**
 * Vector graphic — an SVG / Android `<vector>` icon embedded into the
 * document at the requested rendered size.
 *
 * The renderer scales [image]'s viewport into the destination rectangle
 * preserving aspect ratio when one of [width] / [height] is `null`. When
 * both are `null` the [VectorImage.intrinsicWidth] / [VectorImage.intrinsicHeight]
 * are used (Compose-style fallback).
 *
 * @property image parsed vector graphic.
 * @property width rendered width on the page; `null` derives from the
 *   intrinsic aspect ratio when [height] is given.
 * @property height rendered height on the page; same fallback rules.
 * @property tint optional colour that overrides every path's fill.
 *   Useful when the same icon needs to render in different brand colours
 *   without re-authoring the XML.
 * @property strokeOverride controls how the parsed stroke colour from the
 *   source XML is treated at draw time. See [VectorStrokeMode] for the
 *   options.
 */
public data class VectorNode(
    val image: VectorImage,
    val width: Dp?,
    val height: Dp?,
    val tint: PdfColor? = null,
    val strokeOverride: VectorStrokeMode = VectorStrokeMode.Inherit,
) : PdfNode

/**
 * Per-vector stroke override applied at draw time. Lets the caller drop or
 * recolour stroke outlines without re-authoring the source XML.
 */
public sealed interface VectorStrokeMode {
    /** Use whatever stroke (or absence of stroke) is in the source XML. */
    public data object Inherit : VectorStrokeMode

    /** Drop the stroke entirely; fill colours from the XML still apply. */
    public data object Disabled : VectorStrokeMode

    /**
     * Replace the parsed stroke colour with [color] for every path,
     * keeping the XML's stroke width. If a path has no stroke in the
     * source it stays without a stroke.
     */
    public data class Tint(val color: PdfColor) : VectorStrokeMode
}

/**
 * Tabular layout: a fixed sequence of columns and a stream of rows whose
 * cells line up underneath them.
 *
 * @property columns specification of every column's width.
 * @property rows ordered list of body rows. The header row, if any, is
 *   stored at [headerRow].
 * @property headerRow optional decorated row drawn at the top with its own
 *   default styling. Kept separate so layout / rendering can apply
 *   header-specific decorations.
 * @property border outline and separator-line configuration.
 * @property cornerRadius outer rectangle corner radius. Set to `Dp.Zero`
 *   for sharp corners.
 * @property cellPadding default padding applied to every cell unless the
 *   cell overrides it.
 */
public data class TableNode(
    val columns: List<TableColumn>,
    val rows: List<TableRowNode>,
    val headerRow: TableRowNode?,
    val border: TableBorder,
    val cornerRadius: Dp,
    val cellPadding: Padding,
) : PdfNode

/**
 * One physical row of a [TableNode]. The number of [cells] should match
 * the number of [TableNode.columns]; if a row has fewer cells the trailing
 * columns render as empty, and extra cells are ignored.
 *
 * @property cells the cells of this row in column order.
 * @property background optional fill drawn behind the entire row. Useful
 *   for zebra striping (`if (index % 2 == 0) light else dark`).
 * @property minHeight optional floor on the row height; forces the row to
 *   be at least this tall even if all of its cells fit in less space.
 */
public data class TableRowNode(
    val cells: List<TableCellNode>,
    val background: PdfColor? = null,
    val minHeight: Dp? = null,
)

/**
 * One cell of a [TableRowNode].
 *
 * The cell's content is itself a small [ColumnNode] subtree so the user can
 * stack multiple text blocks (e.g. a title + a subtitle) inside one cell.
 *
 * @property content node tree that fills the cell's interior.
 * @property style cell-level overrides on top of the table-wide
 *   [TableNode.cellPadding] and default alignment.
 */
public data class TableCellNode(
    val content: PdfNode,
    val style: TableCellStyle,
)

/**
 * Wrapper that gives [child] a fractional share of the parent container's
 * remaining space along the main axis.
 *
 * Inside a [RowNode] the weight applies to width: every weighted child
 * receives `(weight / sumOfWeights) × (rowWidth − fixedWidth)`. Inside a
 * [ColumnNode] the weight applies to height in exactly the same way. The
 * minimum sensible weight is `1f`; values smaller than `1f` are still
 * honoured proportionally.
 */
public data class WeightNode(
    val weight: Float,
    val child: PdfNode,
) : PdfNode

public data class SpacerNode(
    val width: Dp = Dp.Zero,
    val height: Dp = Dp.Zero,
) : PdfNode

/**
 * Horizontal divider rendered as a single horizontal stroke spanning the
 * available width.
 *
 * @property thickness stroke width.
 * @property color stroke colour.
 * @property style solid / dashed / dotted pattern.
 */
public data class DividerNode(
    val thickness: Dp,
    val color: PdfColor,
    val style: com.conamobile.pdfkmp.style.LineStyle,
) : PdfNode

/**
 * A bitmap image embedded in the document.
 *
 * Width and height are optional: leave one or both `null` and the layout
 * engine derives the missing dimension from the image's intrinsic aspect
 * ratio (sniffed from the encoded header — see
 * [com.conamobile.pdfkmp.image.readImageInfo]). When both are `null`, the
 * intrinsic pixel size is used as-is, mapped 1px → 1pt.
 *
 * Supported formats are whatever the platform decoder accepts: PNG and JPEG
 * everywhere, plus WebP / HEIF on platforms that have native support
 * (Android 10+ for HEIF, iOS 11+ for HEIF and WebP).
 */
public data class ImageNode(
    val bytes: ByteArray,
    val width: Dp?,
    val height: Dp?,
    val contentScale: ContentScale,
) : PdfNode {
    override fun equals(other: Any?): Boolean =
        other is ImageNode &&
            other.width == width &&
            other.height == height &&
            other.contentScale == contentScale &&
            other.bytes.contentEquals(bytes)

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (width?.hashCode() ?: 0)
        result = 31 * result + (height?.hashCode() ?: 0)
        result = 31 * result + contentScale.hashCode()
        return result
    }
}
