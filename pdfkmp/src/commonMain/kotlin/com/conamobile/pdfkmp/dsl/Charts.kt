package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * One datum in a chart: a human-readable [label], a numeric [value], and the
 * [color] used to draw it.
 *
 * The same type backs bars, pie / donut slices, and legend rows so callers can
 * reuse a single data model across every chart flavour.
 *
 * @property label caption shown under a bar or in a legend row.
 * @property value magnitude; bars scale to the largest value in the set and
 *   pie / donut slices to the share of the total. Negative values are treated
 *   as `0` because a negative bar or slice has no meaningful geometry.
 * @property color fill colour for this datum's bar / slice / legend swatch.
 */
public data class ChartSeries(
    val label: String,
    val value: Float,
    val color: PdfColor,
)

/**
 * One coloured segment within a stacked bar.
 *
 * @property label caption shown in the chart legend.
 * @property value magnitude of this segment; negative values are treated as
 *   `0` because a negative stack segment has no meaningful geometry.
 * @property color fill colour for this segment (and its legend swatch).
 */
public data class StackedBarSegment(
    val label: String,
    val value: Float,
    val color: PdfColor,
)

/**
 * One group (single bar) in a [stackedBarChart], built from stacked
 * [segments] drawn bottom-to-top in list order.
 *
 * @property label caption shown under the bar.
 * @property segments the stacked pieces, bottom first.
 */
public data class StackedBarGroup(
    val label: String,
    val segments: List<StackedBarSegment>,
)

/**
 * One line in a multi-series [lineChart].
 *
 * @property label caption shown in the chart legend.
 * @property points y values in plot order; every series in a chart is scaled
 *   against the combined min/max so the lines share one vertical axis.
 * @property color stroke colour for this line (and its legend swatch).
 */
public data class LineSeries(
    val label: String,
    val points: List<Float>,
    val color: PdfColor,
)

// Chart geometry is authored entirely through `freeDraw`, `row`, `column`, and
// `text` — the existing public DSL primitives — so a chart is just composed
// nodes. That keeps every glyph and shape vector (no rasterisation) and means
// these helpers never need to touch the layout engine or any platform canvas.

/** Default gap between a chart's plot area and its caption / legend block. */
private val CHART_GAP: Dp = 6.dp

/** Reserved vertical band for the per-bar label row under a bar chart. */
private val BAR_LABEL_BAND: Dp = 16.dp

/** Reserved vertical band for the value captions drawn above the bars. */
private val BAR_VALUE_BAND: Float = 12f

/**
 * Appends a vertical bar chart.
 *
 * Each entry in [series] becomes one filled bar whose height is proportional to
 * its value relative to the largest value in the set. A baseline axis line is
 * stroked along the bottom of the plot. Bar captions are laid out in a [row] of
 * equal-width [weighted] slots so every label sits centred under its bar, and —
 * when [showValues] is on — the numeric value is drawn above each bar.
 *
 * The whole drawing is emitted through [freeDraw] / [text], so the output stays
 * crisp vector at any zoom.
 *
 * An empty [series], or one whose values are all `<= 0`, draws nothing.
 *
 * When [showAxis] is on, a baseline is stroked along the bottom. When
 * [gridLines] is greater than `0`, that many faint horizontal value-grid lines
 * are drawn across the plot and the min (`0`) and max value are labelled at the
 * left edge so the bar heights can be read against a scale.
 *
 * @param series the bars, left to right.
 * @param width total width of the plot area.
 * @param height total height of the plot area (bars + value captions live
 *   inside this; the label row is added beneath it).
 * @param showValues when `true` (default), draws each bar's value above it.
 * @param showAxis when `true` (default), strokes the baseline axis line.
 * @param gridLines number of faint horizontal value-grid lines (and the
 *   min/max axis labels). `0` (default) draws no grid.
 * @param axisColor colour of the baseline axis line.
 * @param gridColor colour of the faint value-grid lines.
 */
