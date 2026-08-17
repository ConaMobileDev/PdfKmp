package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.Constraints
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the row/column measurement and arrangement logic in
 * [LayoutEngine]. Each test uses fixed-size [SpacerNode]s as children so
 * the assertions can be exact pixel-perfect comparisons.
 */
class RowColumnLayoutTest {

    private val metrics = FixedWidthFontMetrics()

    @Test
    fun row_packsChildrenLeftToRight() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 30.dp, height = 10.dp),
                SpacerNode(width = 50.dp, height = 10.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
            ),
        )
        val measured = measure(node, Constraints(maxWidth = 200f), metrics) as MeasuredRow

        assertEquals(listOf(0f, 30f, 80f), measured.children.map { it.offsetX })
        assertEquals(10f, measured.size.height)
    }

    @Test
    fun row_spaceBetween_packsChildrenToEdges() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 10.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
            ),
            horizontalArrangement = HorizontalArrangement.SpaceBetween,
        )
        val measured = measure(node, Constraints(maxWidth = 200f), metrics) as MeasuredRow

        // 200 - 60 = 140 of slack split into 2 gaps of 70.
        assertEquals(listOf(0f, 90f, 180f), measured.children.map { it.offsetX })
    }

    @Test
    fun row_centerArrangement_groupsChildrenInTheMiddle() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 10.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
            ),
            horizontalArrangement = HorizontalArrangement.Center,
        )
        val measured = measure(node, Constraints(maxWidth = 100f), metrics) as MeasuredRow

        // 100 - 40 = 60 / 2 = 30 leading pad.
        assertEquals(listOf(30f, 50f), measured.children.map { it.offsetX })
    }

    @Test
    fun row_weightedChildren_shareRemainingWidth() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 40.dp, height = 10.dp),
                WeightNode(weight = 1f, child = SpacerNode(width = 0.dp, height = 10.dp)),
                WeightNode(weight = 2f, child = SpacerNode(width = 0.dp, height = 10.dp)),
            ),
        )
        val measured = measure(node, Constraints(maxWidth = 130f), metrics) as MeasuredRow

        // Remaining = 130 - 40 = 90; 1:2 split → 30 and 60.
        assertEquals(40f, measured.children[0].node.size.width)
        assertEquals(30f, measured.children[1].node.size.width)
        assertEquals(60f, measured.children[2].node.size.width)
    }

    @Test
    fun row_verticalCenterAlignment_centersShorterChildren() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 40.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
            ),
            verticalAlignment = VerticalAlignment.Center,
        )
        val measured = measure(node, Constraints(maxWidth = 200f), metrics) as MeasuredRow

        assertEquals(0f, measured.children[0].offsetY)
        assertEquals(15f, measured.children[1].offsetY) // (40 - 10) / 2
    }

    @Test
    fun column_packsChildrenTopToBottom_withSpacing() {
        val node = ColumnNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 30.dp),
                SpacerNode(width = 20.dp, height = 50.dp),
            ),
            spacing = 10.dp,
        )
        val measured = measure(node, Constraints(maxWidth = 200f), metrics) as MeasuredColumn

        assertEquals(listOf(0f, 40f), measured.children.map { it.offsetY })
        assertEquals(90f, measured.size.height) // 30 + 10 + 50
    }

    @Test
    fun column_horizontalCenterAlignment_centersNarrowerChildren() {
        val node = ColumnNode(
            children = listOf(
                SpacerNode(width = 100.dp, height = 10.dp),
                SpacerNode(width = 40.dp, height = 10.dp),
            ),
            horizontalAlignment = HorizontalAlignment.Center,
        )
        val measured = measure(node, Constraints(maxWidth = 200f), metrics) as MeasuredColumn

        // Column width = widest child = 100.
        // Narrower child centered: (100 - 40) / 2 = 30.
        assertEquals(0f, measured.children[0].offsetX)
        assertEquals(30f, measured.children[1].offsetX)
    }

    @Test
    fun column_weightedChildren_shareRemainingHeight() {
        val node = ColumnNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 30.dp),
                WeightNode(weight = 1f, child = SpacerNode(width = 20.dp, height = 0.dp)),
                WeightNode(weight = 1f, child = SpacerNode(width = 20.dp, height = 0.dp)),
            ),
        )
        val measured = measure(
            node,
            Constraints(maxWidth = 100f, maxHeight = 130f),
            metrics,
        ) as MeasuredColumn

        // Remaining = 130 - 30 = 100; 1:1 split → 50 each.
        assertEquals(30f, measured.children[0].node.size.height)
        assertEquals(50f, measured.children[1].node.size.height)
        assertEquals(50f, measured.children[2].node.size.height)
        // Offsets: 0, 30, 80.
        assertEquals(listOf(0f, 30f, 80f), measured.children.map { it.offsetY })
    }

    @Test
    fun row_spaceEvenly_distributesGapsEqually() {
        val node = RowNode(
            children = listOf(
                SpacerNode(width = 20.dp, height = 10.dp),
                SpacerNode(width = 20.dp, height = 10.dp),
            ),
            horizontalArrangement = HorizontalArrangement.SpaceEvenly,
        )
        val measured = measure(node, Constraints(maxWidth = 100f), metrics) as MeasuredRow

        // Gap = (100 - 40) / 3 = 20.
        assertEquals(20f, measured.children[0].offsetX)
        assertEquals(60f, measured.children[1].offsetX) // 20 + 20 + 20
    }

    @Test
    fun nestedRow_insideColumn_keepsLocalCoordinates() {
        val node = ColumnNode(
            children = listOf(
                RowNode(
                    children = listOf(
                        SpacerNode(width = 30.dp, height = 10.dp),
                        SpacerNode(width = 30.dp, height = 10.dp),
                    ),
                ),
                SpacerNode(width = 60.dp, height = 20.dp),
            ),
        )
        val measured = measure(node, Constraints(maxWidth = 100f), metrics) as MeasuredColumn

        val innerRow = measured.children[0].node as MeasuredRow
        assertEquals(listOf(0f, 30f), innerRow.children.map { it.offsetX })
        assertEquals(0f, measured.children[0].offsetY)
        assertEquals(10f, measured.children[1].offsetY)
        assertTrue(measured.size.width >= 60f)
    }

    @Test
    fun row_verticalPadding_isSubtractedFromChildHeightConstraint() {
        val node = RowNode(
            children = listOf(
                ColumnNode(
                    children = listOf(
                        WeightNode(weight = 1f, child = SpacerNode(width = 10.dp, height = 10.dp)),
                    ),
                ),
            ),
            decoration = ContainerDecoration(padding = Padding.symmetric(vertical = 25.dp)),
        )
        val measured = measure(node, Constraints(maxWidth = 200f, maxHeight = 300f), metrics)

        assertEquals(
            300f,
            measured.size.height,
            "row height ${measured.size.height} must not exceed the 300pt constraint — " +
                "the weighted column child must be measured at the padding-adjusted height",
        )
    }

    @Test
    fun column_weightedTextChild_wrapsAtPaddingAdjustedWidth() {
        val text = "a".repeat(35)
        val node = ColumnNode(
            children = listOf(
                WeightNode(weight = 1f, child = TextNode(text = text, style = TextStyle(fontSize = 10.sp))),
            ),
            decoration = ContainerDecoration(padding = Padding.symmetric(horizontal = 50.dp)),
        )
        val measured = measure(node, Constraints(maxWidth = 400f, maxHeight = 1000f), metrics) as MeasuredColumn

        val inner = measured.children.single().node as MeasuredText
        assertEquals(2, inner.lines.size, "350pt of text must wrap at the 300pt padded slot, not the raw 400pt")
        assertTrue(measured.size.width <= 400f, "column width ${measured.size.width} must not exceed the 400pt constraint")
    }
}
