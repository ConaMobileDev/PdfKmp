package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.barcode.Code128Encoder
import com.conamobile.pdfkmp.barcode.QrCodeGenerator
import com.conamobile.pdfkmp.geometry.Constraints
import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.image.readImageInfo
import com.conamobile.pdfkmp.node.AnchorNode
import com.conamobile.pdfkmp.node.BarcodeNode
import com.conamobile.pdfkmp.node.BookmarkNode
import com.conamobile.pdfkmp.node.BoxNode
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.DividerNode
import com.conamobile.pdfkmp.node.FormCheckBoxNode
import com.conamobile.pdfkmp.node.FormTextFieldNode
import com.conamobile.pdfkmp.node.ImageNode
import com.conamobile.pdfkmp.node.InternalLinkNode
import com.conamobile.pdfkmp.node.KeepTogetherNode
import com.conamobile.pdfkmp.node.LazyNode
import com.conamobile.pdfkmp.node.LinkNode
import com.conamobile.pdfkmp.node.MultiColumnNode
import com.conamobile.pdfkmp.node.PdfNode
import com.conamobile.pdfkmp.node.QrCodeNode
import com.conamobile.pdfkmp.node.RichTextNode
import com.conamobile.pdfkmp.node.RowNode
import com.conamobile.pdfkmp.node.ShapeNode
import com.conamobile.pdfkmp.node.SpacerNode
import com.conamobile.pdfkmp.node.TableNode
import com.conamobile.pdfkmp.node.TableRowNode
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.node.TocNode
import com.conamobile.pdfkmp.node.VectorNode
import com.conamobile.pdfkmp.node.WeightNode
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign

/**
 * Recursive layout pass that turns a [PdfNode] tree into a [MeasuredNode]
 * tree.
 *
 * The pass is single-pass for non-weighted children and two-pass for
 * containers that contain weighted children (`weighted { ... }`): pass one
 * measures non-weighted children to find the fixed space they consume,
 * pass two distributes the remainder among the weighted siblings
 * proportionally to their weight.
 *
 * Container nodes ([ColumnNode], [RowNode]) recurse into their children.
 * Leaf nodes ([TextNode], [SpacerNode], [ImageNode]) compute their own
 * dimensions directly.
 */
