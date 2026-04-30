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
    ): TableCellNode = TableCellNode(
        content = ColumnNode(listOf(content)),
        style = TableCellStyle(padding = padding),
    )
}