public fun ContainerScope.barChart(
    series: List<ChartSeries>,
    width: Dp,
    height: Dp,
    showValues: Boolean = true,
    showAxis: Boolean = true,
    gridLines: Int = 0,
    axisColor: PdfColor = PdfColor.Gray,
    gridColor: PdfColor = PdfColor.LightGray,
) {
    if (series.isEmpty()) return
    val maxValue = series.maxOf { max(it.value, 0f) }
    if (maxValue <= 0f) return

    val w = width.value
    val h = height.value
    // Leave a strip at the top so value captions never collide with the
    // tallest bar.
    val topInset = if (showValues) BAR_VALUE_BAND else 0f
    val plotHeight = max(h - topInset, 0f)
    val baseline = h
    // Bars share the width evenly with a quarter-slot gutter between them, a
    // proportion that reads well for any bar count.
    val slot = w / series.size
    val barWidth = slot * 0.75f
    val gutter = (slot - barWidth) / 2f

    column(spacing = CHART_GAP) {
        // Max-value axis label above the plot when a value grid is shown, so the
        // grid lines have a readable scale (the baseline below is the 0 mark).
        if (gridLines > 0) {
            text(formatValue(maxValue)) {
                fontSize = 7.sp
                // Qualify with `this`: the single-series lineChart's `color`
                // parameter would otherwise shadow TextScope.color for an
                // unqualified assignment.
                this.color = PdfColor.Gray
            }
        }
        freeDraw(width = width, height = height) {
            // Value grid first so everything else paints over it.
            if (gridLines > 0) {
                for (g in 1..gridLines) {
                    val y = baseline - plotHeight * (g.toFloat() / gridLines)
                    path(strokeColor = gridColor, strokeWidth = 0.5f) {
                        moveTo(0f, y)
                        lineTo(w, y)
                    }
                }
            }
            // Baseline axis next so bars paint over its endpoints cleanly.
            if (showAxis) {
                path(strokeColor = axisColor, strokeWidth = 1f) {
                    moveTo(0f, baseline)
                    lineTo(w, baseline)
                }
            }
            series.forEachIndexed { index, bar ->
                val value = max(bar.value, 0f)
                if (value <= 0f) return@forEachIndexed
                val barHeight = plotHeight * (value / maxValue)
                val left = index * slot + gutter
                val top = baseline - barHeight
                path(fill = bar.color) {
                    rect(left, top, barWidth, barHeight)
                }
            }
        }

        // Value captions above bars and the label row below both reuse the
        // same equal-width weighted slots so they align with each bar.
        if (showValues) {
            row {
                series.forEach { bar ->
                    weighted(1f) {
                        text(formatValue(max(bar.value, 0f))) {
                            fontSize = 8.sp
                            align = TextAlign.Center
                        }
                    }
                }
            }
        }
        row {
            series.forEach { bar ->
                weighted(1f) {
                    text(bar.label) {
                        fontSize = 8.sp
                        align = TextAlign.Center
                    }
                }
            }
        }
    }
    // Reserve the label band as a trailing spacer-free hint; the row above
    // already contributes the band height, so nothing else is needed.
}

/**
 * Appends a stacked bar chart.
 *
 * Each entry in [groups] becomes one bar whose segments are stacked
 * bottom-to-top in list order; every bar is scaled against the largest group
 * total so the tallest stack fills the plot. A baseline axis line is stroked
 * along the bottom (when [showAxis] is on) and an optional faint value grid is
 * drawn behind the bars (when [gridLines] is greater than `0`).
 *
 * When [showLegend] is on, a swatch + label legend is appended below, one row
 * per distinct segment label encountered (first occurrence wins its colour),
 * so the segment colours can be decoded.
 *
 * An empty [groups] list, or one whose every segment is `<= 0`, draws nothing.
 *
 * @param groups the bars, left to right; each stacks its [StackedBarGroup.segments].
 * @param width total width of the plot area.
 * @param height total height of the plot area; the label row is added beneath.
 * @param showAxis when `true` (default), strokes the baseline axis line.
 * @param gridLines number of faint horizontal value-grid lines; `0` draws none.
 * @param showLegend when `true` (default), appends a per-segment swatch legend.
 * @param axisColor colour of the baseline axis line.
 * @param gridColor colour of the faint value-grid lines.
 */