public fun measure(
    node: PdfNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredNode = when (node) {
    is TextNode -> layoutText(
        text = node.text,
        style = node.style,
        maxWidth = constraints.maxWidth,
        metrics = metrics,
    )

    is SpacerNode -> MeasuredBlock(
        size = Size(width = node.width.value, height = node.height.value),
    )

    is FormTextFieldNode -> {
        // Clamp to the available width like images / QR codes so a field
        // wider than the slot doesn't spill past the page margin.
        val requested = node.width.value
        val width = if (requested > constraints.maxWidth && constraints.maxWidth > 0f) {
            constraints.maxWidth
        } else {
            requested
        }
        // Field font size: leave a couple of points of vertical padding inside
        // the box, capped so single-line fields stay legible.
        val fontSizePt = (node.height.value - 8f).coerceIn(6f, 14f)
        MeasuredFormTextField(
            name = node.name,
            value = node.value,
            multiline = node.multiline,
            fontSizePt = fontSizePt,
            size = Size(width = width, height = node.height.value),
        )
    }

    is FormCheckBoxNode -> {
        val side = node.size.value.let { requested ->
            if (requested > constraints.maxWidth && constraints.maxWidth > 0f) constraints.maxWidth else requested
        }
        MeasuredFormCheckBox(
            name = node.name,
            checked = node.checked,
            size = Size(width = side, height = side),
        )
    }

    is DividerNode -> MeasuredDivider(
        thickness = node.thickness.value,
        color = node.color,
        style = node.style,
        size = Size(width = constraints.maxWidth, height = node.thickness.value),
    )

    is ImageNode -> measureImage(node, constraints)

    is ColumnNode -> measureColumn(node, constraints, metrics)

    is RowNode -> measureRow(node, constraints, metrics)

    is WeightNode -> measure(node.child, constraints, metrics)

    is TableNode -> measureTable(node, constraints, metrics)

    is VectorNode -> measureVector(node, constraints)

    is BoxNode -> measureBox(node, constraints, metrics)

    is RichTextNode -> layoutRichText(
        spans = node.spans,
        maxWidth = constraints.maxWidth,
        align = node.align,
        paragraphLineHeight = node.lineHeight,
        metrics = metrics,
    )

    is ShapeNode -> MeasuredShape(
        shape = node.shape,
        fill = node.fill,
        strokeColor = node.strokeColor,
        strokeWidth = node.strokeWidth.value,
        size = Size(width = node.width.value, height = node.height.value),
    )

    is LinkNode -> {
        val measuredChild = measure(node.child, constraints, metrics)
        MeasuredLink(url = node.url, child = measuredChild, size = measuredChild.size)
    }

    is QrCodeNode -> measureQrCode(node, constraints)

    is BarcodeNode -> measureBarcode(node, constraints)

    is BookmarkNode -> MeasuredBookmark(
        title = node.title,
        level = node.level,
        size = Size(width = 0f, height = 0f),
    )

    is AnchorNode -> MeasuredAnchor(
        id = node.id,
        size = Size(width = 0f, height = 0f),
    )

    is InternalLinkNode -> {
        val measuredChild = measure(node.child, constraints, metrics)
        MeasuredInternalLink(anchorId = node.anchorId, child = measuredChild, size = measuredChild.size)
    }

    is TocNode -> error(
        "tableOfContents() reached the layout engine without being expanded. " +
            "The renderer expands it for page bodies before layout — headers, " +
            "footers, and watermarks are rebuilt per physical page and cannot " +
            "host a table of contents.",
    )

    is MultiColumnNode -> measureMultiColumn(node, constraints, metrics)

    is KeepTogetherNode -> {
        val measuredChild = measure(node.child, constraints, metrics)
        MeasuredKeepTogether(child = measuredChild, size = measuredChild.size)
    }

    is LazyNode -> error(
        "LazyNode reached the layout engine without being resolved. " +
            "Build the document with pdfAsync { } instead of pdf { } when " +
            "the DSL contains async-loaded resources " +
            "(e.g. drawable(Res.drawable.X) from :pdfkmp-compose-resources). " +
            "Headers, footers, and watermarks are invoked per page during " +
            "render and cannot hold lazy resources — load them in the page " +
            "body or pre-resolve before passing to the header/footer factory.",
    )
}

/**
 * Measures a [BoxNode]: every child is measured against the box's
 * interior (after subtracting [ContainerDecoration.padding]) and placed
 * at its anchor point inside the resolved box size.
 *
 * Box size resolution:
 * - If [BoxNode.width] / [BoxNode.height] are set, those win.
 * - Otherwise the box wraps its largest measured child (plus the
 *   decoration padding).
 */
private fun measureBox(
    node: BoxNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredBox {
    val padding = node.decoration.padding
    val horizontalInset = padding.left.value + padding.right.value
    val verticalInset = padding.top.value + padding.bottom.value
    val explicitWidth = node.width?.value
    val explicitHeight = node.height?.value

    val maxChildWidth = if (explicitWidth != null) {
        (explicitWidth - horizontalInset).coerceAtLeast(0f)
    } else {
        (constraints.maxWidth - horizontalInset).coerceAtLeast(0f)
    }
    val childConstraints = Constraints(maxWidth = maxChildWidth)

    val measuredChildren = node.children.map { boxChild ->
        measure(boxChild.node, childConstraints, metrics) to boxChild.alignment
    }

    val childrenWidth = measuredChildren.maxOfOrNull { it.first.size.width } ?: 0f
    val childrenHeight = measuredChildren.maxOfOrNull { it.first.size.height } ?: 0f
    val finalWidth = explicitWidth ?: (childrenWidth + horizontalInset)
    val finalHeight = explicitHeight ?: (childrenHeight + verticalInset)
    val interiorWidth = (finalWidth - horizontalInset).coerceAtLeast(0f)
    val interiorHeight = (finalHeight - verticalInset).coerceAtLeast(0f)

    val placed = measuredChildren.map { (childMeasured, alignment) ->
        // For non-Start x alignment we let text/rich-text declare the
        // box's interior width as their effective extent — see
        // [effectiveCrossWidth] for the rationale. Without this, the box
        // shifts the text by `interiorWidth - intrinsic` and the text's
        // own align shifts again by the same amount, doubling the offset.
        val effectiveWidth = if (alignment.isStartAlignedX()) {
            childMeasured.size.width
        } else {
            minOf(effectiveCrossWidth(childMeasured), interiorWidth)
        }
        val (offsetX, offsetY) = boxAlignmentOffset(
            alignment = alignment,
            interiorWidth = interiorWidth,
            interiorHeight = interiorHeight,
            childWidth = effectiveWidth,
            childHeight = childMeasured.size.height,
        )
        PlacedChild(
            node = childMeasured,
            offsetX = padding.left.value + offsetX,
            offsetY = padding.top.value + offsetY,
        )
    }

    return MeasuredBox(
        children = placed,
        size = Size(width = finalWidth, height = finalHeight),
        decoration = node.decoration,
    )
}

/**
 * `true` when [BoxAlignment] anchors the child to the box's leading edge —
 * the cases where the alignment offset on the x axis is zero and there is
 * therefore nothing to dedup against an internal text alignment. See
 * [effectiveCrossWidth] for context.
 */
private fun BoxAlignment.isStartAlignedX(): Boolean = when (this) {
    BoxAlignment.TopStart, BoxAlignment.CenterStart, BoxAlignment.BottomStart -> true
    BoxAlignment.TopCenter, BoxAlignment.Center, BoxAlignment.BottomCenter,
    BoxAlignment.TopEnd, BoxAlignment.CenterEnd, BoxAlignment.BottomEnd -> false
}

/**
 * Translates a [com.conamobile.pdfkmp.layout.BoxAlignment] anchor into a
 * `(x, y)` pair that places a `(childWidth × childHeight)` rectangle
 * inside an `(interiorWidth × interiorHeight)` slot.
 */
private fun boxAlignmentOffset(
    alignment: BoxAlignment,
    interiorWidth: Float,
    interiorHeight: Float,
    childWidth: Float,
    childHeight: Float,
): Pair<Float, Float> {
    val xSlack = (interiorWidth - childWidth).coerceAtLeast(0f)
    val ySlack = (interiorHeight - childHeight).coerceAtLeast(0f)
    val x = when (alignment) {
        BoxAlignment.TopStart, BoxAlignment.CenterStart, BoxAlignment.BottomStart -> 0f
        BoxAlignment.TopCenter, BoxAlignment.Center, BoxAlignment.BottomCenter -> xSlack / 2f
        BoxAlignment.TopEnd, BoxAlignment.CenterEnd, BoxAlignment.BottomEnd -> xSlack
    }
    val y = when (alignment) {
        BoxAlignment.TopStart, BoxAlignment.TopCenter, BoxAlignment.TopEnd -> 0f
        BoxAlignment.CenterStart, BoxAlignment.Center, BoxAlignment.CenterEnd -> ySlack / 2f
        BoxAlignment.BottomStart, BoxAlignment.BottomCenter, BoxAlignment.BottomEnd -> ySlack
    }
    return x to y
}

/**
 * Resolves the destination size of a [VectorNode] using the same rules as
 * [measureImage]: explicit dims win, missing axes are derived from the
 * intrinsic aspect ratio, and overly wide vectors are clamped to the
 * available width.
 */
private fun measureVector(node: VectorNode, constraints: Constraints): MeasuredVector {
    val explicitWidth = node.width?.value
    val explicitHeight = node.height?.value
    val intrinsicWidth = node.image.intrinsicWidth.takeIf { it > 0f } ?: node.image.viewportWidth
    val intrinsicHeight = node.image.intrinsicHeight.takeIf { it > 0f } ?: node.image.viewportHeight

    val resolvedWidth: Float
    val resolvedHeight: Float
    when {
        explicitWidth != null && explicitHeight != null -> {
            resolvedWidth = explicitWidth
            resolvedHeight = explicitHeight
        }
        explicitWidth != null -> {
            resolvedWidth = explicitWidth
            resolvedHeight = if (intrinsicWidth > 0f) {
                explicitWidth * intrinsicHeight / intrinsicWidth
            } else {
                explicitWidth
            }
        }
        explicitHeight != null -> {
            resolvedHeight = explicitHeight
            resolvedWidth = if (intrinsicHeight > 0f) {
                explicitHeight * intrinsicWidth / intrinsicHeight
            } else {
                explicitHeight
            }
        }
        else -> {
            resolvedWidth = intrinsicWidth
            resolvedHeight = intrinsicHeight
        }
    }

    val finalWidth = if (resolvedWidth > constraints.maxWidth && constraints.maxWidth > 0f) {
        constraints.maxWidth
    } else {
        resolvedWidth
    }
    val finalHeight = if (finalWidth != resolvedWidth && resolvedWidth > 0f) {
        resolvedHeight * finalWidth / resolvedWidth
    } else {
        resolvedHeight
    }

    return MeasuredVector(
        image = node.image,
        tint = node.tint,
        strokeOverride = node.strokeOverride,
        size = Size(width = finalWidth, height = finalHeight),
    )
}

/**
 * Balances a [MultiColumnNode]'s children into equal-width columns and
 * delegates the actual layout to the existing row + column measurement.
 *
 * Children are pre-measured at the column width to learn their heights,
 * then partitioned greedily: each column receives children until it
 * passes the ideal share (total height ÷ column count). Greedy keeps the
 * source order intact — a quality requirement for flowing prose — at the
 * cost of perfect balance, which is the same trade newspapers make.
 */
private fun measureMultiColumn(
    node: MultiColumnNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredNode {
    val gapTotal = node.gap.value * (node.count - 1)
    val columnWidth = ((constraints.maxWidth - gapTotal) / node.count).coerceAtLeast(0f)
    val childConstraints = Constraints(maxWidth = columnWidth)
    val heights = node.children.map { measure(it, childConstraints, metrics).size.height }
    val totalHeight = heights.sum() + node.spacing.value * (node.children.size - 1).coerceAtLeast(0)
    val target = totalHeight / node.count

    val buckets = List(node.count) { mutableListOf<PdfNode>() }
    var bucket = 0
    var bucketHeight = 0f
    node.children.forEachIndexed { index, child ->
        // Move to the next column once the current one has its share —
        // but never strand the remaining children without columns.
        if (bucketHeight > 0f && bucketHeight + heights[index] / 2f > target && bucket < node.count - 1) {
            bucket++
            bucketHeight = 0f
        }
        buckets[bucket] += child
        bucketHeight += heights[index] + node.spacing.value
    }

    val row = RowNode(
        children = buckets.map { columnChildren ->
            WeightNode(1f, ColumnNode(children = columnChildren.toList(), spacing = node.spacing))
        },
        spacing = node.gap,
    )
    return measure(row, constraints, metrics)
}

/** Default rendered edge length for QR codes when the DSL passes no size. */
private const val DEFAULT_QR_SIZE: Float = 100f

/**
 * Resolves a QR node's destination square and runs the encoder. The
 * symbol is clamped to the available width — a clipped QR code would not
 * scan, so shrinking is always the right call.
 */
private fun measureQrCode(node: QrCodeNode, constraints: Constraints): MeasuredQrCode {
    val matrix = QrCodeGenerator.encode(node.data, node.errorCorrection)
    val requested = node.size?.value ?: DEFAULT_QR_SIZE
    val side = if (requested > constraints.maxWidth && constraints.maxWidth > 0f) {
        constraints.maxWidth
    } else {
        requested
    }
    return MeasuredQrCode(
        matrix = matrix,
        color = node.color,
        background = node.background,
        size = Size(width = side, height = side),
    )
}

/**
 * Resolves a barcode's destination rectangle and runs the encoder. The
 * natural width is one PDF point per module — already crisp at print
 * resolution; explicit widths and the available slot both clamp it.
 */
private fun measureBarcode(node: BarcodeNode, constraints: Constraints): MeasuredBarcode {
    val barcode = Code128Encoder.encode(node.data)
    val requested = node.width?.value ?: barcode.totalModules.toFloat()
    val width = if (requested > constraints.maxWidth && constraints.maxWidth > 0f) {
        constraints.maxWidth
    } else {
        requested
    }
    return MeasuredBarcode(
        barcode = barcode,
        color = node.color,
        background = node.background,
        size = Size(width = width, height = node.height.value),
    )
}

/**
 * Two-pass measurement for tables.
 *
 * Pass 1 — resolve column widths from the column specs:
 *   * Sum every [TableColumn.Fixed] width.
 *   * Distribute the remainder among [TableColumn.Weight] columns
 *     proportionally to their weight.
 *
 * Pass 2 — for each row, measure each cell with its column width as the
 * horizontal constraint, take the row height as `max(cellHeight) +
 * verticalPadding`, and store every cell's content offset so the renderer
 * can apply alignment without re-measuring.
 */
private fun measureTable(
    node: TableNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredTable {
    val borderWidth = node.border.width.value
    val tableWidth = if (constraints.maxWidth == Float.POSITIVE_INFINITY) {
        node.columns.sumOf { (it as? TableColumn.Fixed)?.width?.value?.toDouble() ?: 100.0 }.toFloat()
    } else {
        constraints.maxWidth
    }

    val columnWidths = resolveColumnWidths(node.columns, tableWidth)

    val orderedRows = buildList {
        node.headerRow?.let { add(it to true) }
        node.rows.forEach { add(it to false) }
    }

    val measuredRows = orderedRows.map { (rowNode, isHeader) ->
        measureTableRow(rowNode, isHeader, columnWidths, metrics)
    }

    val totalHeight = measuredRows.sumOf { it.height.toDouble() }.toFloat()

    return MeasuredTable(
        columnWidths = columnWidths,
        rows = measuredRows,
        border = node.border,
        borderColor = node.border.color,
        borderWidth = borderWidth,
        cornerRadius = node.cornerRadius.value,
        size = Size(width = tableWidth, height = totalHeight),
        repeatHeader = node.repeatHeader,
    )
}

private fun resolveColumnWidths(columns: List<TableColumn>, tableWidth: Float): List<Float> {
    val fixedTotal = columns.sumOf {
        if (it is TableColumn.Fixed) it.width.value.toDouble() else 0.0
    }.toFloat()
    val totalWeight = columns.sumOf {
        if (it is TableColumn.Weight) it.weight.toDouble() else 0.0
    }.toFloat()

    // Fixed widths that exceed the slot would silently spill past the page
    // margin. Shrinking every fixed column proportionally keeps the
    // declared ratios while guaranteeing the table fits.
    if (fixedTotal > tableWidth && tableWidth > 0f) {
        val scale = tableWidth / fixedTotal
        return columns.map { spec ->
            when (spec) {
                is TableColumn.Fixed -> spec.width.value * scale
                is TableColumn.Weight -> 0f
            }
        }
    }

    val remaining = (tableWidth - fixedTotal).coerceAtLeast(0f)
    return columns.map { spec ->
        when (spec) {
            is TableColumn.Fixed -> spec.width.value
            is TableColumn.Weight -> if (totalWeight <= 0f) 0f else remaining * (spec.weight / totalWeight)
        }
    }
}

private fun measureTableRow(
    row: TableRowNode,
    isHeader: Boolean,
    columnWidths: List<Float>,
    metrics: FontMetrics,
): MeasuredTableRow {
    val placedCells = ArrayList<MeasuredTableCell>(columnWidths.size)
    var maxCellHeight = 0f
    var cellLeft = 0f

    for ((columnIndex, columnWidth) in columnWidths.withIndex()) {
        val cellNode = row.cells.getOrNull(columnIndex)
        if (cellNode == null) {
            placedCells += MeasuredTableCell(
                content = MeasuredBlock(Size(0f, 0f)),
                style = com.conamobile.pdfkmp.style.TableCellStyle(),
                offsetX = cellLeft,
                width = columnWidth,
                contentOffsetX = 0f,
                contentOffsetY = 0f,
            )
            cellLeft += columnWidth
            continue
        }
        val padding = cellNode.style.padding
        val contentMaxWidth = (columnWidth - padding.left.value - padding.right.value)
            .coerceAtLeast(0f)
        val contentMeasured = measure(
            cellNode.content,
            Constraints(maxWidth = contentMaxWidth),
            metrics,
        )
        val cellInteriorHeight = padding.top.value + contentMeasured.size.height + padding.bottom.value
        if (cellInteriorHeight > maxCellHeight) maxCellHeight = cellInteriorHeight

        placedCells += MeasuredTableCell(
            content = contentMeasured,
            style = cellNode.style,
            offsetX = cellLeft,
            width = columnWidth,
            contentOffsetX = padding.left.value,
            contentOffsetY = padding.top.value,
        )
        cellLeft += columnWidth
    }

    val rowHeight = maxOf(maxCellHeight, row.minHeight?.value ?: 0f)

    // Re-resolve cross-axis offsets now that the row's full height is known.
    val finalCells = placedCells.mapIndexed { i, placed ->
        val cellNode = row.cells.getOrNull(i)
        if (cellNode == null) return@mapIndexed placed
        val padding = cellNode.style.padding
        val contentBoxHeight = (rowHeight - padding.top.value - padding.bottom.value).coerceAtLeast(0f)
        val contentBoxWidth = (placed.width - padding.left.value - padding.right.value).coerceAtLeast(0f)
        // Same dedup as columns/boxes: a non-Start cell alignment must not
        // stack on top of a non-Start TextAlign that already shifts glyphs
        // across the cell's content width.
        val effectiveContentWidth = if (cellNode.style.horizontalAlignment == HorizontalAlignment.Start) {
            placed.content.size.width
        } else {
            minOf(effectiveCrossWidth(placed.content), contentBoxWidth)
        }
        val crossX = horizontalAlignmentOffset(
            childWidth = effectiveContentWidth,
            containerWidth = contentBoxWidth,
            alignment = cellNode.style.horizontalAlignment,
        )
        val crossY = verticalAlignmentOffset(
            childHeight = placed.content.size.height,
            containerHeight = contentBoxHeight,
            alignment = cellNode.style.verticalAlignment,
        )
        placed.copy(
            contentOffsetX = padding.left.value + crossX,
            contentOffsetY = padding.top.value + crossY,
        )
    }

    return MeasuredTableRow(
        height = rowHeight,
        cells = finalCells,
        background = row.background,
        isHeader = isHeader,
    )
}

private fun horizontalAlignmentOffset(
    childWidth: Float,
    containerWidth: Float,
    alignment: HorizontalAlignment,
): Float {
    val slack = (containerWidth - childWidth).coerceAtLeast(0f)
    return when (alignment) {
        HorizontalAlignment.Start -> 0f
        HorizontalAlignment.Center -> slack / 2f
        HorizontalAlignment.End -> slack
    }
}

private fun verticalAlignmentOffset(
    childHeight: Float,
    containerHeight: Float,
    alignment: VerticalAlignment,
): Float {
    val slack = (containerHeight - childHeight).coerceAtLeast(0f)
    return when (alignment) {
        VerticalAlignment.Top -> 0f
        VerticalAlignment.Center -> slack / 2f
        VerticalAlignment.Bottom -> slack
    }
}

/**
 * Resolves the destination size of an [ImageNode] from its requested dims
 * and the intrinsic dimensions sniffed out of the encoded bytes.
 *
 * Resolution rules:
 *
 * - Both `width` and `height` set → use them as-is.
 * - Only `width` set → height comes from `width × intrinsicHeight /
 *   intrinsicWidth` (aspect-preserving).
 * - Only `height` set → width comes from the inverse calculation.
 * - Neither set → use intrinsic pixel dims, mapped 1px → 1pt.
 *
 * If the image format is not recognized, missing dimensions fall back to
 * the supplied dimension (or to a 100×100 square if neither was supplied).
 */
private fun measureImage(node: ImageNode, constraints: Constraints): MeasuredImage {
    val info = readImageInfo(node.bytes)
    val explicitWidth = node.width?.value
    val explicitHeight = node.height?.value

    val resolvedWidth: Float
    val resolvedHeight: Float

    when {
        explicitWidth != null && explicitHeight != null -> {
            resolvedWidth = explicitWidth
            resolvedHeight = explicitHeight
        }

        explicitWidth != null -> {
            resolvedWidth = explicitWidth
            resolvedHeight = if (info != null && info.widthPx > 0) {
                explicitWidth * info.heightPx.toFloat() / info.widthPx.toFloat()
            } else {
                explicitWidth
            }
        }

        explicitHeight != null -> {
            resolvedHeight = explicitHeight
            resolvedWidth = if (info != null && info.heightPx > 0) {
                explicitHeight * info.widthPx.toFloat() / info.heightPx.toFloat()
            } else {
                explicitHeight
            }
        }

        else -> {
            if (info != null) {
                resolvedWidth = info.widthPx.toFloat()
                resolvedHeight = info.heightPx.toFloat()
            } else {
                resolvedWidth = FALLBACK_IMAGE_SIZE
                resolvedHeight = FALLBACK_IMAGE_SIZE
            }
        }
    }

    val finalWidth = if (resolvedWidth > constraints.maxWidth && constraints.maxWidth > 0f) {
        constraints.maxWidth
    } else {
        resolvedWidth
    }
    val finalHeight = if (finalWidth != resolvedWidth && resolvedWidth > 0f) {
        resolvedHeight * finalWidth / resolvedWidth
    } else {
        resolvedHeight
    }

    return MeasuredImage(
        bytes = node.bytes,
        contentScale = node.contentScale,
        size = Size(width = finalWidth, height = finalHeight),
        allowDownScale = node.allowDownScale,
        altText = node.altText,
    )
}

private const val FALLBACK_IMAGE_SIZE: Float = 100f

/**
 * Two-pass measurement for a column: fixed-size children first, then
 * weighted siblings get the leftover height. Cross-axis alignment is
 * applied during placement so children with different widths can still be
 * centred or right-aligned within the column.
 */
private fun measureColumn(
    node: ColumnNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredColumn {
    val spacing = node.spacing.value
    val totalSpacing = if (node.children.size > 1) spacing * (node.children.size - 1) else 0f
    val padding = node.decoration.padding
    val horizontalInset = padding.left.value + padding.right.value
    val verticalInset = padding.top.value + padding.bottom.value
    val containerHeight = if (constraints.maxHeight == Float.POSITIVE_INFINITY) {
        Float.POSITIVE_INFINITY
    } else {
        (constraints.maxHeight - verticalInset).coerceAtLeast(0f)
    }
    val containerMaxWidth = (constraints.maxWidth - horizontalInset).coerceAtLeast(0f)

    // First pass — measure non-weighted children with their intrinsic constraint.
    val childConstraints = Constraints(maxWidth = containerMaxWidth)
    val measuredFixed = node.children.map { child ->
        if (child is WeightNode) null else measure(child, childConstraints, metrics)
    }
    val totalFixedHeight = measuredFixed.filterNotNull().sumOf { it.size.height.toDouble() }.toFloat()
    val totalWeight = node.children.sumOf {
        if (it is WeightNode) it.weight.toDouble() else 0.0
    }.toFloat()

    // Second pass — measure weighted children with their share of the
    // remaining height, but only if the column has a finite height.
    val remainingHeight = (containerHeight - totalFixedHeight - totalSpacing).coerceAtLeast(0f)
    val measured: List<MeasuredNode> = node.children.mapIndexed { index, child ->
        when {
            child !is WeightNode -> measuredFixed[index]!!
            containerHeight == Float.POSITIVE_INFINITY || totalWeight <= 0f ->
                measure(child.child, childConstraints, metrics)
            else -> {
                val share = remainingHeight * (child.weight / totalWeight)
                measure(
                    child.child,
                    Constraints(maxWidth = constraints.maxWidth, maxHeight = share),
                    metrics,
                ).withMinHeight(share)
            }
        }
    }

    // When the column has a non-Start cross-axis alignment, treat each
    // child's horizontal extent as `effectiveCrossWidth(...)` rather than
    // its raw `size.width`. This lets text/rich-text children — whose
    // internal `TextAlign` already shifts glyphs across the parent's slot
    // — declare the slot as their effective width, so the column does not
    // add a second shift on top. With the default `Start` alignment the
    // offset is zero anyway, so we keep using intrinsic widths to preserve
    // hug-content sizing for `row { column { ... } sibling }` layouts.
    val crossAlignment = node.horizontalAlignment.toCrossAlignment()
    val crossWidths = if (crossAlignment == CrossAlignment.Start) {
        measured.map { it.size.width }
    } else {
        measured.map { effectiveCrossWidth(it) }
    }
    val widest = crossWidths.maxOrNull() ?: 0f
    val rawHeight = measured.sumOf { it.size.height.toDouble() }.toFloat() + totalSpacing
    val finalHeight = if (containerHeight == Float.POSITIVE_INFINITY) rawHeight else maxOf(rawHeight, 0f)

    val effectiveHeight = if (containerHeight == Float.POSITIVE_INFINITY) rawHeight else containerHeight
    val mainOffsets = computeMainAxisOffsets(
        sizes = measured.map { it.size.height },
        spacing = spacing,
        containerExtent = effectiveHeight,
        style = node.verticalArrangement.toMainAxisStyle(),
    )
    val placed = measured.mapIndexed { i, child ->
        val crossOffset = crossAxisOffset(
            childExtent = crossWidths[i],
            containerExtent = widest,
            alignment = crossAlignment,
        )
        PlacedChild(
            node = child,
            offsetX = padding.left.value + crossOffset,
            offsetY = padding.top.value + mainOffsets[i],
        )
    }

    return MeasuredColumn(
        children = placed,
        size = Size(
            width = widest + horizontalInset,
            height = finalHeight + verticalInset,
        ),
        decoration = node.decoration,
    )
}

/**
 * Two-pass measurement for a row. Mirror image of [measureColumn]: weighted
 * siblings split the remaining width and cross-axis alignment is vertical.
 */
private fun measureRow(
    node: RowNode,
    constraints: Constraints,
    metrics: FontMetrics,
): MeasuredRow {
    val spacing = node.spacing.value
    val totalSpacing = if (node.children.size > 1) spacing * (node.children.size - 1) else 0f
    val padding = node.decoration.padding
    val horizontalInset = padding.left.value + padding.right.value
    val verticalInset = padding.top.value + padding.bottom.value
    val containerWidth = (constraints.maxWidth - horizontalInset).coerceAtLeast(0f)

    val childConstraints = Constraints(maxWidth = containerWidth, maxHeight = constraints.maxHeight)
    val measuredFixed = node.children.map { child ->
        if (child is WeightNode) null else measure(child, childConstraints, metrics)
    }
    val totalFixedWidth = measuredFixed.filterNotNull().sumOf { it.size.width.toDouble() }.toFloat()
    val totalWeight = node.children.sumOf {
        if (it is WeightNode) it.weight.toDouble() else 0.0
    }.toFloat()

    val remainingWidth = (containerWidth - totalFixedWidth - totalSpacing).coerceAtLeast(0f)
    val measured: List<MeasuredNode> = node.children.mapIndexed { index, child ->
        when {
            child !is WeightNode -> measuredFixed[index]!!
            totalWeight <= 0f -> measure(child.child, childConstraints, metrics)
            else -> {
                val share = remainingWidth * (child.weight / totalWeight)
                measure(
                    child.child,
                    Constraints(maxWidth = share, maxHeight = constraints.maxHeight),
                    metrics,
                ).withMinWidth(share)
            }
        }
    }

    val tallest = measured.maxOfOrNull { it.size.height } ?: 0f
    val rawWidth = measured.sumOf { it.size.width.toDouble() }.toFloat() + totalSpacing
    val finalWidth = if (containerWidth == Float.POSITIVE_INFINITY) rawWidth else containerWidth

    val mainOffsets = computeMainAxisOffsets(
        sizes = measured.map { it.size.width },
        spacing = spacing,
        containerExtent = finalWidth,
        style = node.horizontalArrangement.toMainAxisStyle(),
    )
    val placed = measured.mapIndexed { i, child ->
        val crossOffset = crossAxisOffset(
            childExtent = child.size.height,
            containerExtent = tallest,
            alignment = node.verticalAlignment.toCrossAlignment(),
        )
        PlacedChild(
            node = child,
            offsetX = padding.left.value + mainOffsets[i],
            offsetY = padding.top.value + crossOffset,
        )
    }

    return MeasuredRow(
        children = placed,
        size = Size(
            width = finalWidth + horizontalInset,
            height = tallest + verticalInset,
        ),
        decoration = node.decoration,
    )
}

/** Mirrors the alignment enums into a private representation used by [crossAxisOffset]. */
private enum class CrossAlignment { Start, Center, End }

private fun HorizontalAlignment.toCrossAlignment(): CrossAlignment = when (this) {
    HorizontalAlignment.Start -> CrossAlignment.Start
    HorizontalAlignment.Center -> CrossAlignment.Center
    HorizontalAlignment.End -> CrossAlignment.End
}

private fun VerticalAlignment.toCrossAlignment(): CrossAlignment = when (this) {
    VerticalAlignment.Top -> CrossAlignment.Start
    VerticalAlignment.Center -> CrossAlignment.Center
    VerticalAlignment.Bottom -> CrossAlignment.End
}

private fun crossAxisOffset(
    childExtent: Float,
    containerExtent: Float,
    alignment: CrossAlignment,
): Float {
    val slack = (containerExtent - childExtent).coerceAtLeast(0f)
    return when (alignment) {
        CrossAlignment.Start -> 0f
        CrossAlignment.Center -> slack / 2f
        CrossAlignment.End -> slack
    }
}

private enum class MainAxisStyle { Start, Center, End, SpaceBetween, SpaceAround, SpaceEvenly }

private fun VerticalArrangement.toMainAxisStyle(): MainAxisStyle = when (this) {
    VerticalArrangement.Top -> MainAxisStyle.Start
    VerticalArrangement.Center -> MainAxisStyle.Center
    VerticalArrangement.Bottom -> MainAxisStyle.End
    VerticalArrangement.SpaceBetween -> MainAxisStyle.SpaceBetween
    VerticalArrangement.SpaceAround -> MainAxisStyle.SpaceAround
    VerticalArrangement.SpaceEvenly -> MainAxisStyle.SpaceEvenly
}

private fun HorizontalArrangement.toMainAxisStyle(): MainAxisStyle = when (this) {
    HorizontalArrangement.Start -> MainAxisStyle.Start
    HorizontalArrangement.Center -> MainAxisStyle.Center
    HorizontalArrangement.End -> MainAxisStyle.End
    HorizontalArrangement.SpaceBetween -> MainAxisStyle.SpaceBetween
    HorizontalArrangement.SpaceAround -> MainAxisStyle.SpaceAround
    HorizontalArrangement.SpaceEvenly -> MainAxisStyle.SpaceEvenly
}

private fun computeMainAxisOffsets(
    sizes: List<Float>,
    spacing: Float,
    containerExtent: Float,
    style: MainAxisStyle,
): List<Float> {
    if (sizes.isEmpty()) return emptyList()
    val totalChildren = sizes.sumOf { it.toDouble() }.toFloat()
    val n = sizes.size

    return when (style) {
        MainAxisStyle.Start -> sequentialOffsets(sizes, spacing, leadingPad = 0f)

        MainAxisStyle.Center -> {
            val totalSpacing = spacing * (n - 1).coerceAtLeast(0)
            val pad = ((containerExtent - totalChildren - totalSpacing) / 2f).coerceAtLeast(0f)
            sequentialOffsets(sizes, spacing, leadingPad = pad)
        }

        MainAxisStyle.End -> {
            val totalSpacing = spacing * (n - 1).coerceAtLeast(0)
            val pad = (containerExtent - totalChildren - totalSpacing).coerceAtLeast(0f)
            sequentialOffsets(sizes, spacing, leadingPad = pad)
        }

        MainAxisStyle.SpaceBetween -> {
            if (n <= 1) return sequentialOffsets(sizes, 0f, leadingPad = 0f)
            val gap = ((containerExtent - totalChildren) / (n - 1)).coerceAtLeast(0f)
            sequentialOffsets(sizes, gap, leadingPad = 0f)
        }

        MainAxisStyle.SpaceAround -> {
            val gap = ((containerExtent - totalChildren) / n).coerceAtLeast(0f)
            sequentialOffsets(sizes, gap, leadingPad = gap / 2f)
        }

        MainAxisStyle.SpaceEvenly -> {
            val gap = ((containerExtent - totalChildren) / (n + 1)).coerceAtLeast(0f)
            sequentialOffsets(sizes, gap, leadingPad = gap)
        }
    }
}

/** Sequential offsets with a fixed leading pad and inter-child spacing. */
private fun sequentialOffsets(
    sizes: List<Float>,
    spacing: Float,
    leadingPad: Float,
): List<Float> {
    val out = ArrayList<Float>(sizes.size)
    var cursor = leadingPad
    for ((index, size) in sizes.withIndex()) {
        out += cursor
        cursor += size
        if (index != sizes.lastIndex) cursor += spacing
    }
    return out
}

/**
 * Cross-axis horizontal extent claimed by [child] when an aligning parent
 * (column with non-Start [com.conamobile.pdfkmp.layout.HorizontalAlignment],
 * box with a non-Start [com.conamobile.pdfkmp.layout.BoxAlignment], or table
 * cell with non-Start horizontal alignment) decides where to put it.
 *
 * For most nodes this is just [MeasuredNode.size.width]. For text nodes the
 * picture is subtler: their reported `size.width` is the *intrinsic* width
 * (longest wrapped line), but their internal text alignment shifts glyphs
 * across the full [MeasuredText.paragraphWidth] / [MeasuredRichText.paragraphWidth]
 * slot the layout engine handed them. If the parent then adds another shift
 * based on the intrinsic width, the two stack and the text overflows past
 * its parent. Reporting `paragraphWidth` here makes the parent treat the
 * text as already filling the slot — its own [crossAxisOffset] collapses to
 * zero and the text's internal alignment is the only one that runs.
 *
 * [LinkNode] wrappers are unwrapped so a `link { text(...) }` participates
 * in this dedup the same way a bare `text(...)` does.
 */
private fun effectiveCrossWidth(child: MeasuredNode): Float {
    val target = if (child is MeasuredLink) child.child else child
    return when (target) {
        is MeasuredText ->
            if (target.style.align != TextAlign.Start) maxOf(target.size.width, target.paragraphWidth)
            else target.size.width
        is MeasuredRichText ->
            if (target.align != TextAlign.Start) maxOf(target.size.width, target.paragraphWidth)
            else target.size.width
        else -> child.size.width
    }
}

/**
 * Returns this node with its size widened to at least [minWidth]. Used by
 * weighted children inside a row so that the slot they were given is
 * respected even if their own intrinsic content is narrower.
 */
private fun MeasuredNode.withMinWidth(minWidth: Float): MeasuredNode = when (this) {
    is MeasuredText -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredBlock -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredImage -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredColumn -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredRow -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredTable -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredVector -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredBox -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredDivider -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredRichText -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredShape -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredLink -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredQrCode -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredBarcode -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredBookmark -> this // Zero-size markers never stretch.
    is MeasuredAnchor -> this
    // Form widgets keep their declared box so the static fallback and the
    // interactive widget rectangle stay aligned; weighted slots don't stretch them.
    is MeasuredFormTextField -> this
    is MeasuredFormCheckBox -> this
    is MeasuredInternalLink -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
    is MeasuredKeepTogether -> copy(size = size.copy(width = maxOf(size.width, minWidth)))
}

/** Mirror of [withMinWidth] for the height axis. */
private fun MeasuredNode.withMinHeight(minHeight: Float): MeasuredNode = when (this) {
    is MeasuredText -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredBlock -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredImage -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredColumn -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredRow -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredTable -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredVector -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredBox -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredDivider -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredRichText -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredShape -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredLink -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredQrCode -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredBarcode -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredBookmark -> this // Zero-size markers never stretch.
    is MeasuredAnchor -> this
    is MeasuredFormTextField -> this
    is MeasuredFormCheckBox -> this
    is MeasuredInternalLink -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
    is MeasuredKeepTogether -> copy(size = size.copy(height = maxOf(size.height, minHeight)))
}
