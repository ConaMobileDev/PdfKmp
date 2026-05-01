package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.Constraints
import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.layout.MeasuredBlock
import com.conamobile.pdfkmp.layout.MeasuredBox
import com.conamobile.pdfkmp.layout.MeasuredColumn
import com.conamobile.pdfkmp.layout.MeasuredDivider
import com.conamobile.pdfkmp.layout.MeasuredImage
import com.conamobile.pdfkmp.layout.MeasuredLink
import com.conamobile.pdfkmp.layout.MeasuredNode
import com.conamobile.pdfkmp.layout.MeasuredRichText
import com.conamobile.pdfkmp.layout.MeasuredRow
import com.conamobile.pdfkmp.layout.MeasuredShape
import com.conamobile.pdfkmp.layout.MeasuredTable
import com.conamobile.pdfkmp.layout.MeasuredTableRow
import com.conamobile.pdfkmp.layout.MeasuredText
import com.conamobile.pdfkmp.layout.MeasuredVector
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.layout.TextLine
import com.conamobile.pdfkmp.layout.measure
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.PageContext
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.vector.PathCommand
import com.conamobile.pdfkmp.node.DocumentSpec
import com.conamobile.pdfkmp.node.PageSpec

/**
 * Orchestrates layout and drawing across an entire document.
 *
 * The renderer is platform-agnostic: it talks to a [PdfDriver] through the
 * abstract [PdfCanvas] interface, never to native APIs directly. That keeps
 * the page-break logic, coordinate math, and placement strategy in one place
 * where they can be unit-tested with a fake driver — and makes adding new
 * platforms (desktop, web) a matter of writing a new backend, not touching
 * any common code.
 *
 * Pages flow naturally: the renderer walks the children of the page-level
 * column and decides at each child whether it fits in the remaining
 * vertical space. The decision rule is governed by
 * [PageSpec.pageBreakStrategy]:
 *
 * - [PageBreakStrategy.MoveToNextPage] — the entire child moves to a new
 *   page if it would not fit, leaving blank space at the bottom of the
 *   current page.
 * - [PageBreakStrategy.Slice] — the renderer slices the child at line
 *   boundaries (for text) or at the bottom of the page (for images, once
 *   image rendering is implemented), drawing what fits and continuing the
 *   rest on the next page.
 */
internal object DocumentRenderer {

    /**
     * Lays out [spec] and writes it through [driver]. Returns the encoded PDF
     * bytes by calling [PdfDriver.finish] at the end.
     *
     * When any page in [spec] declares a header or footer, runs a
     * [CountingPdfDriver] dry-run first so the real pass knows the
     * total page count. Documents without headers/footers go straight
     * to the single-pass render.
     */
    fun render(spec: DocumentSpec, driver: PdfDriver): ByteArray {
        val metrics = driver.fontMetrics
        val hasDecoration = spec.pages.any { it.header != null || it.footer != null }
        val totalPages = if (hasDecoration) countTotalPages(spec, metrics) else spec.pages.size
        val state = PageCounter()
        for (page in spec.pages) {
            renderPage(page, driver, metrics, totalPages, state)
        }
        return driver.finish()
    }

    private fun countTotalPages(spec: DocumentSpec, metrics: FontMetrics): Int {
        val countingDriver = CountingPdfDriver(metrics)
        // Use placeholder totalPages = 1 during the count pass; height of
        // headers / footers is fixed per logical page (probed below) so the
        // page count is independent of the value used here.
        val state = PageCounter()
        for (page in spec.pages) {
            renderPage(page, countingDriver, metrics, totalPages = 1, state)
        }
        return countingDriver.pageCount
    }

