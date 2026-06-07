package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.barcode.Code128Barcode
import com.conamobile.pdfkmp.barcode.DataMatrix
import com.conamobile.pdfkmp.barcode.QrMatrix
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.Shape
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableCellStyle
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.VectorImage

/**
 * One word positioned by full justification. The layout engine pre-computes
 * the x-offset of every word so the renderer can draw justified lines with
 * one `drawText` call per word and zero extra math.
 */
public data class JustifiedWord(
    /** Characters of this word (no surrounding spaces). */
    val text: String,
    /** X-offset of the word's left edge relative to the line's left edge. */
    val x: Float,
    /** Advance width of [text] at the line's style. */
    val width: Float,
)

/**
 * One word-wrapped segment of text plus the position of its baseline relative
 * to the top of the surrounding [MeasuredText] block.
 */
public data class TextLine(
    /** The actual characters that make up this line. */
    val text: String,
    /** Advance width of [text] at the configured style, in PDF points. */
    val width: Float,
    /** Distance from the top of the line box to the baseline, in PDF points. */
    val baseline: Float,
    /** Total height occupied by this line, including ascent + descent + line gap. */
    val height: Float,
    /**
     * Word slices with pre-computed x-offsets when this line is fully
     * justified ([com.conamobile.pdfkmp.style.TextAlign.Justify]); empty
     * for every other alignment and for the last line of a paragraph,
     * which stays start-aligned by typographic convention.
     */
    val justifiedWords: List<JustifiedWord> = emptyList(),
)

/**
 * Output of measuring a layout node. Consumed by the renderer when placing
 * the node onto a page.
 */
public sealed interface MeasuredNode {
    /** Final size of the node within its parent. */
    public val size: Size
}

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.TextNode].
 *
 * @property paragraphWidth the maximum width the layout engine had
 *   available when wrapping the text. Used by the renderer to apply
 *   `TextAlign` — `Center`/`End`/`Justify` need to know the full
 *   paragraph slot, not just the widest measured line.
 */
public data class MeasuredText(
    val lines: List<TextLine>,
    val style: TextStyle,
    override val size: Size,
    val paragraphWidth: Float = size.width,
    /**
     * Paragraph direction with [com.conamobile.pdfkmp.style.TextDirection.Auto]
     * already resolved against the content. RTL paragraphs flip what
     * `TextAlign.Start` / `End` anchor to.
     */
    val resolvedDirection: TextDirection = TextDirection.Ltr,
) : MeasuredNode

/** Measurement result for a fixed-size block (e.g. a spacer). */
public data class MeasuredBlock(
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.FormTextFieldNode].
 *
 * Carries the resolved name/value plus the font size the static fallback and
 * the JVM widget's default appearance should both use, so the visual box and
 * the interactive overlay stay in sync.
 */
public data class MeasuredFormTextField(
    val name: String,
    val value: String,
    val multiline: Boolean,
    val fontSizePt: Float,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.FormCheckBoxNode] —
 * always a square of `size × size`.
 */
public data class MeasuredFormCheckBox(
    val name: String,
    val checked: Boolean,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.DividerNode].
 *
 * Carries the resolved stroke configuration so the renderer can hand it
 * straight to [com.conamobile.pdfkmp.render.PdfCanvas.drawLine] or its
 * dashed counterpart.
 */
public data class MeasuredDivider(
    val thickness: Float,
    val color: PdfColor,
    val style: LineStyle,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for an image node.
 *
 * Carries the encoded [bytes] alongside the resolved destination size and
 * [contentScale] so the renderer can hand them straight to the platform
 * canvas without having to walk the original [com.conamobile.pdfkmp.node.ImageNode]
 * tree again.
 */
public data class MeasuredImage(
    val bytes: ByteArray,
    val contentScale: ContentScale,
    override val size: Size,
    val allowDownScale: Boolean = true,
    /** Accessibility description forwarded from [com.conamobile.pdfkmp.node.ImageNode.altText]. */
    val altText: String? = null,
) : MeasuredNode {
    override fun equals(other: Any?): Boolean =
        other is MeasuredImage &&
            other.contentScale == contentScale &&
            other.size == size &&
            other.allowDownScale == allowDownScale &&
            other.altText == altText &&
            other.bytes.contentEquals(bytes)

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentScale.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + allowDownScale.hashCode()
        result = 31 * result + (altText?.hashCode() ?: 0)
        return result
    }
}

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.ColumnNode].
 *
 * Children are stored as [PlacedChild]s with `(offsetX, offsetY)` pre-
 * computed relative to the column's top-left corner so the renderer
 * does not have to redo arrangement math.
 */
public data class MeasuredColumn(
    val children: List<PlacedChild>,
    override val size: Size,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.RowNode]. Mirrors
 * [MeasuredColumn] but stacks children left-to-right.
 */
public data class MeasuredRow(
    val children: List<PlacedChild>,
    override val size: Size,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.BoxNode]. Children
 * are stored with their pre-computed `(offsetX, offsetY)` so the renderer
 * does not need to redo alignment math at draw time.
 */
public data class MeasuredBox(
    val children: List<PlacedChild>,
    override val size: Size,
    val decoration: ContainerDecoration = ContainerDecoration.None,
) : MeasuredNode

/**
 * A child node positioned within a [MeasuredColumn] or [MeasuredRow].
 *
 * Both axes carry a relative offset; the renderer adds the column/row's own
 * top-left when it places the child onto a page.
 */
public data class PlacedChild(
    val node: MeasuredNode,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.LinkNode]. The
 * wrapper simply forwards [child]'s size — the URL is attached to the
 * surrounding rectangle at draw time.
 */
