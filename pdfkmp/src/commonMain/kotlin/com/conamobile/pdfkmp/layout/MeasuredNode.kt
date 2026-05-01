package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableCellStyle
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.VectorImage

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
) : MeasuredNode

/** Measurement result for a fixed-size block (e.g. a spacer). */
public data class MeasuredBlock(
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
    val style: com.conamobile.pdfkmp.style.LineStyle,
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
) : MeasuredNode {
    override fun equals(other: Any?): Boolean =
        other is MeasuredImage &&
            other.contentScale == contentScale &&
            other.size == size &&
            other.allowDownScale == allowDownScale &&
            other.bytes.contentEquals(bytes)

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentScale.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + allowDownScale.hashCode()
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
    val shape: com.conamobile.pdfkmp.node.Shape,
    val fill: com.conamobile.pdfkmp.style.PdfPaint?,
    val strokeColor: com.conamobile.pdfkmp.style.PdfColor?,
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
 */
public data class MeasuredTable(
    val columnWidths: List<Float>,
    val rows: List<MeasuredTableRow>,
    val border: TableBorder,
    val borderColor: PdfColor,
    val borderWidth: Float,
    val cornerRadius: Float,
    override val size: Size,
) : MeasuredNode

/** One measured row in a [MeasuredTable]. */
public data class MeasuredTableRow(
    val height: Float,
    val cells: List<MeasuredTableCell>,
    val background: PdfColor?,
    val isHeader: Boolean,
)

/** One measured cell in a [MeasuredTableRow]. */
public data class MeasuredTableCell(
    val content: MeasuredNode,
    val style: TableCellStyle,
    /** Top-left x-offset of the cell, relative to the table's top-left. */
    val offsetX: Float,
    /** Width of the cell (= column width). */
    val width: Float,
    /** Top-left y-offset within the row's interior (always 0 unless we add row sub-padding). */
    val contentOffsetX: Float,
    val contentOffsetY: Float,
)