    private fun renderPage(
        page: PageSpec,
        driver: PdfDriver,
        metrics: FontMetrics,
        totalPages: Int,
        counter: PageCounter,
    ) {
        // Header / footer height stays constant across every physical
        // page we emit for this logical page so layout is stable. Probe
        // with the first page number so a user formatting "Page 1 of N"
        // sees a representative height.
        val probeContext = PageContext(pageNumber = counter.next + 1, totalPages = totalPages)
        val headerHeight = page.header?.invoke(probeContext)?.let { measureColumnHeight(it, page, metrics) } ?: 0f
        val footerHeight = page.footer?.invoke(probeContext)?.let { measureColumnHeight(it, page, metrics) } ?: 0f

        val frame = page.contentFrame(headerHeight = headerHeight, footerHeight = footerHeight)
        val constraints = Constraints(maxWidth = frame.width, maxHeight = frame.height)

        var canvas = driver.beginPage(page.size)
        counter.next += 1
        renderWatermark(page, canvas, metrics)
        var cursorY = frame.top

        val env = PageEnv(
            page = page,
            driver = driver,
            metrics = metrics,
            totalPages = totalPages,
            headerHeight = headerHeight,
            footerHeight = footerHeight,
            counter = counter,
        )

        for ((index, child) in page.content.children.withIndex()) {
            val measured = measure(child, constraints, metrics)
            val resultCanvas = renderChild(
                node = measured,
                env = env,
                frame = frame,
                cursorY = cursorY,
                canvas = canvas,
            )
            canvas = resultCanvas.canvas
            cursorY = resultCanvas.cursorY
            if (index != page.content.children.lastIndex) {
                cursorY += page.content.spacing.value
            }
        }

        renderHeaderFooter(page, canvas, metrics, totalPages, counter.next, headerHeight, footerHeight)
        driver.endPage()
    }

    /**
     * Renders the page's watermark covering the full page area. Called
     * once per physical page right after [PdfDriver.beginPage] so body
     * content paints on top.
     */
    private fun renderWatermark(page: PageSpec, canvas: PdfCanvas, metrics: FontMetrics) {
        val watermark = page.watermark ?: return
        val measured = measure(
            watermark,
            Constraints(maxWidth = page.size.width.value, maxHeight = page.size.height.value),
            metrics,
        )
        place(measured, canvas, 0f, 0f)
    }

    /** Measures the height of a header / footer column at the page width. */
    private fun measureColumnHeight(
        column: com.conamobile.pdfkmp.node.ColumnNode,
        page: PageSpec,
        metrics: FontMetrics,
    ): Float {
        val width = (page.size.width.value - page.padding.left.value - page.padding.right.value)
            .coerceAtLeast(0f)
        return measure(column, Constraints(maxWidth = width), metrics).size.height
    }

    /**
     * Renders the page's header at the top of the page padding band and
     * the footer at the bottom. Both are rebuilt with [pageNumber] /
     * [totalPages] in their [PageContext] so dynamic content like page
     * numbers shows the right values.
     */
    private fun renderHeaderFooter(
        page: PageSpec,
        canvas: PdfCanvas,
        metrics: FontMetrics,
        totalPages: Int,
        pageNumber: Int,
        headerHeight: Float,
        footerHeight: Float,
    ) {
        val ctx = PageContext(pageNumber = pageNumber, totalPages = totalPages)
        val width = (page.size.width.value - page.padding.left.value - page.padding.right.value)
            .coerceAtLeast(0f)
        page.header?.invoke(ctx)?.let { headerColumn ->
            val measured = measure(headerColumn, Constraints(maxWidth = width), metrics)
            place(measured, canvas, page.padding.left.value, page.padding.top.value)
        }
        page.footer?.invoke(ctx)?.let { footerColumn ->
            val measured = measure(footerColumn, Constraints(maxWidth = width), metrics)
            val y = page.size.height.value - page.padding.bottom.value - footerHeight
            place(measured, canvas, page.padding.left.value, y)
        }
    }

    /**
     * Mutable counter passed through the render pipeline so every helper
     * that opens a new physical page (e.g. slicing) can increment it,
     * keeping page numbers in sync with what the driver sees.
     */
    private class PageCounter(var next: Int = 0)

    /**
     * Bundle of per-page rendering state. Threaded through the slicing
     * helpers so they can call [openNewPage] without re-deriving the
     * header / footer reservation or the running page counter.
     */
    private data class PageEnv(
        val page: PageSpec,
        val driver: PdfDriver,
        val metrics: FontMetrics,
        val totalPages: Int,
        val headerHeight: Float,
        val footerHeight: Float,
        val counter: PageCounter,
    )

