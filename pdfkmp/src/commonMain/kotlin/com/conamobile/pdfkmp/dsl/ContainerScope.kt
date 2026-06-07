package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.barcode.QrErrorCorrection
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.HorizontalArrangement
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.layout.VerticalArrangement
import com.conamobile.pdfkmp.node.AnchorNode
import com.conamobile.pdfkmp.node.BarcodeNode
import com.conamobile.pdfkmp.node.BookmarkNode
import com.conamobile.pdfkmp.node.BoxChild
import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.InternalLinkNode
import com.conamobile.pdfkmp.node.KeepTogetherNode
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.DividerNode
import com.conamobile.pdfkmp.node.FormCheckBoxNode
import com.conamobile.pdfkmp.node.FormTextFieldNode
import com.conamobile.pdfkmp.node.ImageNode
import com.conamobile.pdfkmp.node.LinkNode
import com.conamobile.pdfkmp.node.MultiColumnNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.QrCodeNode
import com.conamobile.pdfkmp.node.RichTextNode
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.Shape
import com.conamobile.pdfkmp.node.ShapeNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.TocNode
import com.conamobile.pdfkmp.node.VectorNode
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.DropShadow
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
        dropShadow: DropShadow? = null,
        rotation: Float = 0f,
        opacity: Float = 1f,
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
                dropShadow = dropShadow,
                rotation = rotation,
                opacity = opacity,
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
        dropShadow: DropShadow? = null,
        rotation: Float = 0f,
        opacity: Float = 1f,
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
                dropShadow = dropShadow,
                rotation = rotation,
                opacity = opacity,
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
        dropShadow: DropShadow? = null,
        rotation: Float = 0f,
        opacity: Float = 1f,
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
                dropShadow = dropShadow,
                rotation = rotation,
                opacity = opacity,
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
        dropShadow: DropShadow? = null,
        rotation: Float = 0f,
        opacity: Float = 1f,
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
            dropShadow = dropShadow,
            rotation = rotation,
            opacity = opacity,
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
     * Appends an interactive AcroForm text input field — a fillable box in
     * the produced PDF.
     *
     * Per-platform behaviour:
     * - **JVM / Desktop (PdfBox)** — a real interactive `PDTextField` the
     *   user can type into, plus the static visual box.
     * - **Android / iOS** — visual-only: a bordered light-gray box rendered
     *   with [value] inside, but not editable (the underlying PDF generators
     *   expose no AcroForm API). The field still reads correctly as a form
     *   slot when the document is printed or viewed.
     *
     * Field-name collisions are resolved by the backend by appending a
     * numeric suffix (`-2`, `-3`, …) so two fields named the same don't
     * clobber each other's value.
     *
     * @param name AcroForm field name (used to read the value back).
     * @param width rendered width of the field box.
     * @param height rendered height of the field box.
     * @param value initial text content.
     * @param multiline whether the field accepts multiple lines.
     */
    public fun textField(
        name: String,
        width: Dp,
        height: Dp = Dp(24f),
        value: String = "",
        multiline: Boolean = false,
    ) {
        children += FormTextFieldNode(
            name = name,
            width = width,
            height = height,
            value = value,
            multiline = multiline,
        )
    }

    /**
     * Appends an interactive AcroForm checkbox.
     *
     * Per-platform behaviour mirrors [textField]: interactive `PDCheckBox`
     * on JVM/Desktop, visual-only square (with an `X` when [checked]) on
     * Android and iOS.
     *
     * @param name AcroForm field name (used to read the state back).
     * @param size edge length of the square checkbox.
     * @param checked initial on/off state.
     */
    public fun checkBox(
        name: String,
        size: Dp = Dp(14f),
        checked: Boolean = false,
    ) {
        children += FormCheckBoxNode(name = name, size = size, checked = checked)
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
     * Adds an entry to the document outline — the bookmark sidebar PDF
     * readers show for quick navigation. The entry points at this marker's
     * position in the rendered flow, so place it right before the heading
     * it labels.
     *
     * Zero-size: contributes nothing visual. Supported on iOS and
     * JVM/Desktop; Android's `PdfDocument` API has no outline support, so
     * the marker is silently ignored there.
     *
     * @param title text shown in the outline panel.
     * @param level nesting depth — `0` for chapters, `1` for sections
     *   under the previous level-0 entry, and so on.
     */
    public fun bookmark(title: String, level: Int = 0) {
        children += BookmarkNode(title = title, level = level)
    }

    /**
     * Registers a named jump target at this position. Pair with
     * [linkToAnchor] to build clickable cross-references ("see chapter 3")
     * and tables of contents.
     *
     * Zero-size: contributes nothing visual.
     *
     * @param id document-unique destination name.
     */
    public fun anchor(id: String) {
        children += AnchorNode(id = id)
    }

    /**
     * Wraps the content added in [block] in a clickable region that jumps
     * to the [anchor] registered under the same id — the internal-link
     * counterpart of [link]. Forward references are fine: the anchor may
     * appear later in the document.
     *
     * Clickable on iOS and JVM/Desktop; on Android only the visual
     * styling renders (no annotation support in `PdfDocument`). Style the
     * content yourself, exactly like [link].
     *
     * @param anchor the [anchor] id to jump to.
     */
    public fun linkToAnchor(anchor: String, block: ColumnScope.() -> Unit) {
        val scope = ColumnScope(textStyle).apply(block)
        val inner = if (scope.children.size == 1) {
            scope.children.first()
        } else {
            ColumnNode(children = scope.children.toList())
        }
        children += InternalLinkNode(anchorId = anchor, child = inner)
    }

    /**
     * Appends a newspaper-style multi-column block: the children added in
     * [block] flow into [count] equal-width columns, balanced so the
     * columns end up roughly the same height while keeping source order.
     *
     * The block participates in page breaking as a single unit — columns
     * do not continue onto the next page. Split very long content into
     * several `columns { }` blocks when it can exceed one page.
     *
     * @param count number of columns; must be positive.
     * @param gap horizontal space between adjacent columns.
     * @param spacing vertical gap between items inside a column.
     */
    public fun columns(
        count: Int = 2,
        gap: Dp = Dp(16f),
        spacing: Dp = Dp(6f),
        block: ColumnScope.() -> Unit,
    ) {
        require(count > 0) { "columns must have at least one column (got $count)" }
        val scope = ColumnScope(textStyle).apply(block)
        children += MultiColumnNode(
            children = scope.children.toList(),
            count = count,
            gap = gap,
            spacing = spacing,
        )
    }

    /**
     * Appends an automatically generated table of contents.
     *
     * Every [bookmark] in the document becomes one clickable row — title,
     * dotted leader, final page number — that jumps to the bookmark's
     * position. Page numbers are resolved with a dry-run layout pass, so
     * forward references (the TOC usually sits before the chapters) come
     * out correct, including the page shift the TOC itself introduces.
     *
     * Only valid in a page body; headers, footers, and watermarks cannot
     * host a TOC because they are rebuilt for every physical page.
     *
     * @param maxLevel deepest bookmark level included; `0` lists chapters
     *   only, `1` adds their sections, and so on.
     * @param indentPerLevel horizontal indent applied per bookmark level.
     * @param spacing vertical gap between entry rows.
     */
    public fun tableOfContents(
        maxLevel: Int = 1,
        indentPerLevel: Dp = Dp(14f),
        spacing: Dp = Dp(6f),
    ) {
        children += TocNode(
            maxLevel = maxLevel,
            style = textStyle,
            indentPerLevel = indentPerLevel,
            spacing = spacing,
        )
    }

    /**
     * Appends a QR code symbol encoding [data] in byte mode (UTF-8). The
     * symbol is drawn as crisp vector squares, so it scans reliably at any
     * print size, and the smallest QR version that fits the payload at the
     * requested [errorCorrection] level is selected automatically.
     *
     * Leave some quiet space around the symbol (the QR spec recommends 4
     * modules) — a padded container or the page margin is usually enough.
     *
     * @param data payload — URLs, plain text, vCards, etc.
     * @param size rendered edge length of the (square) symbol.
     * @param errorCorrection redundancy level; higher levels survive more
     *   damage but produce denser symbols.
     * @param color module (dark square) colour.
     * @param background fill behind the symbol; `null` for transparent.
     *   Keep strong contrast against [color] or scanners will struggle.
     */
    public fun qrCode(
        data: String,
        size: Dp = Dp(100f),
        errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
        color: PdfColor = PdfColor.Black,
        background: PdfColor? = PdfColor.White,
    ) {
        children += QrCodeNode(
            data = data,
            errorCorrection = errorCorrection,
            size = size,
            color = color,
            background = background,
        )
    }

    /**
     * Appends a Code 128 barcode encoding [data] (printable ASCII 32–126).
     * Digit runs compress automatically via code set C, and the mandatory
     * mod-103 checksum is appended for you.
     *
     * Readers expect a quiet zone of roughly ten modules on both sides —
     * give the barcode some horizontal breathing room. The human-readable
     * caption customary under retail barcodes is not drawn automatically;
     * add a centred `text(data)` below when you need one.
     *
     * @param data payload; non-ASCII input throws [IllegalArgumentException].
     * @param width rendered width; `null` uses the symbol's natural size of
     *   one PDF point per module.
     * @param height bar height — taller bars are easier to scan.
     * @param color bar colour.
     * @param background fill behind the bars; `null` for transparent.
     */
    public fun barcode(
        data: String,
        width: Dp? = null,
        height: Dp = Dp(50f),
        color: PdfColor = PdfColor.Black,
        background: PdfColor? = PdfColor.White,
    ) {
        children += BarcodeNode(
            data = data,
            width = width,
            height = height,
            color = color,
            background = background,
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
     * @param allowDownScale when `true` (default), the platform backend
     *   subsamples the source bitmap to roughly match the rendered size
     *   at 200 DPI before drawing — keeping heap and PDF size in check for
     *   image-heavy documents. Pass `false` for one-off assets that must
     *   keep every source pixel.
     */
    public fun image(
        bytes: ByteArray,
        width: Dp,
        height: Dp,
        contentScale: ContentScale = ContentScale.Fit,
        allowDownScale: Boolean = true,
        altText: String? = null,
    ) {
        children += ImageNode(bytes, width, height, contentScale, allowDownScale, altText)
    }

    /**
     * Appends an image whose width is given and whose height is derived
     * from the intrinsic aspect ratio (sniffed from the PNG/JPEG header).
     *
     * Falls back to the supplied width as the height if the format header
     * is not recognized — pass an explicit [height] in that case.
     *
     * @param allowDownScale see the explicit-dimensions overload above.
     */
    public fun image(
        bytes: ByteArray,
        width: Dp,
        contentScale: ContentScale = ContentScale.Fit,
        allowDownScale: Boolean = true,
        altText: String? = null,
    ) {
        children += ImageNode(
            bytes = bytes,
            width = width,
            height = null,
            contentScale = contentScale,
            allowDownScale = allowDownScale,
            altText = altText,
        )
    }

    /**
     * Appends an image rendered at its intrinsic pixel dimensions, mapped
     * 1px → 1pt. Useful when the source asset is already sized for print.
     *
     * @param allowDownScale see the explicit-dimensions overload above.
     */
    public fun image(
        bytes: ByteArray,
        contentScale: ContentScale = ContentScale.Fit,
        allowDownScale: Boolean = true,
        altText: String? = null,
    ) {
        children += ImageNode(
            bytes = bytes,
            width = null,
            height = null,
            contentScale = contentScale,
            allowDownScale = allowDownScale,
            altText = altText,
        )
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
     * @param repeatHeader when the page's
     *   [com.conamobile.pdfkmp.layout.PageBreakStrategy.Slice] strategy
     *   splits the table across pages, re-draw the header row at the top
     *   of every continuation page. Ignored for tables that fit on one
     *   page or have no header.
     */
    public fun table(
        columns: List<TableColumn>,
        border: TableBorder = TableBorder(),
        cornerRadius: Dp = Dp.Zero,
        cellPadding: Padding = Padding.all(Dp(8f)),
        repeatHeader: Boolean = true,
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
            repeatHeader = repeatHeader,
        )
    }

    /**
     * Appends a free-form vector drawing authored in a local coordinate
     * space of `(0, 0)`–`(width, height)` and scaled into the node's final
     * rectangle. Use it for diagrams, decorations, or any shape the
     * primitive nodes don't cover — everything stays sharp vector output.
     *
     * Example — a warning triangle:
     * ```
     * freeDraw(width = 60.dp, height = 60.dp) {
     *     path(fill = PdfColor(1f, 0.8f, 0.2f), strokeColor = PdfColor.Black, strokeWidth = 2f) {
     *         moveTo(30f, 4f); lineTo(56f, 52f); lineTo(4f, 52f); close()
     *     }
     * }
     * ```
     *
     * @param width rendered width; also the local coordinate space width.
     * @param height rendered height; also the local coordinate space height.
     */
    public fun freeDraw(width: Dp, height: Dp, block: FreeDrawScope.() -> Unit) {
        val scope = FreeDrawScope().apply(block)
        children += VectorNode(
            image = VectorImage(
                viewportWidth = width.value,
                viewportHeight = height.value,
                intrinsicWidth = width.value,
                intrinsicHeight = height.value,
                paths = scope.paths.toList(),
            ),
            width = width,
            height = height,
        )
    }

    /**
     * Appends a uniform grid: children added inside [block] flow row-major
     * into [columns] equal-width cells. The last row is padded with empty
     * cells so every column keeps the same width.
     *
     * Sugar over nested [column] / [row] / weighted slots — grid cells can
     * hold any node, including cards and images.
     *
     * @param columns number of cells per row; must be positive.
     * @param spacing gap inserted both between rows and between columns.
     */
    public fun grid(columns: Int, spacing: Dp = Dp.Zero, block: GridScope.() -> Unit) {
        require(columns > 0) { "grid must have at least one column (got $columns)" }
        val scope = GridScope(textStyle).apply(block)
        column(spacing = spacing) {
            scope.children.toList().chunked(columns).forEach { rowCells ->
                row(spacing = spacing) {
                    rowCells.forEach { cell ->
                        children += WeightNode(1f, cell)
                    }
                    repeat(columns - rowCells.size) {
                        children += WeightNode(1f, SpacerNode())
                    }
                }
            }
        }
    }

    /**
     * Wraps the children added in [block] in a group the page-break
     * machinery never splits: under the `Slice` strategy the group moves
     * to a fresh page whole instead of being cut mid-content — the
     * `break-inside: avoid` of this DSL. Use it for figures with their
     * captions, stat cards, or any cluster where a page break in the
     * middle would read as a bug.
     *
     * A group taller than one full page still overflows past the bottom
     * margin (there is nowhere whole to move it).
     */
    public fun keepTogether(block: ColumnScope.() -> Unit) {
        val scope = ColumnScope(textStyle).apply(block)
        val inner = if (scope.children.size == 1) {
            scope.children.first()
        } else {
            ColumnNode(children = scope.children.toList())
        }
        children += KeepTogetherNode(child = inner)
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

/**
 * Receiver inside `grid { ... }`. Each child added here becomes one grid
 * cell, filled row-major.
 */
@PdfDsl
public class GridScope internal constructor(textStyle: TextStyle) : ContainerScope(textStyle)

/** Receiver inside `row { ... }`. */
@PdfDsl
public class RowScope internal constructor(textStyle: TextStyle) : ContainerScope(textStyle)