public fun ContainerScope.stackedBarChart(
    groups: List<StackedBarGroup>,
    width: Dp,
    height: Dp,
    showAxis: Boolean = true,
    gridLines: Int = 0,
    showLegend: Boolean = true,
    axisColor: PdfColor = PdfColor.Gray,
    gridColor: PdfColor = PdfColor.LightGray,
) {
    if (groups.isEmpty()) return
    val totals = groups.map { group -> group.segments.sumOf { max(it.value, 0f).toDouble() }.toFloat() }
    val maxTotal = totals.maxOrNull() ?: 0f
    if (maxTotal <= 0f) return

    val w = width.value
    val h = height.value
    val baseline = h
    val slot = w / groups.size
    val barWidth = slot * 0.75f
    val gutter = (slot - barWidth) / 2f

    column(spacing = CHART_GAP) {
        if (gridLines > 0) {
            text(formatValue(maxTotal)) {
                fontSize = 7.sp
                color = PdfColor.Gray
            }
        }
        freeDraw(width = width, height = height) {
            if (gridLines > 0) {
                for (g in 1..gridLines) {
                    val y = baseline - h * (g.toFloat() / gridLines)
                    path(strokeColor = gridColor, strokeWidth = 0.5f) {
                        moveTo(0f, y)
                        lineTo(w, y)
                    }
                }
            }
            if (showAxis) {
                path(strokeColor = axisColor, strokeWidth = 1f) {
                    moveTo(0f, baseline)
                    lineTo(w, baseline)
                }
            }
            groups.forEachIndexed { index, group ->
                val left = index * slot + gutter
                // Stack segments upward from the baseline; each segment height is
                // its share of the largest total so all bars share one scale.
                var cursorBottom = baseline
                group.segments.forEach { segment ->
                    val value = max(segment.value, 0f)
                    if (value <= 0f) return@forEach
                    val segHeight = h * (value / maxTotal)
                    val top = cursorBottom - segHeight
                    path(fill = segment.color) {
                        rect(left, top, barWidth, segHeight)
                    }
                    cursorBottom = top
                }
            }
        }

        row {
            groups.forEach { group ->
                weighted(1f) {
                    text(group.label) {
                        fontSize = 8.sp
                        align = TextAlign.Center
                    }
                }
            }
        }
    }

    if (showLegend) segmentLegend(groups.flatMap { it.segments })
}

/**
 * Appends a line chart.
 *
 * The [points] are plotted at evenly spaced x positions and scaled vertically
 * so the smallest value sits on the baseline and the largest at the top. They
 * are joined into a single stroked polyline. When [fillUnderLine] is on, the
 * same polyline is closed down to the baseline and filled with a translucent
 * tint of [color] (alpha `0.15`) to shade the area under the curve.
 *
 * Fewer than two points draws nothing — a polyline needs at least a start and
 * an end. When every value is identical the line is drawn flat across the
 * vertical centre.
 *
 * When [gridLines] is greater than `0`, that many faint horizontal grid lines
 * are drawn behind the curve and the min / max values are labelled at the top
 * and bottom so the curve can be read against a scale.
 *
 * @param points y values in plot order.
 * @param width plot width.
 * @param height plot height.
 * @param color stroke (and fill tint) colour.
 * @param strokeWidth polyline thickness in points.
 * @param fillUnderLine when `true`, shades the area under the line.
 * @param gridLines number of faint horizontal grid lines (and the min/max
 *   axis labels). `0` (default) draws no grid.
 * @param axisColor colour of the baseline axis line.
 * @param gridColor colour of the faint grid lines.
 */
public fun ContainerScope.lineChart(
    points: List<Float>,
    width: Dp,
    height: Dp,
    color: PdfColor = PdfColor.Blue,
    strokeWidth: Float = 2f,
    fillUnderLine: Boolean = false,
    gridLines: Int = 0,
    axisColor: PdfColor = PdfColor.Gray,
    gridColor: PdfColor = PdfColor.LightGray,
) {
    if (points.size < 2) return

    val w = width.value
    val h = height.value
    val minValue = points.min()
    val maxValue = points.max()
    val span = maxValue - minValue
    val stepX = w / (points.size - 1)

    // Map a value to a y coordinate (top-left origin: larger value → smaller
    // y). A zero span (all equal) parks the line on the vertical centre.
    fun yOf(value: Float): Float =
        if (span <= 0f) h / 2f else h - (value - minValue) / span * h

    val coords = points.mapIndexed { index, value -> index * stepX to yOf(value) }

    column(spacing = 0.dp) {
        if (gridLines > 0) {
            text(formatValue(maxValue)) {
                fontSize = 7.sp
                // Qualify with `this`: the single-series lineChart's `color`
                // parameter would otherwise shadow TextScope.color for an
                // unqualified assignment.
                this.color = PdfColor.Gray
            }
        }
        freeDraw(width = width, height = height) {
            if (gridLines > 0) {
                for (g in 1 until gridLines) {
                    val y = h * (g.toFloat() / gridLines)
                    path(strokeColor = gridColor, strokeWidth = 0.5f) {
                        moveTo(0f, y)
                        lineTo(w, y)
                    }
                }
            }
            // Baseline axis, drawn before the line and fill so they sit on top.
            path(strokeColor = axisColor, strokeWidth = 1f) {
                moveTo(0f, h)
                lineTo(w, h)
            }

            if (fillUnderLine) {
                // Same polyline, closed down to the baseline and back, filled with
                // a translucent tint so the curve stays readable over the shading.
                path(fill = color.withAlpha(0.15f)) {
                    moveTo(coords.first().first, h)
                    coords.forEach { (x, y) -> lineTo(x, y) }
                    lineTo(coords.last().first, h)
                    close()
                }
            }

            path(strokeColor = color, strokeWidth = strokeWidth) {
                moveTo(coords.first().first, coords.first().second)
                coords.drop(1).forEach { (x, y) -> lineTo(x, y) }
            }
        }
        if (gridLines > 0) {
            text(formatValue(minValue)) {
                fontSize = 7.sp
                this.color = PdfColor.Gray
            }
        }
    }
}