    /**
     * Places one top-level child of the page, breaking onto a new page when
     * required by the configured [PageBreakStrategy]. Returns the canvas and
     * cursor position to use for the next child.
     */
    private fun renderChild(
        node: MeasuredNode,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
    ): RenderState {
        val available = frame.bottom - cursorY
        val fits = node.size.height <= available
        val isPageEmpty = cursorY == frame.top

        if (fits) {
            place(node, canvas, frame.left, cursorY)
            return RenderState(canvas, cursorY + node.size.height)
        }

        return when (env.page.pageBreakStrategy) {
            PageBreakStrategy.MoveToNextPage -> {
                if (isPageEmpty) {
                    // Element is taller than a full page; we have nowhere to
                    // move it — draw what we have and overflow past the
                    // bottom margin. Splitting an oversized element is the
                    // job of `Slice`.
                    place(node, canvas, frame.left, cursorY)
                    RenderState(canvas, cursorY + node.size.height)
                } else {
                    val newCanvas = openNewPage(env, canvas)
                    place(node, newCanvas, frame.left, frame.top)
                    RenderState(newCanvas, frame.top + node.size.height)
                }
            }

            PageBreakStrategy.Slice -> sliceAcrossPages(
                node = node,
                env = env,
                frame = frame,
                cursorY = cursorY,
                canvas = canvas,
            )
        }
    }

    /**
     * Splits [node] into chunks that each fit on a page and emits them across
     * however many physical pages are needed.
     */
    private fun sliceAcrossPages(
        node: MeasuredNode,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
    ): RenderState = when (node) {
        is MeasuredText -> sliceText(node, env, frame, cursorY, canvas)
        is MeasuredImage -> sliceImage(node, env, frame, cursorY, canvas)
        else -> {
            // TODO: implement slicing for MeasuredColumn (recursive). Until
            //  then we degrade gracefully to MoveToNextPage so the document
            //  still produces a valid output.
            val newCanvas = openNewPage(env, canvas)
            place(node, newCanvas, frame.left, frame.top)
            RenderState(newCanvas, frame.top + node.size.height)
        }
    }

    private fun sliceText(
        node: MeasuredText,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
    ): RenderState {
        var currentCanvas = canvas
        var currentTop = cursorY
        var remaining = node.lines

        while (remaining.isNotEmpty()) {
            val available = frame.bottom - currentTop
            val (fitting, overflow) = splitLinesByHeight(remaining, available)

            if (fitting.isEmpty()) {
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
                continue
            }

            val chunk = MeasuredText(
                lines = fitting,
                style = node.style,
                size = Size(width = node.size.width, height = fitting.sumOf { it.height.toDouble() }.toFloat()),
                paragraphWidth = node.paragraphWidth,
            )
            place(chunk, currentCanvas, frame.left, currentTop)

            if (overflow.isEmpty()) {
                return RenderState(currentCanvas, currentTop + chunk.size.height)
            }

            currentCanvas = openNewPage(env, currentCanvas)
            currentTop = frame.top
            remaining = overflow
        }

        return RenderState(currentCanvas, currentTop)
    }

    private fun sliceImage(
        node: MeasuredImage,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
    ): RenderState {
        val totalHeight = node.size.height
        if (totalHeight <= 0f) return RenderState(canvas, cursorY)

        var currentCanvas = canvas
        var currentTop = cursorY
        var consumed = 0f

        while (consumed < totalHeight) {
            val available = frame.bottom - currentTop
            if (available <= 0f) {
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
                continue
            }
            val chunkHeight = minOf(available, totalHeight - consumed)
            val srcTop = consumed / totalHeight
            val srcBottom = (consumed + chunkHeight) / totalHeight
            currentCanvas.drawImage(
                bytes = node.bytes,
                x = frame.left,
                y = currentTop,
                width = node.size.width,
                height = chunkHeight,
                contentScale = node.contentScale,
                sourceTop = srcTop,
                sourceBottom = srcBottom,
                allowDownScale = node.allowDownScale,
            )
            consumed += chunkHeight
            currentTop += chunkHeight
            if (consumed < totalHeight) {
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
            }
        }

        return RenderState(currentCanvas, currentTop)
    }

    private fun splitLinesByHeight(
        lines: List<TextLine>,
        available: Float,
    ): Pair<List<TextLine>, List<TextLine>> {
        var consumed = 0f
        val fitting = mutableListOf<TextLine>()
        var firstOverflowIndex = lines.size
        for ((index, line) in lines.withIndex()) {
            if (consumed + line.height > available) {
                firstOverflowIndex = index
                break
            }
            consumed += line.height
            fitting += line
        }
        val overflow = if (firstOverflowIndex >= lines.size) emptyList() else lines.subList(firstOverflowIndex, lines.size)
        return fitting to overflow
    }

