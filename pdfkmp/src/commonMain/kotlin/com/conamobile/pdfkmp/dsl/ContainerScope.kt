package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.HorizontalArrangement
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.layout.VerticalArrangement
import com.conamobile.pdfkmp.node.BoxChild
import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.DividerNode
import com.conamobile.pdfkmp.node.ImageNode
import com.conamobile.pdfkmp.node.LinkNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.RichTextNode
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.Shape
import com.conamobile.pdfkmp.node.ShapeNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.VectorNode
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.vector.VectorImage

/**
 * Common parent of every scope that can hold child layout nodes (page body,
 * column, row).
 *
 * The scope tracks the inherited [textStyle] so descendants pick up its
 * properties without explicit threading. Children are appended in source
 * order via [text], [column], [row], [spacer], [image], or [weighted].
 */
@PdfDsl
public abstract class ContainerScope internal constructor(
    /**
     * Default text style applied to every [text] call inside this scope unless
     * the call's configuration block overrides individual properties.
     */
    public var textStyle: TextStyle,
) {

    internal val children: MutableList<PdfNode> = mutableListOf()

    /**
     * Appends a custom [PdfNode] to this container's child list.
     *
     * Reserved for integration modules that define their own node
     * shapes — for example `:pdfkmp-compose-resources` enqueues a
     * [com.conamobile.pdfkmp.node.LazyNode] through this hook so the
     * core DSL doesn't have to know about Compose Multiplatform
     * Resources. End-user code never needs to call this directly; use
     * [text], [column], [row], [image], [vector], etc.
     */
    public fun addNode(node: PdfNode) {
        children += node
    }

    /**
     * Appends a text node.
     *
     * @param value the string to render; line breaks (`\n`) split into hard lines.
     * @param block configures style overrides on top of the inherited [textStyle].
     */
    public fun text(value: String, block: TextScope.() -> Unit = {}) {
        val scope = TextScope(textStyle).apply(block)
        children += TextNode(value, scope.build())
    }

    /**
     * Appends a multi-style paragraph. Lets a single paragraph mix bold,
     * italic, coloured, or otherwise differently styled segments without
     * splitting into separate text blocks.
     *
     * Example:
     * ```
     * richText {
     *     span("This sentence has a ")
     *     span("highlighted") { color = PdfColor.Red; bold = true }
     *     span(" word and an ")
     *     span("italic phrase") { italic = true }
     *     span(" inside it.")
     * }
     * ```
     *
     * The block runs against a [RichTextScope]; every [RichTextScope.span]
     * call adds one styled run, and the renderer wraps them all together.
     */
    public fun richText(block: RichTextScope.() -> Unit) {
        val scope = RichTextScope(textStyle).apply(block)
        children += RichTextNode(
            spans = scope.spans.toList(),
            align = scope.align,
            lineHeight = scope.lineHeight,
        )
    }

    /**
     * Appends a vertical container that stacks its children top-to-bottom.
     *
     * Decoration parameters ([background], [cornerRadius], [padding],
     * [border]) wrap the column with a coloured background and / or
     * outline. Pass `null` (default) to skip them and keep the column
     * undecorated.
     *
     * @param spacing extra vertical gap inserted between adjacent children.
     *   Ignored when [verticalArrangement] is one of the `Space*` values.
     * @param verticalArrangement how the children are distributed along
     *   the column's vertical axis.
     * @param horizontalAlignment cross-axis alignment for children that
     *   are narrower than the column.
     * @param background optional fill drawn behind the column.
     * @param cornerRadius radius of the rounded outline for [background] /
     *   [border]. `Dp.Zero` keeps sharp corners.
     * @param padding inset between the column outline and the children.
     * @param border optional outline drawn around the column.
     */
    public fun column(
        spacing: Dp = Dp.Zero,
        verticalArrangement: VerticalArrangement = VerticalArrangement.Top,
        horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
        background: PdfColor? = null,
        cornerRadius: Dp = Dp.Zero,
        padding: Padding = Padding.Zero,
        border: BorderStroke? = null,
        cornerRadiusEach: CornerRadius? = null,
        borderEach: BorderSides? = null,
        backgroundPaint: PdfPaint? = null,
        clipToBounds: Boolean = false,
        block: ColumnScope.() -> Unit,
    ) {
        val scope = ColumnScope(textStyle).apply(block)
        children += ColumnNode(
            children = scope.children.toList(),
            spacing = spacing,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            decoration = ContainerDecoration(
                background = background,
                cornerRadius = cornerRadius,
                padding = padding,
                border = border,
                cornerRadiusEach = cornerRadiusEach,
                borderEach = borderEach,
                backgroundPaint = backgroundPaint,
                clipToBounds = clipToBounds,
            ),
        )
    }

    /**
     * Appends a horizontal container that lays its children left-to-right.
     *
     * Same decoration parameters ([background], [cornerRadius], [padding],
     * [border]) as [column].
     *
     * @param spacing extra horizontal gap inserted between adjacent
     *   children.
     * @param horizontalArrangement how the children are distributed along
     *   the row's horizontal axis.
     * @param verticalAlignment cross-axis alignment for children that are
     *   shorter than the row.
     * @param background optional fill drawn behind the row.
     * @param cornerRadius radius of the rounded outline.
     * @param padding inset between the row outline and the children.
     * @param border optional outline drawn around the row.
     */
    public fun row(
        spacing: Dp = Dp.Zero,
        horizontalArrangement: HorizontalArrangement = HorizontalArrangement.Start,
        verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
        background: PdfColor? = null,
        cornerRadius: Dp = Dp.Zero,
        padding: Padding = Padding.Zero,
        border: BorderStroke? = null,
        cornerRadiusEach: CornerRadius? = null,
        borderEach: BorderSides? = null,
        backgroundPaint: PdfPaint? = null,
        clipToBounds: Boolean = false,
        block: RowScope.() -> Unit,
    ) {
        val scope = RowScope(textStyle).apply(block)
        children += RowNode(
            children = scope.children.toList(),
            spacing = spacing,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            decoration = ContainerDecoration(
                background = background,
                cornerRadius = cornerRadius,
                padding = padding,
                border = border,
                cornerRadiusEach = cornerRadiusEach,
                borderEach = borderEach,
                backgroundPaint = backgroundPaint,
                clipToBounds = clipToBounds,
            ),
        )
    }

    /**
     * Appends a Z-stacking container. Children stack along the depth
     * axis: the first added is at the bottom, the last on top. Use this
     * for image overlays, badges, or any composition where children share
     * the same X/Y space.
     *
     * Each child is positioned at one of the [BoxAlignment] anchor
     * points. Children added directly take [BoxAlignment.TopStart];
     * wrap a child in [aligned] to position it elsewhere.
     *
     * Example:
     * ```
     * box(width = 400.dp, height = 200.dp, cornerRadius = 12.dp) {
     *     image(bytes = heroBytes, contentScale = ContentScale.Crop)
     *     aligned(BoxAlignment.BottomStart) {
     *         text("Hero title") { color = PdfColor.White; fontSize = 28.sp }
     *     }
     * }
     * ```
     *
     * @param width explicit width; `null` wraps the widest child.
     * @param height explicit height; `null` wraps the tallest child.
     * @param background optional fill drawn behind every child.
     * @param cornerRadius rounded outline + clip; children are clipped
     *   to the rounded shape so they never poke past corners.
     * @param padding inset applied before placing children.
     * @param border outline stroked over the children.
     */
    public fun box(
        width: Dp? = null,
        height: Dp? = null,
        background: PdfColor? = null,
        cornerRadius: Dp = Dp.Zero,
        padding: Padding = Padding.Zero,
        border: BorderStroke? = null,
        cornerRadiusEach: CornerRadius? = null,
        borderEach: BorderSides? = null,
        backgroundPaint: PdfPaint? = null,
        clipToBounds: Boolean = false,
        block: BoxScope.() -> Unit,
    ) {
        val scope = BoxScope(textStyle).apply(block)
        children += BoxNode(
            children = scope.build(),
            width = width,
            height = height,
            decoration = ContainerDecoration(
                background = background,
                cornerRadius = cornerRadius,
                padding = padding,
                border = border,
                cornerRadiusEach = cornerRadiusEach,
                borderEach = borderEach,
                backgroundPaint = backgroundPaint,
                clipToBounds = clipToBounds,
            ),
        )
    }

    /**
     * Convenience shortcut that wraps [block] in a column inside a
     * decorated [box]. Equivalent to:
     *
     * ```
     * box(background = ..., cornerRadius = ..., padding = ..., border = ...) {
     *     aligned(BoxAlignment.TopStart) { column { block() } }
     * }
     * ```
     *
     * Useful for stat panels, list items, dashboard tiles — anywhere a
     * Material-style card pattern is the natural shape.
     *
     * @param background fill colour. Defaults to white.
     * @param cornerRadius outer corner radius. Defaults to 8 dp.
     * @param padding inset between the card outline and the content.
     * @param border optional outline stroke.
     */
    public fun card(
        background: PdfColor? = PdfColor.White,
        cornerRadius: Dp = 8.dp,
        padding: Padding = Padding.all(12.dp),
        border: BorderStroke? = null,
        cornerRadiusEach: CornerRadius? = null,
        borderEach: BorderSides? = null,
        backgroundPaint: PdfPaint? = null,
        clipToBounds: Boolean = false,
        block: ColumnScope.() -> Unit,
    ) {
        column(
            background = background,
            cornerRadius = cornerRadius,
            padding = padding,
            border = border,
            cornerRadiusEach = cornerRadiusEach,
            borderEach = borderEach,
            backgroundPaint = backgroundPaint,
            clipToBounds = clipToBounds,
            block = block,
        )
    }

    /**
     * Appends a fixed-size empty area. Useful for explicit gaps that don't
     * belong inside a container's `spacing` parameter.
     */
    public fun spacer(width: Dp = Dp.Zero, height: Dp = Dp.Zero) {
        children += SpacerNode(width, height)
    }

    /**
     * Wraps the children added inside [block] in a hyperlink annotation
     * pointing at [url]. The block contributes whatever visual content
     * the user wants — text, an image, a styled card — and the renderer
     * makes the bounding rectangle clickable in PDF viewers that support
     * link annotations.
     *
     * Use the `text` overload of this DSL on the inner block when
     * styling a link as blue underlined text:
     *
     * ```
     * link("https://example.com") {
     *     text("example.com") {
     *         color = PdfColor.Blue
     *         underline = true
     *     }
     * }
     * ```
     *
     * On Android, the underlying `PdfDocument` API does not support
     * annotations, so the rectangle is recorded but clicks fall through.
     * Visual styling on the inner content still conveys "this is a link".
     */
    public fun link(url: String, block: ColumnScope.() -> Unit) {
        val scope = ColumnScope(textStyle).apply(block)
        val inner: PdfNode = if (scope.children.size == 1) {
            scope.children.first()
        } else {
            ColumnNode(children = scope.children.toList())
        }
        children += LinkNode(url = url, child = inner)
    }

    /**
     * Appends a circle of the given [diameter]. The circle is drawn from a
     * 4-cubic-Bézier path so it stays smooth at any zoom level.
     *
     * Pass either [fill] (solid colour shorthand), [fillPaint] (gradient),
     * or both — `fillPaint` takes precedence when both are supplied. To
     * draw an outline-only circle, leave both fill parameters `null` and
     * supply [strokeColor] + [strokeWidth].
     *
     * @param diameter outer diameter of the circle.
     * @param fill solid fill colour. Skipped when `null`.
     * @param fillPaint optional gradient / paint fill. Wins over [fill].
     * @param strokeColor optional outline colour. Stroke is skipped if
     *   `null` or [strokeWidth] is `Dp.Zero`.
     * @param strokeWidth outline thickness.
     */
    public fun circle(
        diameter: Dp,
        fill: PdfColor? = null,
        fillPaint: PdfPaint? = null,
        strokeColor: PdfColor? = null,
        strokeWidth: Dp = Dp.Zero,
    ) {
        children += ShapeNode(
            shape = Shape.Circle,
            width = diameter,
            height = diameter,
            fill = fillPaint ?: fill?.let { PdfPaint.Solid(it) },
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
        )
    }

    /**
     * Appends an ellipse stretched to fit `[width] × [height]`. Same fill
     * / stroke vocabulary as [circle].
     */
    public fun ellipse(
        width: Dp,
        height: Dp,
        fill: PdfColor? = null,
        fillPaint: PdfPaint? = null,
        strokeColor: PdfColor? = null,
        strokeWidth: Dp = Dp.Zero,
    ) {
        children += ShapeNode(
            shape = Shape.Ellipse,
            width = width,
            height = height,
            fill = fillPaint ?: fill?.let { PdfPaint.Solid(it) },
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
        )
    }

    /**
     * Appends a vertical bulleted list — one row per item, each consisting
     * of a [bullet] marker and the item text on the same line. Wrapping
     * text in an item indents continuation lines under the first text
     * line, not under the bullet, which matches the typical typographic
     * convention for prose lists.
     *
     * @param items one entry per list row.
     * @param bullet character (or short string) drawn before each item.
     *   Defaults to a Unicode bullet (`•`).
     * @param markerWidth width reserved for the marker column. Larger
     *   values make wider visual gutters; smaller values pack the bullet
     *   close to the text. Defaults to `16.dp`.
     * @param spacing vertical gap inserted between consecutive list rows.
     */
    public fun bulletList(
        items: List<String>,
        bullet: String = "•",
        markerWidth: Dp = 16.dp,
        spacing: Dp = 4.dp,
    ) {
        children += listInternal(items, spacing, markerWidth) { _ -> bullet }
    }

    /**
     * Appends a numbered list — same shape as [bulletList] but with
     * `"1."`, `"2."`, … markers. Use [startAt] when the list logically
     * continues from another series (e.g. step 4 of a tutorial that
     * already showed steps 1–3 elsewhere).
     */
    public fun numberedList(
        items: List<String>,
        startAt: Int = 1,
        markerWidth: Dp = 20.dp,
        spacing: Dp = 4.dp,
    ) {
        children += listInternal(items, spacing, markerWidth) { index -> "${startAt + index}." }
    }

    /**
     * Builds the column-of-rows that backs both list flavours. Pulled out
     * so the bullet / numbered variants share marker placement rules and
     * stay in lock-step if either one changes.
     *
     * Layout per item:
     * - A fixed-width [box] holds the marker so wrapped text in the
     *   weighted body column always aligns with itself, not under the
     *   marker.
     * - The body column receives all remaining horizontal space via
     *   [weighted].
     */
    private fun listInternal(
        items: List<String>,
        spacing: Dp,
        markerWidth: Dp,
        marker: (Int) -> String,
    ): ColumnNode {
        val rowsScope = ColumnScope(textStyle)
        rowsScope.column(spacing = spacing) {
            for ((index, item) in items.withIndex()) {
                row(verticalAlignment = VerticalAlignment.Top) {
                    box(width = markerWidth) {
                        aligned(BoxAlignment.TopStart) {
                            text(marker(index))
                        }
                    }
                    weighted(1f) {
                        text(item)
                    }
                }
            }
        }
        return rowsScope.children.first() as ColumnNode
    }

    /**
     * Appends a horizontal divider line that spans the parent's full
     * available width.
     *
     * Useful for visually separating sections — under section headings,
     * between table rows that don't have their own border, or as a quiet
     * footer rule. The default look is a `0.5 dp` solid gray line which
     * reads as a hairline rule on most PDF viewers.
     *
     * @param thickness stroke width. `0.5.dp` is the safe default; bump
     *   to `1.dp` or more for more visual weight.
     * @param color stroke colour.
     * @param style stroke pattern — solid (default), dashed, or dotted.
     */
    public fun divider(
        thickness: Dp = Dp(0.5f),
        color: PdfColor = PdfColor.Gray,
        style: LineStyle = LineStyle.Solid,
    ) {
        children += DividerNode(thickness, color, style)
    }

    /**
     * Appends an image node with explicit dimensions.
     *
     * @param bytes encoded bytes of the source image. PNG and JPEG are
     *   supported on every platform; WebP and HEIF are decoded by the
     *   platform when available (Android 10+ for HEIF, iOS 11+ for both).
     * @param width rendered width on the page.
     * @param height rendered height on the page.
     * @param contentScale how to fit the intrinsic pixels into the
     *   destination rectangle. Defaults to [ContentScale.Fit] which
     *   preserves aspect ratio with letterboxing.
     */
    public fun image(
        bytes: ByteArray,
        width: Dp,
        height: Dp,
        contentScale: ContentScale = ContentScale.Fit,
    ) {
        children += ImageNode(bytes, width, height, contentScale)
    }

    /**
     * Appends an image whose width is given and whose height is derived
     * from the intrinsic aspect ratio (sniffed from the PNG/JPEG header).
     *
     * Falls back to the supplied width as the height if the format header
     * is not recognized — pass an explicit [height] in that case.
     */
    public fun image(
        bytes: ByteArray,
        width: Dp,
        contentScale: ContentScale = ContentScale.Fit,
    ) {
        children += ImageNode(bytes, width = width, height = null, contentScale = contentScale)
    }

    /**
     * Appends an image rendered at its intrinsic pixel dimensions, mapped
     * 1px → 1pt. Useful when the source asset is already sized for print.
     */
    public fun image(
        bytes: ByteArray,
        contentScale: ContentScale = ContentScale.Fit,
    ) {
        children += ImageNode(bytes, width = null, height = null, contentScale = contentScale)
    }

    /**
     * Appends a vector icon previously parsed with [VectorImage.parse].
     *
     * Use this overload when the same icon appears in multiple places in
     * the document — parse once, reuse many times. For one-off icons the
     * convenience overload taking an XML [String] avoids the explicit
     * `VectorImage.parse(...)` step.
     *
     * @param image parsed vector graphic.
     * @param width rendered width on the page; `null` derives the width
     *   from the intrinsic aspect ratio if [height] is given, otherwise
     *   uses [VectorImage.intrinsicWidth].
     * @param height same logic mirrored for the vertical axis.
     * @param tint optional colour applied uniformly to every fill,
     *   overriding the colours baked into the source XML.
     * @param strokeMode whether to inherit, disable, or recolour every
     *   path's stroke at draw time. Defaults to
     *   [VectorStrokeMode.Inherit].
     */
    public fun vector(
        image: VectorImage,
        width: Dp? = null,
        height: Dp? = null,
        tint: PdfColor? = null,
        strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit,
    ) {
        children += VectorNode(image, width, height, tint, strokeMode)
    }

    /**
     * Convenience overload that parses [xml] on the fly and embeds the
     * resulting vector. Prefer the [VectorImage] overload when the same
     * icon is reused several times — parsing is not free.
     *
     * @param xml Android `<vector>` or W3C `<svg>` source.
     */
    public fun vector(
        xml: String,
        width: Dp? = null,
        height: Dp? = null,
        tint: PdfColor? = null,
        strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit,
    ) {
        vector(VectorImage.parse(xml), width, height, tint, strokeMode)
    }

    /**
     * Appends a tabular layout with the given columns and arbitrary rows.
     *
     * The DSL is designed for data-driven tables — you typically declare a
     * [com.conamobile.pdfkmp.dsl.TableScope.header] once and then iterate
     * through a list of model objects with `forEach` to add a body row per
     * record.
     *
     * Example:
     * ```
     * table(
     *     columns = listOf(
     *         TableColumn.Fixed(60.dp),
     *         TableColumn.Weight(2f),
     *         TableColumn.Weight(1f),
     *     ),
     *     border = TableBorder(color = PdfColor.Gray, width = 1.dp),
     *     cornerRadius = 8.dp,
     * ) {
     *     header { cell("ID"); cell("Name"); cell("Status") }
     *     users.forEachIndexed { i, user ->
     *         row(background = if (i % 2 == 0) PdfColor.White else PdfColor.LightGray) {
     *             cell(user.id.toString())
     *             cell(user.name)
     *             cell(user.status)
     *         }
     *     }
     * }
     * ```
     *
     * @param columns specification of every column's width. Mix [TableColumn.Fixed]
     *   for explicit widths with [TableColumn.Weight] for proportional
     *   columns. Must contain at least one entry.
     * @param border outline and separator-line configuration. Pass
     *   [TableBorder.None] to disable borders.
     * @param cornerRadius outer rectangle corner radius. The clipping
     *   shape extends to row backgrounds too — first/last row corners
     *   visibly round off when this is non-zero.
     * @param cellPadding default padding applied to every cell. Override
     *   per row via `row(cellPadding = ...)` or per cell via
     *   `cell(padding = ...)`.
     */
    public fun table(
        columns: List<TableColumn>,
        border: TableBorder = TableBorder(),
        cornerRadius: Dp = Dp.Zero,
        cellPadding: Padding = Padding.all(Dp(8f)),
        block: TableScope.() -> Unit,
    ) {
        require(columns.isNotEmpty()) { "table must have at least one column" }
        val scope = TableScope(textStyle, cellPadding).apply(block)
        children += TableNode(
            columns = columns,
            rows = scope.rows.toList(),
            headerRow = scope.header,
            border = border,
            cornerRadius = cornerRadius,
            cellPadding = cellPadding,
        )
    }

    /**
     * Wraps the children added inside [block] so that the layout engine
     * gives them a proportional share of the parent container's remaining
     * space along the main axis.
     *
     * Inside a [row], `weighted(2f) { ... }` widens the wrapped content to
     * `(2 / totalWeights) × (rowWidth − fixedWidth)`. Inside a [column] it
     * does the same to height. Multiple weighted siblings split the
     * remaining space proportionally — `weighted(1f) { ... }` next to
     * `weighted(2f) { ... }` becomes a 1:2 ratio.
     *
     * The [block] runs in a [ColumnScope] so multiple children inside a
     * weighted area stack vertically. Wrap them in [row] explicitly if you
     * want horizontal stacking inside the weighted slot.
     *
     * @param weight share of the remaining space; must be `> 0`.
     */
    public fun weighted(weight: Float, block: ColumnScope.() -> Unit) {
        require(weight > 0f) { "weight must be > 0 (got $weight)" }
        val scope = ColumnScope(textStyle).apply(block)
        val inner = ColumnNode(scope.children.toList())
        children += WeightNode(weight, inner)
    }
}

/** Receiver inside `column { ... }`. */
@PdfDsl
public class ColumnScope internal constructor(textStyle: TextStyle) : ContainerScope(textStyle)

/** Receiver inside `row { ... }`. */
@PdfDsl
public class RowScope internal constructor(textStyle: TextStyle) : ContainerScope(textStyle)
