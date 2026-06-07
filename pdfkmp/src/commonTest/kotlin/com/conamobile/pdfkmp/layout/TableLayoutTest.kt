package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Constraints
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableCellNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TableRowNode
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableCellStyle
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [LayoutEngine]'s table-handling code path. */
class TableLayoutTest {

    private val metrics = FixedWidthFontMetrics()

    @Test
    fun overWideFixedColumns_shrinkProportionallyToFit() {
        val table = simpleTable(
            columns = listOf(
                TableColumn.Fixed(300.dp),
                TableColumn.Fixed(100.dp),
            ),
            rows = listOf(makeRow(cellHeight = 10f, cellCount = 2)),
        )
        // Slot is half the declared fixed total — widths scale by 0.5 so
        // the table never spills past the page margin.
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable

        assertEquals(listOf(150f, 50f), measured.columnWidths)
        assertEquals(200f, measured.size.width)
    }

    @Test
    fun fixedAndWeightedColumns_resolveToExpectedWidths() {
        val table = simpleTable(
            columns = listOf(
                TableColumn.Fixed(60.dp),
                TableColumn.Weight(1f),
                TableColumn.Weight(2f),
            ),
            rows = listOf(makeRow(cellHeight = 10f, cellCount = 3)),
        )
        val measured = measure(table, Constraints(maxWidth = 360f), metrics) as MeasuredTable

        assertEquals(listOf(60f, 100f, 200f), measured.columnWidths)
        assertEquals(360f, measured.size.width)
    }