    /**
     * Closes the current physical page (rendering its header / footer
     * first) and opens a fresh one. Used by all the slicing paths so
     * every page gets its own [PageContext]-aware decoration.
     */
    private fun openNewPage(env: PageEnv, currentCanvas: PdfCanvas): PdfCanvas {
        renderHeaderFooter(
            page = env.page,
            canvas = currentCanvas,
            metrics = env.metrics,
            totalPages = env.totalPages,
            pageNumber = env.counter.next,
            headerHeight = env.headerHeight,
            footerHeight = env.footerHeight,
        )
        env.driver.endPage()
        val canvas = env.driver.beginPage(env.page.size)
        env.counter.next += 1
        renderWatermark(env.page, canvas, env.metrics)
        return canvas
    }

    private fun place(node: MeasuredNode, canvas: PdfCanvas, originX: Float, originY: Float) {
        when (node) {
            is MeasuredText -> placeText(node, canvas, originX, originY)
            is MeasuredImage -> canvas.drawImage(
                bytes = node.bytes,
                x = originX,
                y = originY,
                width = node.size.width,
                height = node.size.height,
                contentScale = node.contentScale,
                allowDownScale = node.allowDownScale,
            )
            is MeasuredBlock -> Unit // Spacers contribute size only.
            is MeasuredDivider -> {
                // Center the stroke vertically in the line's allocated height.
                val y = originY + node.thickness / 2f
                canvas.drawLine(
                    x1 = originX,
                    y1 = y,
                    x2 = originX + node.size.width,
                    y2 = y,
                    color = node.color,
                    thickness = node.thickness,
                    style = node.style,
                )
            }
            is MeasuredColumn -> placeContainer(
                decoration = node.decoration,
                canvas = canvas,
                originX = originX, originY = originY,
                width = node.size.width, height = node.size.height,
                children = node.children,
            )
            is MeasuredRow -> placeContainer(
                decoration = node.decoration,
                canvas = canvas,
                originX = originX, originY = originY,
                width = node.size.width, height = node.size.height,
                children = node.children,
            )
            is MeasuredBox -> placeContainer(
                decoration = node.decoration,
                canvas = canvas,
                originX = originX, originY = originY,
                width = node.size.width, height = node.size.height,
                children = node.children,
            )
            is MeasuredTable -> placeTable(node, canvas, originX, originY)
            is MeasuredVector -> placeVector(node, canvas, originX, originY)
            is MeasuredRichText -> placeRichText(node, canvas, originX, originY)
            is MeasuredShape -> placeShape(node, canvas, originX, originY)
            is MeasuredLink -> {
                place(node.child, canvas, originX, originY)
                canvas.linkAnnotation(
                    x = originX, y = originY,
                    width = node.size.width, height = node.size.height,
                    url = node.url,
                )
            }
        }
    }

    /**
     * Draws a [MeasuredShape] (circle / ellipse) by generating the
     * 4-cubic-Bézier path that approximates it and handing it to
     * [PdfCanvas.drawPath]. Circles fit a circle whose diameter equals
     * `min(width, height)`, centred inside the measurement rectangle so
     * they stay round even when placed in a non-square slot.
     */
    private fun placeShape(node: MeasuredShape, canvas: PdfCanvas, originX: Float, originY: Float) {
        val w: Float
        val h: Float
        val x: Float
        val y: Float
        when (node.shape) {
            com.conamobile.pdfkmp.node.Shape.Circle -> {
                val diameter = minOf(node.size.width, node.size.height)
                w = diameter
                h = diameter
                x = originX + (node.size.width - diameter) / 2f
                y = originY + (node.size.height - diameter) / 2f
            }
            com.conamobile.pdfkmp.node.Shape.Ellipse -> {
                w = node.size.width
                h = node.size.height
                x = originX
                y = originY
            }
        }
        val path = buildEllipsePath(x, y, w, h)
        if (path.isEmpty()) return
        val translatedFill = node.fill?.translatedTo(originX, originY)
        canvas.drawPath(
            commands = path,
            fill = translatedFill,
            strokeColor = node.strokeColor,
            strokeWidth = node.strokeWidth,
        )
    }