/**
 * Appends a multi-series line chart.
 *
 * Every series in [series] is plotted in the same plot area, scaled against the
 * combined min / max across all series so the lines share one vertical axis,
 * each stroked in its own [LineSeries.color]. As with the single-series
 * overload, [gridLines] draws a faint grid plus min/max axis labels, and
 * [showLegend] appends a swatch + label legend so the lines can be told apart.
 *
 * Series with fewer than two points are skipped. If no series has at least two
 * points, nothing is drawn.
 *
 * @param series the lines to overlay.
 * @param width plot width.
 * @param height plot height.
 * @param strokeWidth polyline thickness in points, shared by every series.
 * @param gridLines number of faint horizontal grid lines; `0` draws none.
 * @param showLegend when `true` (default), appends a per-series swatch legend.
 * @param axisColor colour of the baseline axis line.
 * @param gridColor colour of the faint grid lines.
 */
public fun ContainerScope.lineChart(
    series: List<LineSeries>,
    width: Dp,
    height: Dp,
    strokeWidth: Float = 2f,
    gridLines: Int = 0,
    showLegend: Boolean = true,
    axisColor: PdfColor = PdfColor.Gray,
    gridColor: PdfColor = PdfColor.LightGray,
) {
    val drawable = series.filter { it.points.size >= 2 }
    if (drawable.isEmpty()) return

    val w = width.value
    val h = height.value
    val allValues = drawable.flatMap { it.points }
    val minValue = allValues.min()
    val maxValue = allValues.max()
    val span = maxValue - minValue

    fun yOf(value: Float): Float =
        if (span <= 0f) h / 2f else h - (value - minValue) / span * h

    column(spacing = 0.dp) {
        if (gridLines > 0) {
            text(formatValue(maxValue)) {
                fontSize = 7.sp
                // Qualify with `this`: the single-series lineChart's `color`
                // parameter would otherwise shadow TextScope.color for an
                // unqualified assignment.
                this.color = PdfColor.Gray
            }
        }
        freeDraw(width = width, height = height) {
            if (gridLines > 0) {
                for (g in 1 until gridLines) {
                    val y = h * (g.toFloat() / gridLines)
                    path(strokeColor = gridColor, strokeWidth = 0.5f) {
                        moveTo(0f, y)
                        lineTo(w, y)
                    }
                }
            }
            path(strokeColor = axisColor, strokeWidth = 1f) {
                moveTo(0f, h)
                lineTo(w, h)
            }
            drawable.forEach { line ->
                val stepX = w / (line.points.size - 1)
                val coords = line.points.mapIndexed { index, value -> index * stepX to yOf(value) }
                path(strokeColor = line.color, strokeWidth = strokeWidth) {
                    moveTo(coords.first().first, coords.first().second)
                    coords.drop(1).forEach { (x, y) -> lineTo(x, y) }
                }
            }
        }
        if (gridLines > 0) {
            text(formatValue(minValue)) {
                fontSize = 7.sp
                this.color = PdfColor.Gray
            }
        }
    }

    if (showLegend) {
        spacer(height = CHART_GAP)
        column(spacing = 4.dp) {
            drawable.forEach { line ->
                row(spacing = 6.dp) {
                    freeDraw(width = 10.dp, height = 10.dp) {
                        path(fill = line.color) { rect(0f, 0f, 10f, 10f) }
                    }
                    text(line.label) { fontSize = 9.sp }
                }
            }
        }
    }
}

