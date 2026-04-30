package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.TableCellNode
import com.conamobile.pdfkmp.node.TableRowNode
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableCellStyle
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp

/**
 * Receiver of `table { ... }`.
 *
 * Adds rows to the table. Use [header] for the optional header row (which
 * receives a default light-gray background and bold text style) and [row]
 * for body rows. Iterate a list of model objects with `forEach { row { ... } }`
 * to populate the table from a data source.
 */
@PdfDsl
public class TableScope internal constructor(
    private val parentTextStyle: TextStyle,
    private val defaultCellPadding: Padding,
) {
    internal var header: TableRowNode? = null
    internal val rows: MutableList<TableRowNode> = mutableListOf()

    /**
     * Adds a header row.
     *
     * Header cells default to a bold text style and a light-gray
     * [background]. Both are overridable inside the [block] (per-cell) or
     * via the parameters here (whole row).
     *
     * Calling [header] more than once replaces the previous header.
     */
    public fun header(
        background: PdfColor? = PdfColor.LightGray,
        minHeight: Dp? = null,
        block: TableRowScope.() -> Unit,
    ) {
        val headerStyle = parentTextStyle.copy(
            fontWeight = com.conamobile.pdfkmp.style.FontWeight.Bold,
        )
        val scope = TableRowScope(headerStyle, defaultCellPadding).apply(block)
        header = TableRowNode(
            cells = scope.cells.toList(),
            background = background,
            minHeight = minHeight,
        )
    }

    /**
     * Adds a body row.
     *
     * @param background optional fill drawn behind the row. Use
     *   `if (index % 2 == 0) PdfColor.White else PdfColor.LightGray` for
     *   zebra striping.
     * @param cellPadding row-level override on top of the table-wide cell
     *   padding. `null` keeps the table default.
     * @param minHeight forces the row to be at least this tall.
     */
    public fun row(
        background: PdfColor? = null,
        cellPadding: Padding? = null,
        minHeight: Dp? = null,
        block: TableRowScope.() -> Unit,
    ) {
        val padding = cellPadding ?: defaultCellPadding
        val scope = TableRowScope(parentTextStyle, padding).apply(block)
        rows += TableRowNode(
            cells = scope.cells.toList(),
            background = background,
            minHeight = minHeight,
        )
    }
}

/**
 * Receiver of `header { ... }` and `row { ... }` inside a [TableScope].
 *
 * Adds [TableCellNode]s to the row in column order. Two convenience [cell]
 * overloads exist: a string-only one for plain text cells and a fully-
 * configurable one that opens a [ContainerScope] block.
 */
@PdfDsl
public class TableRowScope internal constructor(
    private val parentTextStyle: TextStyle,
    private val defaultCellPadding: Padding,
) {
    internal val cells: MutableList<TableCellNode> = mutableListOf()

    /**
     * Adds a configurable cell whose contents are described inside [block].
     *
     * Inside [block] you have a normal [ContainerScope] — you can stack
     * multiple [text]s, embed an [image], or even nest a [row] / [column].
     */
    public fun cell(
        verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
        horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
        background: PdfColor? = null,
        padding: Padding? = null,
        block: ContainerScope.() -> Unit,
    ) {
        val cellScope = TableCellContentScope(parentTextStyle).apply(block)
        cells += TableCellNode(
            content = ColumnNode(cellScope.children.toList()),
            style = TableCellStyle(
                padding = padding ?: defaultCellPadding,
                verticalAlignment = verticalAlignment,
                horizontalAlignment = horizontalAlignment,
                background = background,
            ),
        )
    }

    /**
     * Adds a simple text cell.
     *
     * Equivalent to `cell { text(value, block) }` but keeps the call site
     * readable when the cell contents are a single short label.
     */
    public fun cell(
        value: String,
        verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
        horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
        background: PdfColor? = null,
        padding: Padding? = null,
        textBlock: TextScope.() -> Unit = {},
    ) {
        cell(
            verticalAlignment = verticalAlignment,
            horizontalAlignment = horizontalAlignment,
            background = background,
            padding = padding,
        ) {
            text(value, textBlock)
        }
    }
}

/**
 * Container scope used internally for the contents of a single table cell.
 *
 * The cell's content can be anything a normal column can hold (text,
 * images, nested layouts), so this is just a thin alias around
 * [ContainerScope] that participates in the [PdfDsl] receiver hierarchy.
 */
@PdfDsl
private class TableCellContentScope(textStyle: TextStyle) : ContainerScope(textStyle)