    /**
     * Generic placement helper used by every decorated container
     * ([MeasuredColumn], [MeasuredRow], [MeasuredBox]).
     *
     * Drawing order:
     * 1. Save canvas state.
     * 2. Fill the rectangle (rounded if [ContainerDecoration.cornerRadius] > 0
     *    or [ContainerDecoration.cornerRadiusEach] is set) with
     *    [ContainerDecoration.background] when set.
     * 3. Clip subsequent draws to the same rounded shape so children
     *    never bleed past the corners.
     * 4. Place each child at its pre-computed offset.
     * 5. Restore the canvas state to drop the clip.
     * 6. Stroke the outline. When
     *    [ContainerDecoration.borderEach] is non-null each side is stroked
     *    independently; otherwise the uniform border is drawn.
     */
    private fun placeContainer(
        decoration: ContainerDecoration,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        children: List<com.conamobile.pdfkmp.layout.PlacedChild>,
    ) {
        val cornerEach = decoration.cornerRadiusEach
        val perCornerPath: List<PathCommand>? = if (cornerEach != null && cornerEach.hasAnyRadius()) {
            buildRoundedRectPath(
                x = originX, y = originY, width = width, height = height,
                tl = cornerEach.topLeft.value,
                tr = cornerEach.topRight.value,
                bl = cornerEach.bottomLeft.value,
                br = cornerEach.bottomRight.value,
            )
        } else null
        val uniformRadius = decoration.cornerRadius.value
        val needsClip = perCornerPath != null || uniformRadius > 0f || decoration.clipToBounds

        if (needsClip) canvas.saveState()
        try {
            val paintFill: PdfPaint? = decoration.backgroundPaint?.translatedTo(originX, originY)
                ?: decoration.background?.let { PdfPaint.Solid(it) }
            paintFill?.let { fill ->
                val path = perCornerPath ?: rectanglePath(originX, originY, width, height, uniformRadius)
                if (fill is PdfPaint.Solid && perCornerPath == null) {
                    when {
                        uniformRadius > 0f ->
                            canvas.drawRoundedRect(originX, originY, width, height, uniformRadius, fill.color)
                        else -> canvas.drawRect(originX, originY, width, height, fill.color)
                    }
                } else {
                    canvas.drawPath(
                        commands = path,
                        fill = fill,
                        strokeColor = null,
                        strokeWidth = 0f,
                    )
                }
            }
            if (needsClip) {
                when {
                    perCornerPath != null -> canvas.clipPath(perCornerPath)
                    uniformRadius > 0f ->
                        canvas.clipRoundedRect(originX, originY, width, height, uniformRadius)
                    // clipToBounds without rounded corners — sharp rectangle clip.
                    else -> canvas.clipRect(originX, originY, width, height)
                }
            }
            for (child in children) {
                place(child.node, canvas, originX + child.offsetX, originY + child.offsetY)
            }
        } finally {
            if (needsClip) canvas.restoreState()
        }

        val borderEach = decoration.borderEach
        if (borderEach != null) {
            drawPerSideBorder(canvas, originX, originY, width, height, borderEach)
        } else {
            decoration.border?.let { border ->
                val strokeWidth = border.width.value
                if (strokeWidth > 0f) {
                    when {
                        perCornerPath != null -> canvas.drawPath(
                            commands = perCornerPath,
                            fill = null,
                            strokeColor = border.color,
                            strokeWidth = strokeWidth,
                        )
                        uniformRadius > 0f -> canvas.strokeRoundedRect(
                            originX, originY, width, height, uniformRadius, border.color, strokeWidth,
                        )
                        else -> canvas.strokeRect(originX, originY, width, height, border.color, strokeWidth)
                    }
                }
            }
        }
    }

    private fun com.conamobile.pdfkmp.style.CornerRadius.hasAnyRadius(): Boolean =
        topLeft.value > 0f || topRight.value > 0f || bottomLeft.value > 0f || bottomRight.value > 0f

    /**
     * Builds a path describing the outer rectangle of a container — sharp
     * when [uniformRadius] is `0f`, uniformly rounded otherwise. Used as
     * the fallback shape when a gradient fill needs to be passed through
     * [PdfCanvas.drawPath].
     */
    private fun rectanglePath(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        uniformRadius: Float,
    ): List<PathCommand> = if (uniformRadius > 0f) {
        buildRoundedRectPath(x, y, width, height, uniformRadius, uniformRadius, uniformRadius, uniformRadius)
    } else {
        listOf(
            PathCommand.MoveTo(x, y),
            PathCommand.LineTo(x + width, y),
            PathCommand.LineTo(x + width, y + height),
            PathCommand.LineTo(x, y + height),
            PathCommand.Close,
        )
    }