/**
 * Appends a pie chart.
 *
 * Each entry in [slices] becomes a wedge whose angle is its share of the total.
 * A wedge is a path: move to the centre, line to the arc's start point, sweep
 * the outer arc (approximated with cubic Béziers — arcs wider than 90° are
 * split into multiple cubics using the standard `k = 4/3·tan(θ/4)` control
 * handle), then [close] back to the centre. The first slice starts at the top
 * (12 o'clock) and slices sweep clockwise.
 *
 * When [showLegend] is `true` (default), a [column] of legend rows is added
 * beside the pie, each with a small colour swatch, the slice's label, and its
 * percentage of the total.
 *
 * An empty [slices] list, or one whose values sum to `<= 0`, draws nothing.
 *
 * @param slices the wedges, in draw order from the top, clockwise.
 * @param diameter outer diameter of the pie.
 * @param showLegend when `true`, appends a swatch + label + percentage legend.
 */
public fun ContainerScope.pieChart(
    slices: List<ChartSeries>,
    diameter: Dp,
    showLegend: Boolean = true,
) {
    val total = slices.sumOf { max(it.value, 0f).toDouble() }.toFloat()
    if (slices.isEmpty() || total <= 0f) return

    val d = diameter.value
    val radius = d / 2f
    val cx = radius
    val cy = radius

    freeDraw(width = diameter, height = diameter) {
        var startAngle = TOP_ANGLE
        slices.forEach { slice ->
            val value = max(slice.value, 0f)
            if (value <= 0f) return@forEach
            val sweep = value / total * TAU
            path(fill = slice.color) {
                moveTo(cx, cy)
                appendWedge(cx, cy, radius, startAngle, sweep)
                close()
            }
            startAngle += sweep
        }
    }

    if (showLegend) legend(slices, total)
}

/**
 * Appends a donut chart — a pie with a circular hole punched in the centre.
 *
 * Each entry in [slices] becomes an annular segment: the outer arc is swept
 * forward, a line drops to the inner radius, the inner arc is swept backward,
 * and the path is closed. Arc approximation matches [pieChart] (cubic Béziers,
 * `k = 4/3·tan(θ/4)`, ≤90° per cubic). The hole's size is [holeRatio] of the
 * outer radius.
 *
 * When [showLegend] is `true` (default), the same swatch + label + percentage
 * legend as [pieChart] is appended.
 *
 * An empty [slices] list, or one whose values sum to `<= 0`, draws nothing.
 *
 * @param slices the segments, in draw order from the top, clockwise.
 * @param diameter outer diameter of the donut.
 * @param holeRatio inner-hole radius as a fraction of the outer radius,
 *   clamped to `0f..0.95f` so the ring never vanishes or inverts.
 * @param showLegend when `true`, appends a swatch + label + percentage legend.
 */
public fun ContainerScope.donutChart(
    slices: List<ChartSeries>,
    diameter: Dp,
    holeRatio: Float = 0.55f,
    showLegend: Boolean = true,
) {
    val total = slices.sumOf { max(it.value, 0f).toDouble() }.toFloat()
    if (slices.isEmpty() || total <= 0f) return

    val d = diameter.value
    val outerRadius = d / 2f
    val innerRadius = outerRadius * holeRatio.coerceIn(0f, 0.95f)
    val cx = outerRadius
    val cy = outerRadius

    freeDraw(width = diameter, height = diameter) {
        var startAngle = TOP_ANGLE
        slices.forEach { slice ->
            val value = max(slice.value, 0f)
            if (value <= 0f) return@forEach
            val sweep = value / total * TAU
            val endAngle = startAngle + sweep
            path(fill = slice.color) {
                // Outer arc forward, drop to the inner radius, inner arc back,
                // then close — the classic annular-segment construction.
                val outerStart = pointOnCircle(cx, cy, outerRadius, startAngle)
                moveTo(outerStart.first, outerStart.second)
                appendWedge(cx, cy, outerRadius, startAngle, sweep)
                val innerEnd = pointOnCircle(cx, cy, innerRadius, endAngle)
                lineTo(innerEnd.first, innerEnd.second)
                appendWedge(cx, cy, innerRadius, endAngle, -sweep)
                close()
            }
            startAngle = endAngle
        }
    }

    if (showLegend) legend(slices, total)
}

// region — shared helpers

/** A quarter turn; we never let a single cubic span more than this. */
private const val QUARTER: Float = (PI / 2.0).toFloat()

/** A full turn in radians. */
private val TAU: Float = (2.0 * PI).toFloat()

/**
 * The 12-o'clock angle in our drawing convention. The plane has a top-left
 * origin with y growing downward, so the upward direction is `-90°`; slices
 * therefore start at the top and a positive sweep advances clockwise.
 */
