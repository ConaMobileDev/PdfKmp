package com.conamobile.pdfkmp.style

import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.unit.Dp

/**
 * Width specification for one column of a [com.conamobile.pdfkmp.dsl.table].
 *
 * Use [Fixed] when the column has an explicit pixel width and [Weight] when
 * the column should share the remaining space proportionally with other
 * weighted columns. Mixing both kinds in the same table is supported —
 * fixed widths are reserved first, then the remainder is distributed across
 * weighted columns.
 */
public sealed interface TableColumn {

    /** Reserves an explicit, immutable [width] for the column. */
    public data class Fixed(val width: Dp) : TableColumn

    /**
     * Lets the column grow into a share of the remaining table width. With
     * two columns of weight `1f` and `2f`, the second receives twice the
     * space of the first after fixed-width columns are accounted for.
     */
    public data class Weight(val weight: Float = 1f) : TableColumn {
        init {
            require(weight > 0f) { "weight must be > 0 (got $weight)" }
        }
    }
}

/**
 * Border configuration for a [com.conamobile.pdfkmp.dsl.table].
 *
 * The border consists of three independently togglable parts: the outer
 * [showOutline], the [showHorizontalLines] separating rows, and the
 * [showVerticalLines] separating columns. All three share the same [color]
 * and [width].
 *
 * Set [width] to `Dp.Zero` (or any of the toggles to `false`) to suppress a
 * particular line. Use [None] to disable borders entirely.
 */
public data class TableBorder(
    val color: PdfColor = PdfColor.Gray,
    val width: Dp = Dp(0.5f),
    val showOutline: Boolean = true,
    val showHorizontalLines: Boolean = true,
    val showVerticalLines: Boolean = true,
) {
    public companion object {
        /** No borders anywhere — useful when the design relies on row backgrounds for separation. */
        public val None: TableBorder = TableBorder(
            width = Dp.Zero,
            showOutline = false,
            showHorizontalLines = false,
            showVerticalLines = false,
        )
    }
}

/**
 * Per-cell visual style. Defaults are sensible for body cells; the
 * [com.conamobile.pdfkmp.dsl.TableScope.header] DSL applies its own
 * sensible defaults on top.
 *
 * @property padding inset between the cell border and the content.
 * @property verticalAlignment how the cell content is positioned along the
 *   row's vertical axis when the row is taller than the content.
 * @property horizontalAlignment how the cell content is positioned along
 *   the column's horizontal axis when the column is wider than the
 *   content.
 * @property background optional fill drawn behind the cell content.
 *   Overrides the row-level background for this cell only.
 */
public data class TableCellStyle(
    val padding: Padding = Padding.all(Dp(8f)),
    val verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    val background: PdfColor? = null,
)