    /**
     * Shifts gradient coordinates from a container's local space into the
     * page's absolute coordinate space. Local `(0, 0)` becomes the
     * container's `(originX, originY)`. Solid paints don't need translation.
     */
    private fun PdfPaint.translatedTo(originX: Float, originY: Float): PdfPaint = when (this) {
        is PdfPaint.Solid -> this
        is PdfPaint.LinearGradient -> copy(
            startX = originX + startX,
            startY = originY + startY,
            endX = originX + endX,
            endY = originY + endY,
        )
        is PdfPaint.RadialGradient -> copy(
            centerX = originX + centerX,
            centerY = originY + centerY,
        )
    }

    /**
     * Draws each non-null side of [sides] as an independent line, so a
     * container can have e.g. only a bottom rule or different colours per
     * side. Each stroke runs along the rectangle edge with no corner
     * mitering — fine for typical "underline a row" patterns.
     */
    private fun drawPerSideBorder(
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        sides: com.conamobile.pdfkmp.style.BorderSides,
    ) {
        sides.top?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY,
                x2 = originX + width, y2 = originY,
                color = it.color, thickness = w,
            )
        }
        sides.right?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX + width, y1 = originY,
                x2 = originX + width, y2 = originY + height,
                color = it.color, thickness = w,
            )
        }
        sides.bottom?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY + height,
                x2 = originX + width, y2 = originY + height,
                color = it.color, thickness = w,
            )
        }
        sides.left?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY,
                x2 = originX, y2 = originY + height,
                color = it.color, thickness = w,
            )
        }
    }

    /**
     * Draws a vector graphic by transforming each path command from the
     * source viewport into the destination rectangle and stroking / filling
     * via [PdfCanvas.drawPath].
     *
     * Aspect ratios are preserved by computing a uniform `scale` from the
     * viewport-to-destination ratio. [MeasuredVector.tint] overrides the
     * fill colour of every path when set.
     */
    private fun placeVector(node: MeasuredVector, canvas: PdfCanvas, originX: Float, originY: Float) {
        val viewportWidth = node.image.viewportWidth
        val viewportHeight = node.image.viewportHeight
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        val scaleX = node.size.width / viewportWidth
        val scaleY = node.size.height / viewportHeight
        for (path in node.image.paths) {
            val transformed = path.commands.map { transformCommand(it, originX, originY, scaleX, scaleY) }
            val fill: PdfPaint? = when {
                node.tint != null -> PdfPaint.Solid(node.tint)
                else -> path.fill?.transformedTo(originX, originY, scaleX, scaleY)
            }
            val strokeColor = when (val mode = node.strokeOverride) {
                VectorStrokeMode.Inherit -> path.strokeColor
                VectorStrokeMode.Disabled -> null
                is VectorStrokeMode.Tint -> path.strokeColor?.let { mode.color }
            }
            canvas.drawPath(
                commands = transformed,
                fill = fill,
                strokeColor = strokeColor,
                strokeWidth = if (strokeColor != null) path.strokeWidth * minOf(scaleX, scaleY) else 0f,
            )
        }
    }

    /**
     * Maps a [PdfPaint]'s coordinates from the vector's viewport space
     * into the destination rectangle the path is being drawn into. Solid
     * paints are returned as-is.
     */
    private fun PdfPaint.transformedTo(
        originX: Float,
        originY: Float,
        scaleX: Float,
        scaleY: Float,
    ): PdfPaint = when (this) {
        is PdfPaint.Solid -> this
        is PdfPaint.LinearGradient -> copy(
            startX = originX + startX * scaleX,
            startY = originY + startY * scaleY,
            endX = originX + endX * scaleX,
            endY = originY + endY * scaleY,
        )
        is PdfPaint.RadialGradient -> copy(
            centerX = originX + centerX * scaleX,
            centerY = originY + centerY * scaleY,
            radius = radius * minOf(scaleX, scaleY),
        )
    }

    private fun transformCommand(
        cmd: PathCommand,
        originX: Float,
        originY: Float,
        scaleX: Float,
        scaleY: Float,
    ): PathCommand = when (cmd) {
        is PathCommand.MoveTo -> PathCommand.MoveTo(originX + cmd.x * scaleX, originY + cmd.y * scaleY)
        is PathCommand.LineTo -> PathCommand.LineTo(originX + cmd.x * scaleX, originY + cmd.y * scaleY)
        is PathCommand.CubicTo -> PathCommand.CubicTo(
            c1x = originX + cmd.c1x * scaleX,
            c1y = originY + cmd.c1y * scaleY,
            c2x = originX + cmd.c2x * scaleX,
            c2y = originY + cmd.c2y * scaleY,
            x = originX + cmd.x * scaleX,
            y = originY + cmd.y * scaleY,
        )
        is PathCommand.QuadTo -> PathCommand.QuadTo(
            cx = originX + cmd.cx * scaleX,
            cy = originY + cmd.cy * scaleY,
            x = originX + cmd.x * scaleX,
            y = originY + cmd.y * scaleY,
        )
        PathCommand.Close -> PathCommand.Close
    }

    /**
     * Two-phase table drawing:
     *
     * 1. Inside a clipping region matching the (possibly rounded) outer
     *    rectangle, fill row backgrounds, fill cell backgrounds, draw cell
     *    contents, and stroke the inner separator lines. Clipping ensures
     *    nothing pokes out past a rounded corner.
     * 2. After [PdfCanvas.restoreState] removes the clip, stroke the outer
     *    border so the rounded outline itself is not clipped to its own
     *    interior.
     */
    private fun placeTable(node: MeasuredTable, canvas: PdfCanvas, originX: Float, originY: Float) {
        val tableWidth = node.size.width
        val tableHeight = node.size.height

        canvas.saveState()
        try {
            if (node.cornerRadius > 0f) {
                canvas.clipRoundedRect(originX, originY, tableWidth, tableHeight, node.cornerRadius)
            } else {
                canvas.clipRect(originX, originY, tableWidth, tableHeight)
            }

            var rowY = originY
            for (row in node.rows) {
                drawTableRow(row, canvas, originX, rowY)
                rowY += row.height
            }

            if (node.border.showHorizontalLines && node.borderWidth > 0f) {
                var sepY = originY
                for ((index, row) in node.rows.withIndex()) {
                    sepY += row.height
                    if (index == node.rows.lastIndex) break
                    canvas.drawLine(
                        x1 = originX,
                        y1 = sepY,
                        x2 = originX + tableWidth,
                        y2 = sepY,
                        color = node.borderColor,
                        thickness = node.borderWidth,
                    )
                }
            }

            if (node.border.showVerticalLines && node.borderWidth > 0f) {
                var lineX = originX
                for ((index, columnWidth) in node.columnWidths.withIndex()) {
                    lineX += columnWidth
                    if (index == node.columnWidths.lastIndex) break
                    canvas.drawLine(
                        x1 = lineX,
                        y1 = originY,
                        x2 = lineX,
                        y2 = originY + tableHeight,
                        color = node.borderColor,
                        thickness = node.borderWidth,
                    )
                }
            }
        } finally {
            canvas.restoreState()
        }

        if (node.border.showOutline && node.borderWidth > 0f) {
            if (node.cornerRadius > 0f) {
                canvas.strokeRoundedRect(
                    x = originX,
                    y = originY,
                    width = tableWidth,
                    height = tableHeight,
                    cornerRadius = node.cornerRadius,
                    color = node.borderColor,
                    thickness = node.borderWidth,
                )
            } else {
                canvas.strokeRect(
                    x = originX,
                    y = originY,
                    width = tableWidth,
                    height = tableHeight,
                    color = node.borderColor,
                    thickness = node.borderWidth,
                )
            }
        }
    }

    private fun drawTableRow(
        row: MeasuredTableRow,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
    ) {
        val tableWidth = row.cells.sumOf { it.width.toDouble() }.toFloat()
        row.background?.let { fill ->
            canvas.drawRect(originX, originY, tableWidth, row.height, fill)
        }
        for (cell in row.cells) {
            val cellX = originX + cell.offsetX
            cell.style.background?.let { cellFill ->
                canvas.drawRect(cellX, originY, cell.width, row.height, cellFill)
            }
            place(
                node = cell.content,
                canvas = canvas,
                originX = cellX + cell.contentOffsetX,
                originY = originY + cell.contentOffsetY,
            )
        }
    }

    private fun placeText(node: MeasuredText, canvas: PdfCanvas, originX: Float, originY: Float) {
        var lineTop = originY
        for (line in node.lines) {
            val lineX = originX + alignmentOffsetForLine(node, line)
            // Decoration lines (underline, strikethrough) scale with font
            // size — small for body text, weighty for display sizes.
            val decorationThickness = (node.style.fontSize.value * 0.06f).coerceAtLeast(0.5f)
            canvas.drawText(text = line.text, x = lineX, y = lineTop, style = node.style)
            drawTextDecorations(
                line = line,
                style = node.style,
                canvas = canvas,
                lineLeft = lineX,
                lineWidth = line.width,
                lineTop = lineTop,
                decorationThickness = decorationThickness,
            )
            lineTop += line.height
        }
    }

    /**
     * Returns how far to shift [line] from the paragraph's left edge so
     * it obeys [com.conamobile.pdfkmp.style.TextAlign].
     *
     * `Justify` falls back to `Start` for now — proper justification
     * requires per-word width metrics that the v1 layout pipeline does
     * not surface. Tracked as a follow-up; for `Start`, `Center`, and
     * `End` the rendering is exact.
     */
    private fun alignmentOffsetForLine(node: MeasuredText, line: TextLine): Float {
        val slack = (node.paragraphWidth - line.width).coerceAtLeast(0f)
        return when (node.style.align) {
            com.conamobile.pdfkmp.style.TextAlign.Start -> 0f
            com.conamobile.pdfkmp.style.TextAlign.Center -> slack / 2f
            com.conamobile.pdfkmp.style.TextAlign.End -> slack
            com.conamobile.pdfkmp.style.TextAlign.Justify -> 0f // TODO: per-word spacing
        }
    }

    /**
     * Draws every wrapped line of a [MeasuredRichText], one
     * `drawText` call per styled segment so each span keeps its own
     * font weight, colour, decorations, etc.
     *
     * Alignment offsets the line itself (not individual segments) so
     * `Center` / `End` / `Justify` behave the same as for plain text.
     */
    private fun placeRichText(
        node: MeasuredRichText,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
    ) {
        var lineTop = originY
        for (line in node.lines) {
            val slack = (node.paragraphWidth - line.totalWidth).coerceAtLeast(0f)
            val lineLeft = originX + when (node.align) {
                com.conamobile.pdfkmp.style.TextAlign.Start -> 0f
                com.conamobile.pdfkmp.style.TextAlign.Center -> slack / 2f
                com.conamobile.pdfkmp.style.TextAlign.End -> slack
                com.conamobile.pdfkmp.style.TextAlign.Justify -> 0f // TODO: per-word spacing
            }
            for (segment in line.segments) {
                val segmentX = lineLeft + segment.xOffset
                canvas.drawText(
                    text = segment.text,
                    x = segmentX,
                    y = lineTop,
                    style = segment.style,
                )
                val decorationThickness = (segment.style.fontSize.value * 0.06f).coerceAtLeast(0.5f)
                val asTextLine = TextLine(
                    text = segment.text,
                    width = segment.width,
                    baseline = line.baseline,
                    height = line.height,
                )
                drawTextDecorations(
                    line = asTextLine,
                    style = segment.style,
                    canvas = canvas,
                    lineLeft = segmentX,
                    lineWidth = segment.width,
                    lineTop = lineTop,
                    decorationThickness = decorationThickness,
                )
            }
            lineTop += line.height
        }
    }

    private fun drawTextDecorations(
        line: TextLine,
        style: TextStyle,
        canvas: PdfCanvas,
        lineLeft: Float,
        lineWidth: Float,
        lineTop: Float,
        decorationThickness: Float,
    ) {
        if (style.underline) {
            val y = lineTop + line.baseline + style.fontSize.value * 0.12f
            canvas.drawLine(
                x1 = lineLeft, y1 = y,
                x2 = lineLeft + lineWidth, y2 = y,
                color = style.color, thickness = decorationThickness,
            )
        }
        if (style.strikethrough) {
            val y = lineTop + line.baseline - style.fontSize.value * 0.30f
            canvas.drawLine(
                x1 = lineLeft, y1 = y,
                x2 = lineLeft + lineWidth, y2 = y,
                color = style.color, thickness = decorationThickness,
            )
        }
    }

    private fun PageSpec.contentFrame(
        headerHeight: Float = 0f,
        footerHeight: Float = 0f,
    ): ContentFrame {
        val pageWidth = size.width.value
        val pageHeight = size.height.value
        val left = padding.left.value
        val top = padding.top.value + headerHeight
        val right = pageWidth - padding.right.value
        val bottom = pageHeight - padding.bottom.value - footerHeight
        return ContentFrame(
            left = left,
            top = top,
            width = (right - left).coerceAtLeast(0f),
            height = (bottom - top).coerceAtLeast(0f),
        )
    }

    private data class ContentFrame(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) {
        val bottom: Float get() = top + height
    }

    private data class RenderState(val canvas: PdfCanvas, val cursorY: Float)
}