private val TOP_ANGLE: Float = (-PI / 2.0).toFloat()

/** Point at [angle] on the circle of [radius] centred at `(cx, cy)`. */
private fun pointOnCircle(cx: Float, cy: Float, radius: Float, angle: Float): Pair<Float, Float> =
    cx + radius * cos(angle) to cy + radius * sin(angle)

/**
 * Sweeps an arc of [sweep] radians (signed) starting at [startAngle] along the
 * circle of [radius] centred at `(cx, cy)`, appending cubic Béziers to the
 * current path. The path's pen is assumed to already sit at the arc's start
 * point, so this emits only `cubicTo` commands.
 *
 * The arc is split into segments of at most 90°, each approximated by one cubic
 * with control-handle length `k = 4/3·tan(θ/4)` — the textbook circular-arc
 * Bézier formula, the same approach [com.conamobile.pdfkmp.vector.ArcConverter]
 * uses internally.
 */
private fun PathScope.appendWedge(
    cx: Float,
    cy: Float,
    radius: Float,
    startAngle: Float,
    sweep: Float,
) {
    if (sweep == 0f || radius <= 0f) return
    val segments = kotlin.math.ceil(kotlin.math.abs(sweep) / QUARTER).toInt().coerceAtLeast(1)
    val segmentSweep = sweep / segments
    val k = (4.0 / 3.0 * tan(segmentSweep / 4.0)).toFloat()

    var theta = startAngle
    repeat(segments) {
        val next = theta + segmentSweep
        val (sx, sy) = pointOnCircle(cx, cy, radius, theta)
        val (ex, ey) = pointOnCircle(cx, cy, radius, next)
        // Tangent handles: rotate the radius-vector 90° and scale by k·r.
        val c1x = sx - k * radius * sin(theta)
        val c1y = sy + k * radius * cos(theta)
        val c2x = ex + k * radius * sin(next)
        val c2y = ey - k * radius * cos(next)
        cubicTo(c1x, c1y, c2x, c2y, ex, ey)
        theta = next
    }
}

/**
 * Appends the swatch + label + percentage legend shared by [pieChart] and
 * [donutChart]: one row per slice, each a small filled square drawn with
 * [freeDraw], the slice label, and its share of [total] as a percentage.
 * Zero / negative slices are skipped — they contribute no visible wedge.
 */
private fun ContainerScope.legend(slices: List<ChartSeries>, total: Float) {
    spacer(height = CHART_GAP)
    column(spacing = 4.dp) {
        slices.forEach { slice ->
            val value = max(slice.value, 0f)
            if (value <= 0f) return@forEach
            val percent = value / total * 100f
            row(spacing = 6.dp) {
                freeDraw(width = 10.dp, height = 10.dp) {
                    path(fill = slice.color) { rect(0f, 0f, 10f, 10f) }
                }
                text(slice.label) { fontSize = 9.sp }
                text("${formatValue(percent)}%") {
                    fontSize = 9.sp
                    color = PdfColor.Gray
                }
            }
        }
    }
}

/**
 * Appends a swatch + label legend for stacked-bar segments: one row per distinct
 * [StackedBarSegment.label] across [segments], in first-occurrence order, using
 * the colour of that first occurrence. Distinct labels are deduplicated because
 * the same segment label repeats once per group.
 */
private fun ContainerScope.segmentLegend(segments: List<StackedBarSegment>) {
    val seen = LinkedHashMap<String, PdfColor>()
    segments.forEach { segment ->
        if (max(segment.value, 0f) > 0f) seen.getOrPut(segment.label) { segment.color }
    }
    if (seen.isEmpty()) return
    spacer(height = CHART_GAP)
    column(spacing = 4.dp) {
        seen.forEach { (label, color) ->
            row(spacing = 6.dp) {
                freeDraw(width = 10.dp, height = 10.dp) {
                    path(fill = color) { rect(0f, 0f, 10f, 10f) }
                }
                text(label) { fontSize = 9.sp }
            }
        }
    }
}

/**
 * Formats a value for captions / percentages: integers drop the decimal point,
 * everything else keeps a single decimal so captions stay compact.
 */
private fun formatValue(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        // No String.format in common Kotlin — compose the one-decimal form by
        // hand from the integer and tenths parts.
        val whole = rounded.toInt()
        val tenths = kotlin.math.abs(kotlin.math.round((rounded - whole) * 10f).toInt())
        "$whole.$tenths"
    }
}

// endregion