public data class MeasuredLink(
    val url: String,
    val child: MeasuredNode,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.ShapeNode].
 *
 * The shape's path is generated at draw time inside [size] — we do not
 * pre-compute it during measurement so that nothing has to be re-layout
 * when the shape gets stretched by a weighted slot.
 */
public data class MeasuredShape(
    val shape: Shape,
    val fill: PdfPaint?,
    val strokeColor: PdfColor?,
    val strokeWidth: Float,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.VectorNode].
 *
 * The renderer scales [image]'s viewport into [size] preserving the
 * configured aspect ratio; the optional [tint] overrides every path's
 * fill colour at draw time.
 */
public data class MeasuredVector(
    val image: VectorImage,
    val tint: PdfColor?,
    val strokeOverride: VectorStrokeMode,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.TableNode].
 *
 * Stores enough resolved geometry — column widths, row heights, per-cell
 * sub-trees with offsets — that the renderer can stroke borders, fill row
 * and cell backgrounds, and place every cell's content with no further
 * arithmetic.
 *
 * The header row, when present, is always at index 0 of [rows].
 *
 * @property repeatHeader when the `Slice` strategy splits this table
 *   across pages, repeat the header row on each continuation page.
 */
public data class MeasuredTable(
    val columnWidths: List<Float>,
    val rows: List<MeasuredTableRow>,
    val border: TableBorder,
    val borderColor: PdfColor,
    val borderWidth: Float,
    val cornerRadius: Float,
    override val size: Size,
    val repeatHeader: Boolean = true,
    /**
     * Occupancy grid: `cellOwners[rowIndex][columnIndex]` is a stable id of
     * the cell that paints that grid slot. Slots covered by the same spanned
     * cell share an id; every other slot (including empty fillers) gets its
     * own unique id so the renderer still strokes separators around them.
     *
     * The renderer derives per-segment separator lines from this grid so an
     * inner border never crosses a merged (col/row-spanned) region. Empty by
     * default for backward compatibility; always populated by the layout
     * engine.
     */
    val cellOwners: List<List<Int>> = emptyList(),
) : MeasuredNode

/**
 * One measured row in a [MeasuredTable].
 *
 * [cells] holds only the cells that *start* in this row (spanned cells appear
 * once, in their top row); slots covered by a cell from an earlier row or an
 * earlier column are not repeated here. Each cell carries its own
 * [MeasuredTableCell.columnIndex] so the renderer can place it without
 * re-deriving the grid.
 */
public data class MeasuredTableRow(
    val height: Float,
    val cells: List<MeasuredTableCell>,
    val background: PdfColor?,
    val isHeader: Boolean,
)

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.QrCodeNode]. The
 * module matrix is computed during measurement (pure common code) so the
 * renderer only has to turn dark modules into filled rectangles.
 */
public data class MeasuredQrCode(
    val matrix: QrMatrix,
    val color: PdfColor,
    val background: PdfColor?,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.BarcodeNode].
 * Carries the encoded bar/space module widths so the renderer can emit
 * the bars without re-running the encoder.
 */
public data class MeasuredBarcode(
    val barcode: Code128Barcode,
    val color: PdfColor,
    val background: PdfColor?,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.DataMatrixNode]. The
 * module matrix is computed during measurement (pure common code) so the
 * renderer only has to turn dark modules into filled rectangles, exactly like
 * [MeasuredQrCode].
 */
public data class MeasuredDataMatrix(
    val matrix: DataMatrix,
    val color: PdfColor,
    val background: PdfColor?,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.BookmarkNode] —
 * zero-size; the renderer registers the outline entry at its position.
 */
public data class MeasuredBookmark(
    val title: String,
    val level: Int,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for an [com.conamobile.pdfkmp.node.AnchorNode] —
 * zero-size; the renderer registers the named destination at its position.
 */
public data class MeasuredAnchor(
    val id: String,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for an [com.conamobile.pdfkmp.node.InternalLinkNode].
 * Mirrors [MeasuredLink] but jumps to a named destination instead of a URL.
 */
public data class MeasuredInternalLink(
    val anchorId: String,
    val child: MeasuredNode,
    override val size: Size,
) : MeasuredNode

/**
 * Measurement result for a [com.conamobile.pdfkmp.node.KeepTogetherNode] —
 * forwards the child's size; the renderer refuses to slice it.
 */
public data class MeasuredKeepTogether(
    val child: MeasuredNode,
    override val size: Size,
) : MeasuredNode

/** One measured cell in a [MeasuredTableRow]. */
public data class MeasuredTableCell(
    val content: MeasuredNode,
    val style: TableCellStyle,
    /** Top-left x-offset of the cell, relative to the table's top-left. */
    val offsetX: Float,
    /** Width of the cell (= sum of the [colSpan] column widths). */
    val width: Float,
    /** Top-left y-offset within the row's interior (always 0 unless we add row sub-padding). */
    val contentOffsetX: Float,
    val contentOffsetY: Float,
    /** Grid column this cell starts at (its left edge). */
    val columnIndex: Int = 0,
    /** Number of columns the cell occupies (>= 1). */
    val colSpan: Int = 1,
    /** Number of rows the cell occupies (>= 1). */
    val rowSpan: Int = 1,
    /**
     * Drawn height of the cell — the sum of the heights of the [rowSpan] rows
     * it covers. Equals the starting row's height for non-spanning cells, so
     * existing single-row behaviour is unchanged.
     */
    val spannedHeight: Float = 0f,
)