    @Test
    fun rowHeight_isMaxOfCellHeights_plusPadding() {
        val padding = Padding.all(8.dp)
        val table = simpleTable(
            columns = listOf(TableColumn.Weight(1f), TableColumn.Weight(1f)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        cell(SpacerNode(width = 30.dp, height = 20.dp), padding = padding),
                        cell(SpacerNode(width = 30.dp, height = 50.dp), padding = padding),
                    ),
                ),
            ),
            cellPadding = padding,
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        // Row height = max(20, 50) + 8 (top) + 8 (bottom) = 66
        assertEquals(66f, measured.rows.first().height)
    }

    @Test
    fun headerRow_isFirstInOrder() {
        val table = TableNode(
            columns = listOf(TableColumn.Weight(1f)),
            rows = listOf(
                TableRowNode(cells = listOf(cell(SpacerNode(width = 50.dp, height = 12.dp)))),
            ),
            headerRow = TableRowNode(
                cells = listOf(cell(SpacerNode(width = 50.dp, height = 12.dp))),
                background = PdfColor.LightGray,
            ),
            border = TableBorder(),
            cornerRadius = 0.dp,
            cellPadding = Padding.Zero,
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable

        assertEquals(2, measured.rows.size)
        assertTrue(measured.rows[0].isHeader)
        assertEquals(PdfColor.LightGray, measured.rows[0].background)
        assertEquals(false, measured.rows[1].isHeader)
        assertNull(measured.rows[1].background)
    }

    @Test
    fun cellsExtendingBeyondColumnCount_areIgnored() {
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                        // The third cell should be ignored — only two columns.
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                    ),
                ),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        assertEquals(2, measured.rows.first().cells.size)
    }

    @Test
    fun missingTrailingCells_renderEmptyButPreserveColumnGeometry() {
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp)),
            rows = listOf(
                TableRowNode(cells = listOf(cell(SpacerNode(width = 10.dp, height = 10.dp)))),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        assertEquals(3, measured.rows.first().cells.size)
        assertEquals(listOf(0f, 40f, 80f), measured.rows.first().cells.map { it.offsetX })
    }

    @Test
    fun rowMinHeight_floors_rowHeight() {
        val table = simpleTable(
            columns = listOf(TableColumn.Weight(1f)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(cell(SpacerNode(width = 10.dp, height = 4.dp))),
                    minHeight = 80.dp,
                ),
            ),
            cellPadding = Padding.Zero,
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        assertEquals(80f, measured.rows.first().height)
    }

    private fun simpleTable(
        columns: List<TableColumn>,
        rows: List<TableRowNode>,
        cellPadding: Padding = Padding.Zero,
    ): TableNode = TableNode(
        columns = columns,
        rows = rows,
        headerRow = null,
        border = TableBorder(),
        cornerRadius = 0.dp,
        cellPadding = cellPadding,
    )

    private fun makeRow(cellHeight: Float, cellCount: Int) = TableRowNode(
        cells = List(cellCount) {
            cell(SpacerNode(width = 0.dp, height = cellHeight.dp))
        },
    )

    private fun cell(
        content: com.conamobile.pdfkmp.node.PdfNode,
        padding: Padding = Padding.Zero,
        colSpan: Int = 1,
        rowSpan: Int = 1,
    ): TableCellNode = TableCellNode(
        content = ColumnNode(listOf(content)),
        style = TableCellStyle(padding = padding),
        colSpan = colSpan,
        rowSpan = rowSpan,
    )

    @Test
    fun colSpan_cellTakesBothColumnWidths_andFollowingCellShiftsRight() {
        val table = simpleTable(
            columns = listOf(
                TableColumn.Fixed(40.dp),
                TableColumn.Fixed(40.dp),
                TableColumn.Fixed(40.dp),
            ),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        cell(SpacerNode(width = 10.dp, height = 10.dp), colSpan = 2),
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                    ),
                ),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        val cells = measured.rows.single().cells

        assertEquals(2, cells.size)
        // The colspan cell starts at column 0 and is two columns wide.
        assertEquals(0, cells[0].columnIndex)
        assertEquals(2, cells[0].colSpan)
        assertEquals(80f, cells[0].width)
        assertEquals(0f, cells[0].offsetX)
        // The following cell lands in column 3 (offset = first two widths).
        assertEquals(2, cells[1].columnIndex)
        assertEquals(80f, cells[1].offsetX)
        assertEquals(40f, cells[1].width)
    }

    @Test
    fun rowSpan_secondRowFirstCellLandsInColumnTwo() {
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        cell(SpacerNode(width = 10.dp, height = 10.dp), rowSpan = 2),
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                    ),
                ),
                TableRowNode(
                    cells = listOf(cell(SpacerNode(width = 10.dp, height = 10.dp))),
                ),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable

        val row0 = measured.rows[0].cells
        val row1 = measured.rows[1].cells
        // Row 0 holds the spanning cell (col 0) and a normal cell (col 1).
        assertEquals(listOf(0, 1), row0.map { it.columnIndex })
        assertEquals(2, row0[0].rowSpan)
        // Row 1 has only ONE cell — the rowspan occupies col 0, so its single
        // declared cell falls into column 1.
        assertEquals(1, row1.size)
        assertEquals(1, row1[0].columnIndex)
        assertEquals(40f, row1[0].offsetX)
    }

    @Test
    fun rowSpan_spannedCellDrawnHeightCoversBothRows() {
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        cell(SpacerNode(width = 10.dp, height = 10.dp), rowSpan = 2),
                        cell(SpacerNode(width = 10.dp, height = 20.dp)),
                    ),
                ),
                TableRowNode(
                    cells = listOf(cell(SpacerNode(width = 10.dp, height = 30.dp))),
                ),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        // Row 0 height = 20 (its non-spanning cell), row 1 height = 30.
        assertEquals(20f, measured.rows[0].height)
        assertEquals(30f, measured.rows[1].height)
        // The rowspan cell's drawn height covers both rows: 20 + 30 = 50.
        assertEquals(50f, measured.rows[0].cells[0].spannedHeight)
    }

    @Test
    fun tallRowSpanContent_growsBothRowsEqually() {
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(40.dp)),
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        // 100-tall content across two 10-tall rows: deficit 80
                        // splits 40 / 40 between the two rows.
                        cell(SpacerNode(width = 10.dp, height = 100.dp), rowSpan = 2),
                        cell(SpacerNode(width = 10.dp, height = 10.dp)),
                    ),
                ),
                TableRowNode(
                    cells = listOf(cell(SpacerNode(width = 10.dp, height = 10.dp))),
                ),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        assertEquals(50f, measured.rows[0].height)
        assertEquals(50f, measured.rows[1].height)
        assertEquals(100f, measured.rows[0].cells[0].spannedHeight)
    }

    @Test
    fun spansOfOne_matchPreSpanGeometryExactly() {
        // A plain 2×2 table must produce identical geometry whether or not
        // the span fields are touched — guards the "spans of 1 = today's
        // behavior" invariant.
        val table = simpleTable(
            columns = listOf(TableColumn.Fixed(40.dp), TableColumn.Fixed(60.dp)),
            rows = listOf(
                TableRowNode(cells = listOf(
                    cell(SpacerNode(width = 10.dp, height = 12.dp)),
                    cell(SpacerNode(width = 10.dp, height = 18.dp)),
                )),
                TableRowNode(cells = listOf(
                    cell(SpacerNode(width = 10.dp, height = 24.dp)),
                    cell(SpacerNode(width = 10.dp, height = 6.dp)),
                )),
            ),
        )
        val measured = measure(table, Constraints(maxWidth = 200f), metrics) as MeasuredTable
        assertEquals(listOf(40f, 60f), measured.columnWidths)
        assertEquals(18f, measured.rows[0].height)
        assertEquals(24f, measured.rows[1].height)
        assertEquals(listOf(0f, 40f), measured.rows[0].cells.map { it.offsetX })
        assertEquals(listOf(40f, 60f), measured.rows[0].cells.map { it.width })
        // Every cell carries the trivial span values.
        assertTrue(measured.rows.all { row -> row.cells.all { it.colSpan == 1 && it.rowSpan == 1 } })
    }
}
