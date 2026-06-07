package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the chart DSL. Each chart is rendered through the real
 * `pdf { ... }` pipeline backed by [FakePdfDriverFactory], then asserted
 * against the recorded [DrawCall]s. Because every chart composes `freeDraw`,
 * its geometry surfaces as [DrawCall.Path] entries (one per authored
 * `path { }`), with filled shapes carrying a non-null [DrawCall.Path.fill] and
 * stroked outlines a non-null [DrawCall.Path.strokeColor].
 */
class ChartsTest {

    private val series = listOf(
        ChartSeries("Q1", 10f, PdfColor.Red),
        ChartSeries("Q2", 20f, PdfColor.Green),
        ChartSeries("Q3", 15f, PdfColor.Blue),
    )

    /** All [DrawCall.Path]s recorded across every page of a freshly rendered doc. */
    private fun paths(block: PageScopeBody): List<DrawCall.Path> {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                block()
            }
        }
        return factory.drivers.single().pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Path>() }
    }

    /** All text strings recorded across every page. */
    private fun texts(block: PageScopeBody): List<String> {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                block()
            }
        }
        return factory.drivers.single().pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
            .map { it.text }
    }

    private fun List<DrawCall.Path>.filled() = filter { it.fill != null }
    private fun List<DrawCall.Path>.stroked() = filter { it.strokeColor != null && it.fill == null }

    // region — bar chart

    @Test
    fun barChart_emitsOneFilledPathPerSeries_plusAxisLine() {
        val paths = paths { barChart(series, width = 200.dp, height = 100.dp) }

        assertEquals(series.size, paths.filled().size, "one filled bar per series")
        assertTrue(paths.stroked().isNotEmpty(), "expected a stroked baseline axis path")
    }

    @Test
    fun barChart_filledBars_useTheSeriesColors() {
        val paths = paths { barChart(series, width = 200.dp, height = 100.dp) }
        val fillColors = paths.filled().mapNotNull { (it.fill as? PdfPaint.Solid)?.color }

        assertTrue(fillColors.containsAll(series.map { it.color }), "bars must use series colors")
    }

    @Test
    fun barChart_drawsLabelsAndValueCaptions() {
        val texts = texts { barChart(series, width = 200.dp, height = 100.dp, showValues = true) }

        // Each label appears, and each value caption appears.
        series.forEach { assertTrue(it.label in texts, "missing bar label '${it.label}'") }
        assertTrue("10" in texts && "20" in texts && "15" in texts, "missing value captions: $texts")
    }

    @Test
    fun barChart_showValuesFalse_omitsValueCaptions_keepsLabels() {
        val texts = texts { barChart(series, width = 200.dp, height = 100.dp, showValues = false) }

        series.forEach { assertTrue(it.label in texts, "labels must still render") }
        assertTrue("10" !in texts, "value captions must be omitted when showValues = false")
    }

    @Test
    fun barChart_emptySeries_drawsNothing_andDoesNotThrow() {
        val paths = paths { barChart(emptyList(), width = 200.dp, height = 100.dp) }
        assertTrue(paths.isEmpty(), "empty series must draw no geometry")
    }

    @Test
    fun barChart_allZeroValues_drawsNothing() {
        val zero = listOf(ChartSeries("a", 0f, PdfColor.Red), ChartSeries("b", 0f, PdfColor.Blue))
        val paths = paths { barChart(zero, width = 200.dp, height = 100.dp) }
        assertTrue(paths.isEmpty(), "all-zero series has no scale and must draw nothing")
    }

    // endregion

    // region — line chart

    @Test
    fun lineChart_emitsAStrokedPath() {
        val paths = paths { lineChart(listOf(1f, 4f, 2f, 6f, 3f), width = 200.dp, height = 80.dp) }

        // The line itself is a stroked, unfilled path carrying the requested
        // stroke width, distinguishing it from the thin baseline axis.
        val line = paths.stroked().firstOrNull { it.strokeWidth == 2f }
        assertTrue(line != null, "expected a stroked polyline with the default 2f width")
    }

    @Test
    fun lineChart_fillUnderLine_addsATranslucentFilledArea() {
        val plain = paths { lineChart(listOf(1f, 4f, 2f), width = 200.dp, height = 80.dp, fillUnderLine = false) }
        val filled = paths { lineChart(listOf(1f, 4f, 2f), width = 200.dp, height = 80.dp, fillUnderLine = true) }

        assertTrue(plain.filled().isEmpty(), "no area fill when fillUnderLine = false")
        val area = filled.filled().single()
        val color = (area.fill as PdfPaint.Solid).color
        assertEquals(0.15f, color.alpha, "area fill must be translucent (alpha 0.15)")
    }

    @Test
    fun lineChart_fewerThanTwoPoints_drawsNothing() {
        assertTrue(paths { lineChart(listOf(5f), width = 200.dp, height = 80.dp) }.isEmpty())
        assertTrue(paths { lineChart(emptyList(), width = 200.dp, height = 80.dp) }.isEmpty())
    }

    @Test
    fun lineChart_flatData_doesNotThrow() {
        val paths = paths { lineChart(listOf(3f, 3f, 3f), width = 200.dp, height = 80.dp) }
        assertTrue(paths.stroked().isNotEmpty(), "flat data still draws a line")
    }

    // endregion

    // region — pie chart

    @Test
    fun pieChart_emitsOneFilledPathPerSlice() {
        // Legend swatches are also filled paths, so isolate the pie by turning
        // the legend off for the geometry count.
        val paths = paths { pieChart(series, diameter = 120.dp, showLegend = false) }

        assertEquals(series.size, paths.filled().size, "one wedge per slice")
    }

    @Test
    fun pieChart_legendDrawsSwatchesAndPercentageTexts() {
        val texts = texts { pieChart(series, diameter = 120.dp, showLegend = true) }

        series.forEach { assertTrue(it.label in texts, "legend missing label '${it.label}'") }
        assertTrue(texts.any { it.endsWith("%") }, "legend must show percentages: $texts")
    }

    @Test
    fun pieChart_emptyOrZero_drawsNothing() {
        assertTrue(paths { pieChart(emptyList(), diameter = 120.dp) }.isEmpty())
        val zero = listOf(ChartSeries("a", 0f, PdfColor.Red))
        assertTrue(paths { pieChart(zero, diameter = 120.dp) }.isEmpty())
    }

    // endregion

    // region — donut chart

    @Test
    fun donutChart_emitsOneAnnularPathPerSlice() {
        val paths = paths { donutChart(series, diameter = 120.dp, showLegend = false) }

        assertEquals(series.size, paths.filled().size, "one annular segment per slice")
    }

    @Test
    fun donutChart_segmentsContainBothOuterAndInnerArcs() {
        // An annular segment must visit the inner radius (a cubic on the way
        // back). A solid pie wedge would have a centre line-to instead, so a
        // donut path is materially longer in command count than a bare wedge.
        val donut = paths { donutChart(series, diameter = 120.dp, showLegend = false) }.filled()
        val pie = paths { pieChart(series, diameter = 120.dp, showLegend = false) }.filled()

        donut.indices.forEach { i ->
            assertTrue(
                donut[i].commands.size > pie[i].commands.size,
                "donut segment $i should have more commands (inner arc) than the pie wedge",
            )
        }
    }

    @Test
    fun donutChart_emptyOrZero_drawsNothing() {
        assertTrue(paths { donutChart(emptyList(), diameter = 120.dp) }.isEmpty())
        val zero = listOf(ChartSeries("a", 0f, PdfColor.Red))
        assertTrue(paths { donutChart(zero, diameter = 120.dp) }.isEmpty())
    }

    // endregion
}

/** Body of a `page { ... }` block, narrowed to the container surface charts need. */
private typealias PageScopeBody = ContainerScope.() -> Unit
