package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.Constraints
import com.conamobile.pdfkmp.geometry.Size
import com.conamobile.pdfkmp.layout.MeasuredAnchor
import com.conamobile.pdfkmp.layout.MeasuredBarcode
import com.conamobile.pdfkmp.layout.MeasuredBlock
import com.conamobile.pdfkmp.layout.MeasuredBookmark
import com.conamobile.pdfkmp.layout.MeasuredInternalLink
import com.conamobile.pdfkmp.layout.MeasuredKeepTogether
import com.conamobile.pdfkmp.layout.MeasuredBox
import com.conamobile.pdfkmp.layout.MeasuredColumn
import com.conamobile.pdfkmp.layout.MeasuredDataMatrix
import com.conamobile.pdfkmp.layout.MeasuredDivider
import com.conamobile.pdfkmp.layout.MeasuredFormCheckBox
import com.conamobile.pdfkmp.layout.MeasuredFormTextField
import com.conamobile.pdfkmp.layout.MeasuredImage
import com.conamobile.pdfkmp.layout.MeasuredLink
import com.conamobile.pdfkmp.layout.MeasuredNode
import com.conamobile.pdfkmp.layout.MeasuredQrCode
import com.conamobile.pdfkmp.layout.MeasuredRichText
import com.conamobile.pdfkmp.layout.MeasuredRow
import com.conamobile.pdfkmp.layout.MeasuredShape
import com.conamobile.pdfkmp.layout.MeasuredTable
import com.conamobile.pdfkmp.layout.MeasuredTableRow
import com.conamobile.pdfkmp.layout.MeasuredText
import com.conamobile.pdfkmp.layout.MeasuredVector
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.layout.PlacedChild
import com.conamobile.pdfkmp.layout.TextLine
import com.conamobile.pdfkmp.layout.measure
import com.conamobile.pdfkmp.node.ColumnNode
import com.conamobile.pdfkmp.node.ContainerDecoration
import com.conamobile.pdfkmp.node.PageContext
import com.conamobile.pdfkmp.node.Shape
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.DropShadow
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.sp
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
 *   boundaries (for text) or at the bottom of the page (for images),
 *   drawing what fits and continuing the rest on the next page.
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
        val effectiveSpec: DocumentSpec
        var precomputedTotal: Int? = null
        if (spec.containsToc()) {
            // Two-stage TOC resolution. Stage 1: expand with placeholder
            // page numbers (entry count and heights are already final) and
            // dry-run to learn where every bookmark anchor lands. Stage 2:
            // re-expand with the real numbers and render that.
            val bookmarks = collectBookmarks(spec)
            val placeholderEntries = bookmarks.mapIndexed { index, (title, level) ->
                TocEntry(index = index, title = title, level = level, pageNumber = 0)
            }
            val countingDriver = CountingPdfDriver(metrics, trackDestinations = true)
            runCountingPass(expandToc(spec, placeholderEntries), countingDriver)
            val resolvedEntries = bookmarks.mapIndexed { index, (title, level) ->
                TocEntry(
                    index = index,
                    title = title,
                    level = level,
                    pageNumber = countingDriver.destinationPages[TOC_ANCHOR_PREFIX + index] ?: 0,
                )
            }
            effectiveSpec = expandToc(spec, resolvedEntries)
            precomputedTotal = countingDriver.pageCount
        } else {
            effectiveSpec = spec
        }

        val hasDecoration = effectiveSpec.pages.any { it.header != null || it.footer != null }
        val totalPages = precomputedTotal
            ?: if (hasDecoration) countTotalPages(effectiveSpec, metrics) else effectiveSpec.pages.size
        val state = PageCounter()
        try {
            for (page in effectiveSpec.pages) {
                renderPage(page, driver, metrics, totalPages, state)
            }
            return driver.finish()
        } catch (t: Throwable) {
            // A draw call threw before finish() could release the backend's
            // resources (e.g. PdfBox's PDDocument). Close it best-effort so we
            // don't leak native / file handles, then rethrow the original.
            try {
                driver.close()
            } catch (_: Throwable) {
                // Ignore cleanup failures; the original exception wins.
            }
            throw t
        }
    }

    private fun countTotalPages(spec: DocumentSpec, metrics: FontMetrics): Int {
        val countingDriver = CountingPdfDriver(metrics)
        runCountingPass(spec, countingDriver)
        return countingDriver.pageCount
    }

    /**
     * Renders [spec] through [countingDriver] without emitting output.
     * Uses placeholder totalPages = 1 — header / footer heights are fixed
     * per logical page, so the page count is independent of the value.
     */
    private fun runCountingPass(spec: DocumentSpec, countingDriver: CountingPdfDriver) {
        val state = PageCounter()
        for (page in spec.pages) {
            renderPage(page, countingDriver, countingDriver.fontMetrics, totalPages = 1, state)
        }
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
        column: ColumnNode,
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
     * Places one child of the page, breaking onto a new page when required
     * by the configured [PageBreakStrategy]. Returns the canvas and cursor
     * position to use for the next child.
     *
     * @param xOffset horizontal shift from the frame's left edge. Zero for
     *   top-level page children; non-zero when a sliced column re-renders
     *   its children at their aligned offsets.
     */
    private fun renderChild(
        node: MeasuredNode,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
        xOffset: Float = 0f,
    ): RenderState {
        val available = frame.bottom - cursorY
        val fits = node.size.height <= available
        val isPageEmpty = cursorY == frame.top

        if (fits) {
            place(node, canvas, frame.left + xOffset, cursorY)
            return RenderState(canvas, cursorY + node.size.height)
        }

        return when (env.page.pageBreakStrategy) {
            PageBreakStrategy.MoveToNextPage -> {
                if (isPageEmpty) {
                    // Element is taller than a full page; we have nowhere
                    // to move it. Images and vectors scale down to fit —
                    // overflowing past the bottom margin clips them in
                    // most PDF readers, which always reads as a bug.
                    // Other node types still overflow; splitting them is
                    // the job of `Slice`.
                    val fitted = fitOversizeToHeight(node, available)
                    place(fitted, canvas, frame.left + xOffset, cursorY)
                    RenderState(canvas, cursorY + fitted.size.height)
                } else {
                    val newCanvas = openNewPage(env, canvas)
                    val fitted = fitOversizeToHeight(node, frame.height)
                    place(fitted, newCanvas, frame.left + xOffset, frame.top)
                    RenderState(newCanvas, frame.top + fitted.size.height)
                }
            }

            PageBreakStrategy.Slice -> sliceAcrossPages(
                node = node,
                env = env,
                frame = frame,
                cursorY = cursorY,
                canvas = canvas,
                xOffset = xOffset,
            )
        }
    }

    /**
     * Scales an image / vector that is taller than the available frame
     * down so it fits, preserving aspect ratio. Other node types are
     * returned unchanged — splitting them across pages is
     * [PageBreakStrategy.Slice]'s job, not a silent rescale.
     */
    private fun fitOversizeToHeight(node: MeasuredNode, maxHeight: Float): MeasuredNode = when {
        maxHeight <= 0f || node.size.height <= maxHeight -> node
        node is MeasuredImage -> {
            val scale = maxHeight / node.size.height
            node.copy(size = Size(width = node.size.width * scale, height = maxHeight))
        }
        node is MeasuredVector -> {
            val scale = maxHeight / node.size.height
            node.copy(size = Size(width = node.size.width * scale, height = maxHeight))
        }
        else -> node
    }

    /**
     * Splits [node] into chunks that each fit on a page and emits them across
     * however many physical pages are needed.
     *
     * Sliceable nodes: text (by line), images (by source window), columns
     * (recursively, by child), and tables (by row, with an optional
     * repeated header). Everything else — rows, boxes, shapes — moves to
     * the next page whole, because splitting horizontal or z-stacked
     * content vertically has no sensible general answer.
     */
    private fun sliceAcrossPages(
        node: MeasuredNode,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
        xOffset: Float = 0f,
    ): RenderState = when (node) {
        is MeasuredText -> sliceText(node, env, frame, cursorY, canvas, xOffset)
        is MeasuredImage -> sliceImage(node, env, frame, cursorY, canvas, xOffset)
        is MeasuredColumn -> sliceColumn(node, env, frame, cursorY, canvas, xOffset)
        is MeasuredTable -> sliceTable(node, env, frame, cursorY, canvas, xOffset)
        // keepTogether { } exists precisely to opt out of slicing — the
        // wrapped content falls through to the move-whole branch below.
        else -> {
            val newCanvas = openNewPage(env, canvas)
            place(node, newCanvas, frame.left + xOffset, frame.top)
            RenderState(newCanvas, frame.top + node.size.height)
        }
    }

    /**
     * Slices a column by walking its already-measured children in flow
     * order and pushing each through [renderChild], so an oversized child
     * (a long paragraph, a nested column, a tall table) recursively slices
     * with the same rules as a top-level node.
     *
     * Decorated columns (background / border / corner radius) are not
     * sliced — a fill or outline cut in half at an arbitrary page boundary
     * reads as a rendering bug, so they fall back to moving whole, and to
     * a plain overflow when even a full page can't hold them.
     */
    private fun sliceColumn(
        node: MeasuredColumn,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
        xOffset: Float,
    ): RenderState {
        if (node.decoration != ContainerDecoration.None) {
            val isPageEmpty = cursorY == frame.top
            return if (isPageEmpty) {
                place(node, canvas, frame.left + xOffset, cursorY)
                RenderState(canvas, cursorY + node.size.height)
            } else {
                val newCanvas = openNewPage(env, canvas)
                place(node, newCanvas, frame.left + xOffset, frame.top)
                RenderState(newCanvas, frame.top + node.size.height)
            }
        }

        var state = RenderState(canvas, cursorY)
        // Children carry pre-computed offsets; re-derive the inter-child
        // gaps from them so arrangement spacing survives the re-flow.
        var previousBottom = 0f
        for (placed in node.children) {
            val gap = (placed.offsetY - previousBottom).coerceAtLeast(0f)
            var childTop = state.cursorY + gap
            if (childTop >= frame.bottom && placed.node.size.height > 0f) {
                state = RenderState(openNewPage(env, state.canvas), frame.top)
                childTop = frame.top
            }
            state = renderChild(
                node = placed.node,
                env = env,
                frame = frame,
                cursorY = childTop,
                canvas = state.canvas,
                xOffset = xOffset + placed.offsetX,
            )
            previousBottom = placed.offsetY + placed.node.size.height
        }
        return state
    }

    /**
     * Slices a table between rows: each page receives as many complete
     * rows as fit, and — when [MeasuredTable.repeatHeader] is set — the
     * header row is re-drawn at the top of every continuation page. A
     * single row taller than a full page is drawn anyway and overflows;
     * splitting inside a row would tear its cell content.
     */
    private fun sliceTable(
        node: MeasuredTable,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
        xOffset: Float,
    ): RenderState {
        val hasHeader = node.rows.firstOrNull()?.isHeader == true
        val header = if (hasHeader) node.rows.first() else null
        val headerOwners = if (hasHeader) node.cellOwners.getOrNull(0) else null
        val firstBodyIndex = if (hasHeader) 1 else 0

        // Group body rows into atomic blocks so a rowspan is never split
        // across a page boundary. A block ends only at a row index where no
        // spanning cell reaches past it. Single-row tables collapse to one
        // block per row, reproducing the old per-row chunking.
        val groups = atomicBodyGroups(node, firstBodyIndex)

        var currentCanvas = canvas
        var currentTop = cursorY
        var remaining = groups
        var isFirstChunk = true

        while (remaining.isNotEmpty()) {
            val available = frame.bottom - currentTop
            val chunkGroups = mutableListOf<BodyGroup>()
            var chunkHeight = 0f

            // Continuation pages re-draw the header when requested.
            val includeHeader = header != null && (isFirstChunk || node.repeatHeader)
            if (includeHeader) chunkHeight += header.height

            for (group in remaining) {
                if (chunkHeight + group.height <= available) {
                    chunkGroups += group
                    chunkHeight += group.height
                    continue
                }
                // The block doesn't fit. If this chunk has no body yet and
                // the page is fresh, the block can never fit anywhere — take
                // it alone and let it overflow rather than loop forever.
                if (chunkGroups.isEmpty() && currentTop == frame.top) {
                    chunkGroups += group
                    chunkHeight += group.height
                }
                break
            }

            if (chunkGroups.isEmpty()) {
                // Nothing fit — move to a fresh page and retry with the full
                // frame height. Nothing was drawn, so the next iteration is
                // still the table's first chunk; flipping the flag here would
                // lose the header entirely when repeatHeader is off.
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
                continue
            }

            // Assemble the chunk's rows + a matching owner grid slice so the
            // per-segment separator logic in [placeTable] indexes correctly.
            val chunkRows = mutableListOf<MeasuredTableRow>()
            val chunkOwners = mutableListOf<List<Int>>()
            if (includeHeader) {
                chunkRows += header
                if (headerOwners != null) chunkOwners += headerOwners
            }
            for (group in chunkGroups) {
                chunkRows += group.rows
                chunkOwners += group.owners
            }

            // A table that splits across pages drops its corner radius:
            // re-rounding every fragment's top AND bottom would draw corners
            // in the middle of the table where the break happens.
            val splits = !isFirstChunk || remaining.size > chunkGroups.size
            val chunk = node.copy(
                rows = chunkRows,
                cellOwners = chunkOwners,
                size = Size(width = node.size.width, height = chunkHeight),
                cornerRadius = if (splits) 0f else node.cornerRadius,
            )
            place(chunk, currentCanvas, frame.left + xOffset, currentTop)
            remaining = remaining.drop(chunkGroups.size)
            currentTop += chunkHeight
            isFirstChunk = false

            if (remaining.isNotEmpty()) {
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
            }
        }

        return RenderState(currentCanvas, currentTop)
    }

    /**
     * One atomic block of body rows that must stay on the same page — a run
     * tied together by a rowspan. Carries the matching slice of the owner
     * grid so a chunk can rebuild a self-consistent [MeasuredTable].
     */
    private class BodyGroup(
        val rows: List<MeasuredTableRow>,
        val owners: List<List<Int>>,
        val height: Float,
    )

    /**
     * Splits the body rows (from [firstBodyIndex] to the end) into atomic
     * groups. A group boundary may fall only where no cell's rowspan crosses
     * it; cells that span multiple rows keep their rows in one group so the
     * slicer never tears a merged region. With no rowspans every body row is
     * its own group, matching the historical per-row slicing exactly.
     */
    private fun atomicBodyGroups(node: MeasuredTable, firstBodyIndex: Int): List<BodyGroup> {
        val groups = ArrayList<BodyGroup>()
        var i = firstBodyIndex
        val lastRow = node.rows.lastIndex
        while (i <= lastRow) {
            // Extend the group while any row already in it has a cell whose
            // rowspan reaches past the current end.
            var end = i
            var scan = i
            while (scan <= end) {
                val reach = rowSpanReach(node, scan)
                if (reach > end) end = reach
                scan++
            }
            if (end > lastRow) end = lastRow
            val rows = ArrayList<MeasuredTableRow>(end - i + 1)
            val owners = ArrayList<List<Int>>(end - i + 1)
            var height = 0f
            for (r in i..end) {
                rows += node.rows[r]
                node.cellOwners.getOrNull(r)?.let { owners += it }
                height += node.rows[r].height
            }
            groups += BodyGroup(rows = rows, owners = owners, height = height)
            i = end + 1
        }
        return groups
    }

    /** Furthest row index reached by a cell starting in [rowIndex]. */
    private fun rowSpanReach(node: MeasuredTable, rowIndex: Int): Int {
        val row = node.rows.getOrNull(rowIndex) ?: return rowIndex
        var maxReach = rowIndex
        for (cell in row.cells) {
            val reach = rowIndex + cell.rowSpan - 1
            if (reach > maxReach) maxReach = reach
        }
        return maxReach
    }

    private fun sliceText(
        node: MeasuredText,
        env: PageEnv,
        frame: ContentFrame,
        cursorY: Float,
        canvas: PdfCanvas,
        xOffset: Float = 0f,
    ): RenderState {
        var currentCanvas = canvas
        var currentTop = cursorY
        var remaining = node.lines

        while (remaining.isNotEmpty()) {
            val available = frame.bottom - currentTop
            val split = splitLinesByHeight(remaining, available)
            var fitting = split.first
            var overflow = split.second

            if (fitting.isEmpty()) {
                currentCanvas = openNewPage(env, currentCanvas)
                currentTop = frame.top
                continue
            }

            if (overflow.isNotEmpty()) {
                // Orphan control: too few lines would stay behind — move
                // the paragraph forward instead (only when there is a
                // fresh page to move to; a full-frame chunk that still
                // violates the minimum has nowhere better to go).
                if (fitting.size < node.style.minLinesBeforeBreak && currentTop != frame.top) {
                    currentCanvas = openNewPage(env, currentCanvas)
                    currentTop = frame.top
                    continue
                }
                // Widow control: too few lines would continue — pull
                // lines back from this page to keep the widow company.
                val deficit = node.style.minLinesAfterBreak - overflow.size
                if (deficit > 0) {
                    val pullBack = deficit.coerceAtMost(fitting.size - 1)
                    if (pullBack > 0) {
                        overflow = fitting.takeLast(pullBack) + overflow
                        fitting = fitting.dropLast(pullBack)
                    }
                }
            }

            val chunk = MeasuredText(
                lines = fitting,
                style = node.style,
                size = Size(width = node.size.width, height = fitting.sumOf { it.height.toDouble() }.toFloat()),
                paragraphWidth = node.paragraphWidth,
                resolvedDirection = node.resolvedDirection,
            )
            place(chunk, currentCanvas, frame.left + xOffset, currentTop)

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
        xOffset: Float = 0f,
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
                x = frame.left + xOffset,
                y = currentTop,
                width = node.size.width,
                height = chunkHeight,
                contentScale = node.contentScale,
                sourceTop = srcTop,
                sourceBottom = srcBottom,
                allowDownScale = node.allowDownScale,
                altText = node.altText,
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
                altText = node.altText,
            )
            is MeasuredBlock -> Unit // Spacers contribute size only.
            is MeasuredFormTextField -> placeFormTextField(node, canvas, originX, originY)
            is MeasuredFormCheckBox -> placeFormCheckBox(node, canvas, originX, originY)
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
            is MeasuredQrCode -> placeQrCode(node, canvas, originX, originY)
            is MeasuredBarcode -> placeBarcode(node, canvas, originX, originY)
            is MeasuredDataMatrix -> placeDataMatrix(node, canvas, originX, originY)
            is MeasuredBookmark -> canvas.bookmark(node.title, node.level, originY)
            is MeasuredAnchor -> canvas.namedDestination(node.id, originY)
            is MeasuredInternalLink -> {
                place(node.child, canvas, originX, originY)
                canvas.linkToDestination(
                    name = node.anchorId,
                    x = originX, y = originY,
                    width = node.size.width, height = node.size.height,
                )
            }
            is MeasuredKeepTogether -> place(node.child, canvas, originX, originY)
        }
    }

    /**
     * Draws a QR symbol as one vector path: horizontal runs of dark
     * modules collapse into single rectangles, which keeps the path
     * command count (and thus the PDF size) far below one-rect-per-module.
     */
    private fun placeQrCode(node: MeasuredQrCode, canvas: PdfCanvas, originX: Float, originY: Float) {
        val n = node.matrix.size
        if (n <= 0 || node.size.width <= 0f) return
        node.background?.let { canvas.drawRect(originX, originY, node.size.width, node.size.height, it) }

        val module = node.size.width / n
        val commands = mutableListOf<PathCommand>()
        for (y in 0 until n) {
            var x = 0
            while (x < n) {
                if (!node.matrix[x, y]) {
                    x++
                    continue
                }
                var runEnd = x
                while (runEnd + 1 < n && node.matrix[runEnd + 1, y]) runEnd++
                val left = originX + x * module
                val top = originY + y * module
                val right = originX + (runEnd + 1) * module
                val bottom = top + module
                commands += PathCommand.MoveTo(left, top)
                commands += PathCommand.LineTo(right, top)
                commands += PathCommand.LineTo(right, bottom)
                commands += PathCommand.LineTo(left, bottom)
                commands += PathCommand.Close
                x = runEnd + 1
            }
        }
        if (commands.isEmpty()) return
        canvas.drawPath(
            commands = commands,
            fill = PdfPaint.Solid(node.color),
            strokeColor = null,
            strokeWidth = 0f,
        )
    }

    /**
     * Draws a Data Matrix symbol as one vector path, collapsing horizontal runs
     * of dark modules into single rectangles exactly like [placeQrCode] so the
     * path command count stays well below one-rect-per-module.
     */
    private fun placeDataMatrix(node: MeasuredDataMatrix, canvas: PdfCanvas, originX: Float, originY: Float) {
        val n = node.matrix.size
        if (n <= 0 || node.size.width <= 0f) return
        node.background?.let { canvas.drawRect(originX, originY, node.size.width, node.size.height, it) }

        val module = node.size.width / n
        val commands = mutableListOf<PathCommand>()
        for (y in 0 until n) {
            var x = 0
            while (x < n) {
                if (!node.matrix[x, y]) {
                    x++
                    continue
                }
                var runEnd = x
                while (runEnd + 1 < n && node.matrix[runEnd + 1, y]) runEnd++
                val left = originX + x * module
                val top = originY + y * module
                val right = originX + (runEnd + 1) * module
                val bottom = top + module
                commands += PathCommand.MoveTo(left, top)
                commands += PathCommand.LineTo(right, top)
                commands += PathCommand.LineTo(right, bottom)
                commands += PathCommand.LineTo(left, bottom)
                commands += PathCommand.Close
                x = runEnd + 1
            }
        }
        if (commands.isEmpty()) return
        canvas.drawPath(
            commands = commands,
            fill = PdfPaint.Solid(node.color),
            strokeColor = null,
            strokeWidth = 0f,
        )
    }

    /**
     * Draws a Code 128 barcode: the encoder's alternating bar/space module
     * widths scale into the destination rectangle, bars become full-height
     * rectangles in a single vector path.
     */
    private fun placeBarcode(node: MeasuredBarcode, canvas: PdfCanvas, originX: Float, originY: Float) {
        val totalModules = node.barcode.totalModules
        if (totalModules <= 0 || node.size.width <= 0f) return
        node.background?.let { canvas.drawRect(originX, originY, node.size.width, node.size.height, it) }

        val module = node.size.width / totalModules
        val commands = mutableListOf<PathCommand>()
        var cursor = originX
        for ((index, widthModules) in node.barcode.modules.withIndex()) {
            val width = widthModules * module
            // Even indices are bars, odd are spaces — the encoder guarantees
            // the sequence starts and ends with a bar.
            if (index % 2 == 0) {
                commands += PathCommand.MoveTo(cursor, originY)
                commands += PathCommand.LineTo(cursor + width, originY)
                commands += PathCommand.LineTo(cursor + width, originY + node.size.height)
                commands += PathCommand.LineTo(cursor, originY + node.size.height)
                commands += PathCommand.Close
            }
            cursor += width
        }
        if (commands.isEmpty()) return
        canvas.drawPath(
            commands = commands,
            fill = PdfPaint.Solid(node.color),
            strokeColor = null,
            strokeWidth = 0f,
        )
    }

    /**
     * Draws an AcroForm text field. Always paints the static visual fallback
     * (a light-gray-filled box with a gray outline and the value rendered
     * inside) so the document reads correctly on every backend, then asks the
     * canvas to overlay a real interactive widget at the same rectangle. The
     * overlay is a no-op on backends without AcroForm support (Android, iOS),
     * leaving only the static box.
     */
    private fun placeFormTextField(
        node: MeasuredFormTextField,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
    ) {
        val w = node.size.width
        val h = node.size.height
        // Static look: pale fill + hairline border, matching a typical form box.
        canvas.drawRect(originX, originY, w, h, FORM_FIELD_FILL)
        canvas.strokeRect(originX, originY, w, h, FORM_FIELD_BORDER, 0.75f)
        if (node.value.isNotEmpty()) {
            val style = TextStyle(fontSize = node.fontSizePt.sp, color = PdfColor.Black)
            // drawText never interprets newlines, so a multiline value is
            // drawn line-by-line, inset a couple of points from the box
            // edges and clipped to the box height.
            val lineAdvance = node.fontSizePt * 1.3f
            var lineTop = originY + 3f
            for (line in node.value.split('\n')) {
                if (lineTop + lineAdvance > originY + h) break
                canvas.drawText(line, originX + 3f, lineTop, style)
                lineTop += lineAdvance
            }
        }
        canvas.formTextField(
            name = node.name,
            x = originX,
            y = originY,
            width = w,
            height = h,
            value = node.value,
            multiline = node.multiline,
            fontSizePt = node.fontSizePt,
        )
    }

    /**
     * Draws an AcroForm checkbox: a bordered square (with an `X` through it
     * when checked) as the static fallback, then the interactive widget
     * overlay (no-op on Android / iOS).
     */
    private fun placeFormCheckBox(
        node: MeasuredFormCheckBox,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
    ) {
        val s = node.size.width
        canvas.drawRect(originX, originY, s, s, FORM_FIELD_FILL)
        canvas.strokeRect(originX, originY, s, s, FORM_FIELD_BORDER, 0.75f)
        if (node.checked) {
            val inset = s * 0.2f
            canvas.drawLine(
                originX + inset, originY + inset,
                originX + s - inset, originY + s - inset,
                PdfColor.Black, s * 0.12f,
            )
            canvas.drawLine(
                originX + s - inset, originY + inset,
                originX + inset, originY + s - inset,
                PdfColor.Black, s * 0.12f,
            )
        }
        canvas.formCheckBox(name = node.name, x = originX, y = originY, size = s, checked = node.checked)
    }

    /** Pale fill shared by the static form-field fallbacks. */
    private val FORM_FIELD_FILL = PdfColor(0.95f, 0.95f, 0.95f)

    /** Hairline outline shared by the static form-field fallbacks. */
    private val FORM_FIELD_BORDER = PdfColor.Gray

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
            Shape.Circle -> {
                val diameter = minOf(node.size.width, node.size.height)
                w = diameter
                h = diameter
                x = originX + (node.size.width - diameter) / 2f
                y = originY + (node.size.height - diameter) / 2f
            }
            Shape.Ellipse -> {
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
        children: List<PlacedChild>,
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

        // Rotation / opacity wrap the entire container — fill, shadow,
        // children, and border all transform together.
        val groupOpacity = decoration.opacity.coerceIn(0f, 1f)
        val needsTransform = decoration.rotation != 0f || groupOpacity < 1f
        if (needsTransform) {
            canvas.saveState()
            if (decoration.rotation != 0f) {
                canvas.rotate(
                    degrees = decoration.rotation,
                    pivotX = originX + width / 2f,
                    pivotY = originY + height / 2f,
                )
            }
            if (groupOpacity < 1f) canvas.beginTransparencyGroup(groupOpacity)
        }

        decoration.dropShadow?.let { shadow ->
            drawDropShadow(canvas, shadow, originX, originY, width, height, uniformRadius)
        }

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
                        // Dashed / dotted outlines are drawn edge-by-edge —
                        // drawLine is the only primitive that takes a
                        // LineStyle. Rounded rectangles fall back to a
                        // solid stroke above (the dash phase can't follow
                        // the arc cleanly).
                        border.style != LineStyle.Solid -> {
                            drawStyledRectOutline(canvas, originX, originY, width, height, border)
                        }
                        else -> canvas.strokeRect(originX, originY, width, height, border.color, strokeWidth)
                    }
                }
            }
        }

        if (needsTransform) {
            if (groupOpacity < 1f) canvas.endTransparencyGroup()
            canvas.restoreState()
        }
    }

    /**
     * Approximates a blurred shadow with concentric translucent rounded
     * rectangles, outermost first so the innermost layers stack their
     * alpha towards [DropShadow.color]'s at the centre.
     */
    private fun drawDropShadow(
        canvas: PdfCanvas,
        shadow: DropShadow,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        val layers = 5
        val layerColor = shadow.color.copy(alpha = shadow.color.alpha / layers)
        for (i in layers downTo 1) {
            val spread = shadow.blur.value * i / layers
            canvas.drawRoundedRect(
                x = x + shadow.offsetX.value - spread / 2f,
                y = y + shadow.offsetY.value - spread / 2f,
                width = width + spread,
                height = height + spread,
                cornerRadius = cornerRadius + spread / 2f,
                color = layerColor,
            )
        }
    }

    /** Strokes a sharp rectangle outline as four styled (dashed / dotted) lines. */
    private fun drawStyledRectOutline(
        canvas: PdfCanvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        border: BorderStroke,
    ) {
        val w = border.width.value
        canvas.drawLine(x, y, x + width, y, border.color, w, border.style)
        canvas.drawLine(x + width, y, x + width, y + height, border.color, w, border.style)
        canvas.drawLine(x + width, y + height, x, y + height, border.color, w, border.style)
        canvas.drawLine(x, y + height, x, y, border.color, w, border.style)
    }

    private fun CornerRadius.hasAnyRadius(): Boolean =
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
        sides: BorderSides,
    ) {
        sides.top?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY,
                x2 = originX + width, y2 = originY,
                color = it.color, thickness = w, style = it.style,
            )
        }
        sides.right?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX + width, y1 = originY,
                x2 = originX + width, y2 = originY + height,
                color = it.color, thickness = w, style = it.style,
            )
        }
        sides.bottom?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY + height,
                x2 = originX + width, y2 = originY + height,
                color = it.color, thickness = w, style = it.style,
            )
        }
        sides.left?.let {
            val w = it.width.value
            if (w > 0f) canvas.drawLine(
                x1 = originX, y1 = originY,
                x2 = originX, y2 = originY + height,
                color = it.color, thickness = w, style = it.style,
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

        // Pre-compute each row's top edge (relative to the table top) so cells
        // and separators can address any row by index — spanned cells need
        // the y of rows other than their own.
        val rowTops = FloatArray(node.rows.size)
        run {
            var y = 0f
            for ((i, row) in node.rows.withIndex()) {
                rowTops[i] = y
                y += row.height
            }
        }

        canvas.saveState()
        try {
            if (node.cornerRadius > 0f) {
                canvas.clipRoundedRect(originX, originY, tableWidth, tableHeight, node.cornerRadius)
            } else {
                canvas.clipRect(originX, originY, tableWidth, tableHeight)
            }

            // Row backgrounds first, then cells on top. Row fills skip any
            // column carried over by a rowspan from an earlier row so the
            // spanning cell (drawn in its starting row) is never overpainted.
            for ((rowIndex, row) in node.rows.withIndex()) {
                val fill = row.background ?: continue
                val rowTop = originY + rowTops[rowIndex]
                for ((startX, width) in rowBackgroundSegments(node, rowIndex)) {
                    canvas.drawRect(originX + startX, rowTop, width, row.height, fill)
                }
            }

            for ((rowIndex, row) in node.rows.withIndex()) {
                drawTableRow(row, canvas, originX, originY + rowTops[rowIndex])
            }

            if (node.border.showHorizontalLines && node.borderWidth > 0f) {
                drawHorizontalSeparators(node, canvas, originX, originY, rowTops)
            }

            if (node.border.showVerticalLines && node.borderWidth > 0f) {
                drawVerticalSeparators(node, canvas, originX, originY, rowTops)
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

    /**
     * Horizontal run-segments `(startX, width)` of [rowIndex] that this row
     * should paint its background across — i.e. every column whose owner is
     * NOT inherited from the row above (those belong to a spanning cell that
     * already painted itself). Adjacent paint-this-row columns coalesce so a
     * plain row stays one rectangle.
     */
    private fun rowBackgroundSegments(node: MeasuredTable, rowIndex: Int): List<Pair<Float, Float>> {
        val owners = node.cellOwners.getOrNull(rowIndex) ?: return listOf(0f to node.size.width)
        val above = if (rowIndex > 0) node.cellOwners.getOrNull(rowIndex - 1) else null
        val segments = ArrayList<Pair<Float, Float>>()
        var x = 0f
        var segStart = -1f
        var segWidth = 0f
        for (c in node.columnWidths.indices) {
            val w = node.columnWidths[c]
            val carriedOver = above != null && above.getOrNull(c) == owners.getOrNull(c)
            if (!carriedOver) {
                if (segStart < 0f) segStart = x
                segWidth += w
            } else if (segStart >= 0f) {
                segments += segStart to segWidth
                segStart = -1f
                segWidth = 0f
            }
            x += w
        }
        if (segStart >= 0f) segments += segStart to segWidth
        return segments
    }

    /**
     * Draws the horizontal inner separators. A segment under column `c`
     * between rows `r` and `r+1` is skipped when the same cell owns both
     * slots (a rowspan crossing the boundary) so the line never cuts through
     * a merged region.
     */
    private fun drawHorizontalSeparators(
        node: MeasuredTable,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
        rowTops: FloatArray,
    ) {
        val lastColumn = node.columnWidths.lastIndex
        for (rowIndex in 0 until node.rows.lastIndex) {
            val sepY = originY + rowTops[rowIndex] + node.rows[rowIndex].height
            val ownersHere = node.cellOwners.getOrNull(rowIndex)
            val ownersBelow = node.cellOwners.getOrNull(rowIndex + 1)
            // Coalesce consecutive non-merged columns into one stroke so a
            // boundary with no rowspan crossing it draws a single line — the
            // pre-span behaviour — while a rowspan still breaks the run.
            var x = originX
            var runStart = -1f
            for (c in node.columnWidths.indices) {
                val w = node.columnWidths[c]
                val merged = ownersHere != null && ownersBelow != null &&
                    ownersHere.getOrNull(c) == ownersBelow.getOrNull(c)
                if (!merged) {
                    if (runStart < 0f) runStart = x
                    if (c == lastColumn) {
                        // Extend the final segment to the full table width so a
                        // full-width separator draws edge-to-edge, as before.
                        canvas.drawLine(runStart, sepY, originX + node.size.width, sepY, node.borderColor, node.borderWidth)
                        runStart = -1f
                    }
                } else if (runStart >= 0f) {
                    canvas.drawLine(runStart, sepY, x, sepY, node.borderColor, node.borderWidth)
                    runStart = -1f
                }
                x += w
            }
        }
    }

    /**
     * Draws the vertical inner separators. A segment in row `r` between
     * columns `c` and `c+1` is skipped when the same cell owns both slots
     * (a colspan crossing the boundary). Consecutive non-merged rows coalesce
     * into one stroke so a boundary with no colspan draws a single full-height
     * line — the pre-span behaviour — while a colspan still breaks the run.
     */
    private fun drawVerticalSeparators(
        node: MeasuredTable,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
        rowTops: FloatArray,
    ) {
        val lastRow = node.rows.lastIndex
        var lineX = originX
        for (c in 0 until node.columnWidths.lastIndex) {
            lineX += node.columnWidths[c]
            var runTop = -1f
            for (rowIndex in node.rows.indices) {
                val owners = node.cellOwners.getOrNull(rowIndex)
                val merged = owners != null && owners.getOrNull(c) == owners.getOrNull(c + 1)
                val top = originY + rowTops[rowIndex]
                if (!merged) {
                    if (runTop < 0f) runTop = top
                    if (rowIndex == lastRow) {
                        // Extend to the full table height so a full-height
                        // separator draws top-to-bottom, as before.
                        canvas.drawLine(lineX, runTop, lineX, originY + node.size.height, node.borderColor, node.borderWidth)
                        runTop = -1f
                    }
                } else if (runTop >= 0f) {
                    canvas.drawLine(lineX, runTop, lineX, top, node.borderColor, node.borderWidth)
                    runTop = -1f
                }
            }
        }
    }

    private fun drawTableRow(
        row: MeasuredTableRow,
        canvas: PdfCanvas,
        originX: Float,
        originY: Float,
    ) {
        for (cell in row.cells) {
            // [spannedHeight] equals the row height for non-spanning cells, so
            // single-row cells behave exactly as before; a rowspan cell draws
            // its fill and content over the full block it covers.
            val cellHeight = cell.spannedHeight
            val cellX = originX + cell.offsetX
            cell.style.background?.let { cellFill ->
                canvas.drawRect(cellX, originY, cell.width, cellHeight, cellFill)
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
            if (line.justifiedWords.isNotEmpty()) {
                // The layout engine pre-computed every word's x-offset so
                // the line fills the paragraph slot exactly.
                for (word in line.justifiedWords) {
                    canvas.drawText(text = word.text, x = lineX + word.x, y = lineTop, style = node.style)
                }
            } else {
                canvas.drawText(text = line.text, x = lineX, y = lineTop, style = node.style)
            }
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
     * it obeys [TextAlign] under the paragraph's resolved direction —
     * RTL paragraphs anchor `Start` to the right edge and `End` to the
     * left, mirroring what readers of those scripts expect.
     *
     * Justified lines already carry per-word positions spanning the full
     * paragraph slot (their [TextLine.width] equals the slot), so their
     * slack collapses to zero here.
     */
    private fun alignmentOffsetForLine(node: MeasuredText, line: TextLine): Float {
        val slack = (node.paragraphWidth - line.width).coerceAtLeast(0f)
        val rtl = node.resolvedDirection == TextDirection.Rtl
        return when (node.style.align) {
            TextAlign.Start -> if (rtl) slack else 0f
            TextAlign.Center -> slack / 2f
            TextAlign.End -> if (rtl) 0f else slack
            // Non-stretched (paragraph-final) lines anchor to the start edge.
            TextAlign.Justify -> if (rtl) slack else 0f
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
        val rtl = node.resolvedDirection == TextDirection.Rtl
        for (line in node.lines) {
            val slack = (node.paragraphWidth - line.totalWidth).coerceAtLeast(0f)
            val lineLeft = originX + when (node.align) {
                TextAlign.Start -> if (rtl) slack else 0f
                TextAlign.Center -> slack / 2f
                TextAlign.End -> if (rtl) 0f else slack
                // Justified lines already span the slot (stretched space
                // segments), so there is no slack left to distribute.
                TextAlign.Justify -> if (rtl) slack else 0f
            }
            for (segment in line.segments) {
                val segmentX = lineLeft + segment.xOffset
                // [RichSegment.yOffset] carries the superscript / subscript
                // baseline shift computed during layout.
                val segmentY = lineTop + segment.yOffset
                canvas.drawText(
                    text = segment.text,
                    x = segmentX,
                    y = segmentY,
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
                    lineTop = segmentY,
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
