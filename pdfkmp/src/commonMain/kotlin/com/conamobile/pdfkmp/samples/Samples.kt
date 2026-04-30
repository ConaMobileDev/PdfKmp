package com.conamobile.pdfkmp.samples

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.HorizontalArrangement
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.layout.VerticalArrangement
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.unit.sp
import com.conamobile.pdfkmp.vector.VectorImage

/**
 * Pre-built [PdfDocument]s used by the bundled sample apps and as living
 * documentation of the API.
 *
 * Each function in this object returns a freshly-rendered document. Calling
 * the function more than once produces independent [PdfDocument] instances
 * — the rendering pipeline is repeated on every call, which is intentional
 * so changes to bundled fonts or to the renderer take effect without any
 * cache invalidation.
 *
 * The samples cover the public API surface roughly in order of complexity:
 * - [helloWorld] — a single-page document with one styled line.
 * - [typography] — every text knob (size, weight, italic, color, custom
 *   line height) on one page.
 * - [longBody] — auto-paginated multi-paragraph body text demonstrating the
 *   default `MoveToNextPage` strategy.
 * - [slicedBody] — the same body using `Slice` so paragraphs are split at
 *   line boundaries instead of being moved whole.
 */
public object Samples {

    /** Single page, one line of bold blue text. The simplest API call. */
    public fun helloWorld(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Hello World" }
        page {
            text("Hello, world!") {
                fontSize = 24.sp
                bold = true
                color = PdfColor.Blue
            }
        }
    }

    /**
     * Showcases every [TextStyle] knob in one page — pick this sample as a
     * reference when figuring out what is configurable.
     */
    public fun typography(): PdfDocument = pdf {
        metadata {
            title = "PdfKmp – Typography Showcase"
            author = "PdfKmp"
        }
        defaultTextStyle = TextStyle(
            fontSize = 14.sp,
            color = PdfColor.DarkGray,
        )
        page {
            spacing = 8.dp

            text("PdfKmp Typography") {
                fontSize = 28.sp
                fontWeight = FontWeight.Bold
                color = PdfColor.Black
            }
            text("Every property of TextStyle on a single page.")

            text("Heading 2 — semibold") {
                fontSize = 20.sp
                fontWeight = FontWeight.SemiBold
                color = PdfColor.Black
            }

            text("Body regular: lorem ipsum dolor sit amet, consectetur adipiscing elit.")
            text("Body bold: lorem ipsum dolor sit amet, consectetur adipiscing elit.") { bold = true }
            text("Body italic: lorem ipsum dolor sit amet, consectetur adipiscing elit.") { italic = true }
            text("Body bold italic: lorem ipsum dolor sit amet, consectetur adipiscing elit.") {
                bold = true
                italic = true
            }

            text("Coloured text — red") { color = PdfColor.Red }
            text("Coloured text — green") { color = PdfColor.Green }
            text("Coloured text — blue") { color = PdfColor.Blue }

            text("Letter-spaced text") { letterSpacing = 2.sp }
            text("Generous line height for multi-line readability — wraps across two visible lines on A4.") {
                lineHeight = 22.sp
            }

            text("Text decorations") {
                fontSize = 18.sp; fontWeight = FontWeight.SemiBold; color = PdfColor.Black
            }
            text("Underlined heading") { fontSize = 16.sp; underline = true }
            text("Struck-through text") { strikethrough = true }
            text("Both decorations together") { underline = true; strikethrough = true }

            text("Alignment") {
                fontSize = 18.sp; fontWeight = FontWeight.SemiBold; color = PdfColor.Black
            }
            text("Start: $LOREM") { align = TextAlign.Start }
            text("Center: $LOREM") { align = TextAlign.Center }
            text("End: $LOREM") { align = TextAlign.End }
            text("Justify (falls back to Start in v1): $LOREM") { align = TextAlign.Justify }

            text("Rich text — multi-style spans inside one wrapping paragraph") {
                fontSize = 18.sp; fontWeight = FontWeight.SemiBold; color = PdfColor.Black
            }
            richText {
                span("This sentence mixes ")
                span("bold") { bold = true }
                span(", ")
                span("italic") { italic = true }
                span(", ")
                span("red") { color = PdfColor.Red }
                span(", ")
                span("underlined") { underline = true }
                span(", and ")
                span("struck-through") { strikethrough = true }
                span(" runs while wrapping naturally across the paragraph width. ")
                span("Bigger emphasis") { fontSize = 18.sp; bold = true }
                span(" scales the line up to fit the largest run.")
            }
        }
    }

    /**
     * Long body text that overflows a single page. Uses the default
     * [PageBreakStrategy.MoveToNextPage] so each paragraph stays whole.
     */
    public fun longBody(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Long Body" }
        page {
            spacing = 12.dp
            for (i in 1..40) {
                text("Paragraph $i. ${LOREM.repeat(2)}")
            }
        }
    }

    /**
     * Same content as [longBody] but with [PageBreakStrategy.Slice]: long
     * paragraphs are split at line boundaries instead of being pushed
     * whole-cloth onto a new page.
     */
    public fun slicedBody(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Sliced Body" }
        defaultPageBreakStrategy = PageBreakStrategy.Slice
        page {
            spacing = 12.dp
            for (i in 1..40) {
                text("Paragraph $i. ${LOREM.repeat(2)}")
            }
        }
    }

    /**
     * Embeds a developer-supplied image alongside text. The image bytes
     * normally come from app assets / network — sample apps load them from
     * their bundle and pass them in here.
     *
     * @param imageBytes encoded PNG/JPEG bytes of the image to embed.
     */
    public fun withImage(imageBytes: ByteArray): PdfDocument = pdf {
        metadata { title = "PdfKmp – Image" }
        page {
            spacing = 12.dp
            text("Image rendered with ContentScale.Fit") {
                fontSize = 18.sp
                bold = true
            }
            image(bytes = imageBytes, width = 300.dp, contentScale = ContentScale.Fit)

            spacer(height = 12.dp)
            text("Same image, ContentScale.Crop into a square frame") {
                fontSize = 18.sp
                bold = true
            }
            image(
                bytes = imageBytes,
                width = 200.dp,
                height = 200.dp,
                contentScale = ContentScale.Crop,
            )
        }
    }

    /**
     * Embeds a tall image that doesn't fit on a single page so the
     * [PageBreakStrategy.Slice] logic kicks in and splits the picture
     * across two pages.
     */
    public fun slicedImage(imageBytes: ByteArray): PdfDocument = pdf {
        metadata { title = "PdfKmp – Sliced Image" }
        defaultPageBreakStrategy = PageBreakStrategy.Slice
        page(PageSize.A5) {
            text("Tall image — sliced across pages") {
                fontSize = 16.sp
                bold = true
            }
            spacer(height = 12.dp)
            image(
                bytes = imageBytes,
                width = 400.dp,
                height = 1200.dp,
                contentScale = ContentScale.FillBounds,
            )
        }
    }

    /**
     * Demonstrates vector / SVG icons — both [Android-style](#) `<vector>`
     * XML and standard `<svg>` are accepted, scaled to a requested size,
     * and optionally tinted at draw time.
     */
    public fun vectorShowcase(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Vector / SVG" }

        val starVector = VectorImage.parse(STAR_ANDROID_VECTOR)
        val heartSvg = VectorImage.parse(HEART_SVG)

        page {
            spacing = 16.dp

            text("Vector graphics") {
                fontSize = 22.sp
                bold = true
            }
            text("Both Android <vector> XML and W3C <svg> are accepted by `VectorImage.parse`. Icons keep their vector form in the PDF and stay sharp at any zoom level.") {
                fontSize = 11.sp
                color = PdfColor.Gray
            }

            // Same vector at three sizes — illustrates that scaling stays vector.
            row(spacing = 24.dp, verticalAlignment = VerticalAlignment.Center) {
                vector(image = starVector, width = 32.dp)
                vector(image = starVector, width = 64.dp)
                vector(image = starVector, width = 96.dp)
            }

            spacer(height = 12.dp)

            // Tint the same icon in three brand colours — the source XML is unchanged.
            row(spacing = 24.dp, verticalAlignment = VerticalAlignment.Center) {
                vector(image = heartSvg, width = 64.dp, tint = PdfColor.Red)
                vector(image = heartSvg, width = 64.dp, tint = PdfColor.Blue)
                vector(image = heartSvg, width = 64.dp, tint = PdfColor.Green)
            }

            spacer(height = 12.dp)

            // Inline parsing for one-off icons.
            text("Inline parsing — pass the XML string directly:") {
                fontSize = 12.sp
                color = PdfColor.Gray
            }
            vector(xml = STAR_ANDROID_VECTOR, width = 48.dp, tint = PdfColor.fromRgb(0xE6A100))

            spacer(height = 16.dp)

            // Circle + ellipse primitives — 4-cubic-Bézier paths so they
            // stay smooth at any zoom level, with solid / gradient / stroke
            // fill options.
            text("Circle & Ellipse primitives") {
                fontSize = 16.sp; bold = true
            }
            text("Built-in geometric shapes: circles and ellipses with solid fills, gradient paints, or stroke-only outlines.") {
                fontSize = 11.sp; color = PdfColor.Gray
            }
            row(spacing = 16.dp, verticalAlignment = VerticalAlignment.Center) {
                circle(diameter = 56.dp, fill = PdfColor.Red)
                circle(
                    diameter = 56.dp,
                    fillPaint = PdfPaint.radialGradient(
                        from = PdfColor.White, to = PdfColor.Blue,
                        centerX = 28f, centerY = 28f, radius = 28f,
                    ),
                )
                ellipse(width = 100.dp, height = 56.dp, fill = PdfColor.Green)
                circle(diameter = 56.dp, strokeColor = PdfColor.Black, strokeWidth = 2.dp)
                circle(
                    diameter = 56.dp,
                    fill = PdfColor.LightGray,
                    strokeColor = PdfColor.DarkGray,
                    strokeWidth = 1.dp,
                )
            }
        }
    }

    /**
     * Demonstrates advanced SVG features: gradients, elliptical arcs,
     * group transforms — all rendered as vector PDF operators.
     */
    public fun vectorAdvanced(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Advanced SVG" }
        page {
            spacing = 16.dp
            text("Advanced vector rendering") {
                fontSize = 22.sp; bold = true
            }
            text("Gradients, elliptical arcs, group transforms — all rendered as vector PDF operators, no rasterisation.") {
                fontSize = 11.sp; color = PdfColor.Gray
            }

            row(spacing = 24.dp, verticalAlignment = VerticalAlignment.Center) {
                vector(xml = LINEAR_GRADIENT_SVG, width = 96.dp)
                vector(xml = RADIAL_GRADIENT_SVG, width = 96.dp)
                vector(xml = ARC_PIE_CHART_SVG, width = 96.dp)
            }

            spacer(height = 12.dp)
            text("Group transforms — three rotated triangles around a centre") {
                fontSize = 12.sp; color = PdfColor.Gray
            }
            vector(xml = GROUP_TRANSFORMS_SVG, width = 200.dp)
        }
    }

    private const val STAR_ANDROID_VECTOR: String = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFC107"
        android:pathData="M12,2L14.59,8.36L21.5,9.27L16.5,14.14L17.77,21L12,17.77L6.23,21L7.5,14.14L2.5,9.27L9.41,8.36Z" />
</vector>"""

    private const val HEART_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
    <path fill="#000000" d="M12,21.35L10.55,20.03C5.4,15.36 2,12.28 2,8.5C2,5.42 4.42,3 7.5,3C9.24,3 10.91,3.81 12,5.09C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.42 22,8.5C22,12.28 18.6,15.36 13.45,20.04L12,21.35Z"/>
</svg>"""

    private const val LINEAR_GRADIENT_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
    <linearGradient id="grad1" x1="0" y1="0" x2="100" y2="100">
        <stop offset="0" stop-color="#FF5722" />
        <stop offset="1" stop-color="#FFC107" />
    </linearGradient>
    <path fill="url(#grad1)" d="M10,10 L90,10 L90,90 L10,90 Z" />
</svg>"""

    private const val RADIAL_GRADIENT_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
    <radialGradient id="grad2" cx="50" cy="50" r="50">
        <stop offset="0" stop-color="#7986CB" />
        <stop offset="1" stop-color="#0D47A1" />
    </radialGradient>
    <path fill="url(#grad2)" d="M50,5 A45,45 0 1,1 49.99,5 Z" />
</svg>"""

    private const val ARC_PIE_CHART_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
    <path fill="#E91E63" d="M50,50 L50,5 A45,45 0 0,1 95,50 Z" />
    <path fill="#9C27B0" d="M50,50 L95,50 A45,45 0 0,1 50,95 Z" />
    <path fill="#673AB7" d="M50,50 L50,95 A45,45 0 1,1 50,5 Z" />
</svg>"""

    private const val GROUP_TRANSFORMS_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
    <g transform="translate(50 50)">
        <g transform="rotate(0)">
            <path fill="#F44336" d="M-30,-15 L0,-30 L0,0 Z" />
        </g>
        <g transform="rotate(120)">
            <path fill="#4CAF50" d="M-30,-15 L0,-30 L0,0 Z" />
        </g>
        <g transform="rotate(240)">
            <path fill="#2196F3" d="M-30,-15 L0,-30 L0,0 Z" />
        </g>
    </g>
</svg>"""

    /**
     * Custom design composition: hero image with text overlay (using
     * [box]), three statistics cards (using [card]), and a styled todo
     * list (using decorated [column] / [row]). Demonstrates how the
     * three new primitives combine to build production-grade layouts.
     */
    public fun customDesigns(imageBytes: ByteArray): PdfDocument = pdf {
        metadata { title = "PdfKmp – Custom Designs" }
        page {
            spacing = 20.dp

            // HERO — image with text overlay via Box.
            box(
                width = 460.dp,
                height = 180.dp,
                cornerRadius = 16.dp,
            ) {
                // Bottom layer — full-bleed image.
                image(
                    bytes = imageBytes,
                    width = 460.dp,
                    height = 180.dp,
                    contentScale = ContentScale.Crop,
                )
                // Top layer — text pinned to the bottom-left corner.
                aligned(BoxAlignment.BottomStart) {
                    column(padding = Padding.all(20.dp)) {
                        text("Quarterly Report") {
                            fontSize = 32.sp
                            bold = true
                            color = PdfColor.White
                        }
                        text("Q1 2026 — All teams") {
                            fontSize = 14.sp
                            color = PdfColor.White
                        }
                    }
                }
            }

            // STATS — three cards in a row, each using card() with a
            // coloured background and rounded corners.
            row(spacing = 12.dp) {
                weighted(1f) {
                    card(
                        background = PdfColor.fromRgb(0xE3F2FD),
                        cornerRadius = 12.dp,
                        padding = Padding.all(16.dp),
                    ) {
                        text("Revenue") { fontSize = 11.sp; color = PdfColor.Gray }
                        text("$1.2M") { fontSize = 28.sp; bold = true }
                        text("+12% YoY") { fontSize = 11.sp; color = PdfColor.Green }
                    }
                }
                weighted(1f) {
                    card(
                        background = PdfColor.fromRgb(0xFFF3E0),
                        cornerRadius = 12.dp,
                        padding = Padding.all(16.dp),
                    ) {
                        text("Customers") { fontSize = 11.sp; color = PdfColor.Gray }
                        text("1,840") { fontSize = 28.sp; bold = true }
                        text("+8% YoY") { fontSize = 11.sp; color = PdfColor.Green }
                    }
                }
                weighted(1f) {
                    card(
                        background = PdfColor.fromRgb(0xFFEBEE),
                        cornerRadius = 12.dp,
                        padding = Padding.all(16.dp),
                    ) {
                        text("Churn") { fontSize = 11.sp; color = PdfColor.Gray }
                        text("3.4%") { fontSize = 28.sp; bold = true }
                        text("-0.5pp YoY") { fontSize = 11.sp; color = PdfColor.Red }
                    }
                }
            }

            // TODO LIST — sequence of decorated rows.
            text("Action items") { fontSize = 16.sp; bold = true }
            val items = listOf(
                Todo("Buy groceries", done = true, priority = "Low"),
                Todo("Review PR #42", done = false, priority = "High"),
                Todo("Call dentist", done = false, priority = "Med"),
                Todo("Send invoice", done = true, priority = "Med"),
            )
            column(spacing = 8.dp) {
                items.forEach { todo ->
                    row(
                        spacing = 12.dp,
                        verticalAlignment = VerticalAlignment.Center,
                        background = if (todo.done) PdfColor.fromRgb(0xE8F5E9) else PdfColor.White,
                        cornerRadius = 10.dp,
                        padding = Padding.symmetric(horizontal = 14.dp, vertical = 12.dp),
                        border = BorderStroke(1.dp, PdfColor.LightGray),
                    ) {
                        // Status icon — circle (open) or check (done).
                        vector(
                            xml = if (todo.done) CHECK_ICON_SVG else CIRCLE_ICON_SVG,
                            width = 18.dp,
                            tint = if (todo.done) PdfColor.Green else PdfColor.Gray,
                        )
                        weighted(1f) {
                            text(todo.title) {
                                fontSize = 14.sp
                                fontWeight = if (todo.done) FontWeight.Normal else FontWeight.SemiBold
                                color = if (todo.done) PdfColor.Gray else PdfColor.Black
                            }
                        }
                        // Priority badge — small pill.
                        column(
                            background = priorityBackground(todo.priority),
                            cornerRadius = 8.dp,
                            padding = Padding.symmetric(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            text(todo.priority) {
                                fontSize = 10.sp
                                bold = true
                                color = priorityColor(todo.priority)
                            }
                        }
                    }
                }
            }
        }

        // PAGE 2 — gradient backgrounds, per-corner radius, per-side
        // borders, dashed / dotted dividers, circles + ellipses.
        page {
            spacing = 16.dp

            text("Decorations") { fontSize = 24.sp; bold = true }
            divider(thickness = 1.dp)

            // Gradient banner with top-only rounded corners — tab pattern.
            text("Linear gradient with top-rounded corners:") { bold = true; fontSize = 12.sp }
            box(
                width = 460.dp,
                height = 80.dp,
                cornerRadiusEach = CornerRadius.top(16.dp),
                backgroundPaint = PdfPaint.linearGradient(
                    from = PdfColor.Blue, to = PdfColor.Red,
                    endX = 460f, endY = 0f,
                ),
            ) {
                aligned(BoxAlignment.Center) {
                    text("Sunrise tab") {
                        color = PdfColor.White; bold = true; fontSize = 20.sp
                    }
                }
            }

            // Radial gradient.
            text("Radial gradient spotlight:") { bold = true; fontSize = 12.sp }
            box(
                width = 460.dp,
                height = 100.dp,
                cornerRadius = 12.dp,
                backgroundPaint = PdfPaint.radialGradient(
                    from = PdfColor.White,
                    to = PdfColor(0.1f, 0.1f, 0.4f),
                    centerX = 230f, centerY = 50f, radius = 230f,
                ),
            ) {
                aligned(BoxAlignment.Center) {
                    text("Spotlight") {
                        color = PdfColor(0.1f, 0.1f, 0.4f); bold = true; fontSize = 22.sp
                    }
                }
            }

            // Asymmetric corners + per-side accent border.
            row(spacing = 12.dp) {
                weighted(1f) {
                    box(
                        height = 80.dp,
                        background = PdfColor.LightGray,
                        cornerRadiusEach = CornerRadius(
                            topLeft = 24.dp, topRight = 4.dp,
                            bottomLeft = 4.dp, bottomRight = 24.dp,
                        ),
                    ) {
                        aligned(BoxAlignment.Center) { text("Diagonal corners") }
                    }
                }
                weighted(1f) {
                    card(
                        cornerRadius = 0.dp,
                        padding = Padding.all(12.dp),
                        borderEach = BorderSides(
                            bottom = BorderStroke(2.dp, PdfColor.Blue),
                            left = BorderStroke(2.dp, PdfColor.Blue),
                        ),
                    ) {
                        text("Accent quote — bottom + left only") { bold = true; fontSize = 12.sp }
                        text("borderEach lets you outline only the sides you ask for.") {
                            fontSize = 11.sp
                        }
                    }
                }
            }

            // Circles + ellipses (solid, gradient, outline).
            text("Circles & ellipses:") { bold = true; fontSize = 12.sp }
            row(spacing = 16.dp, verticalAlignment = VerticalAlignment.Center) {
                circle(diameter = 56.dp, fill = PdfColor.Red)
                circle(
                    diameter = 56.dp,
                    fillPaint = PdfPaint.radialGradient(
                        from = PdfColor.White, to = PdfColor.Blue,
                        centerX = 28f, centerY = 28f, radius = 28f,
                    ),
                )
                ellipse(width = 100.dp, height = 56.dp, fill = PdfColor.Green)
                circle(diameter = 56.dp, strokeColor = PdfColor.Black, strokeWidth = 2.dp)
            }

            // Dashed / dotted dividers.
            text("Solid, dashed, dotted dividers:") { bold = true; fontSize = 12.sp }
            divider(thickness = 1.dp, color = PdfColor.Black)
            divider(thickness = 1.5.dp, color = PdfColor.Gray, style = LineStyle.Dashed)
            divider(thickness = 2.dp, color = PdfColor.Red, style = LineStyle.Dotted)
        }
    }

    private data class Todo(val title: String, val done: Boolean, val priority: String)

    private fun priorityBackground(priority: String): PdfColor = when (priority) {
        "High" -> PdfColor.fromRgb(0xFFCDD2)
        "Med" -> PdfColor.fromRgb(0xFFE0B2)
        else -> PdfColor.fromRgb(0xC8E6C9)
    }

    private fun priorityColor(priority: String): PdfColor = when (priority) {
        "High" -> PdfColor.fromRgb(0xC62828)
        "Med" -> PdfColor.fromRgb(0xE65100)
        else -> PdfColor.fromRgb(0x2E7D32)
    }

    private const val CIRCLE_ICON_SVG: String = """<svg viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M12,3 A9,9 0 1,1 11.99,3 Z"/></svg>"""
    private const val CHECK_ICON_SVG: String = """<svg viewBox="0 0 24 24"><path fill="currentColor" d="M9,16.17L4.83,12L3.41,13.41L9,19L21,7L19.59,5.59L9,16.17Z"/></svg>"""

    /**
     * Demonstrates the table DSL — header + alternating-row body, mixed
     * fixed and weighted columns, custom border, and rounded corners.
     */
    public fun tableShowcase(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Tables" }

        val users = listOf(
            User(id = "U-001", name = "Aisha Ahmadova", email = "aisha@example.com", status = "Active"),
            User(id = "U-002", name = "Bobur Karimov", email = "bobur@example.com", status = "Pending"),
            User(id = "U-003", name = "Charos Ergasheva", email = "charos@example.com", status = "Active"),
            User(id = "U-004", name = "Doniyor Aliyev", email = "doniyor@example.com", status = "Inactive"),
            User(id = "U-005", name = "Elina Rahmonova", email = "elina@example.com", status = "Active"),
        )

        page {
            spacing = 16.dp

            text("Users") {
                fontSize = 22.sp
                bold = true
            }
            text("Demonstrates the full table DSL: per-row backgrounds, custom border, rounded corners, mixed fixed and weighted columns, multi-line cells, and easy iteration.") {
                fontSize = 11.sp
                color = PdfColor.Gray
            }

            table(
                columns = listOf(
                    TableColumn.Fixed(70.dp),
                    TableColumn.Weight(2f),
                    TableColumn.Weight(1f),
                ),
                border = TableBorder(
                    color = PdfColor.fromRgb(0xCFD8DC),
                    width = 1.dp,
                ),
                cornerRadius = 10.dp,
                cellPadding = Padding.symmetric(horizontal = 12.dp, vertical = 10.dp),
            ) {
                header(background = PdfColor.fromRgb(0xECEFF1)) {
                    cell("ID")
                    cell("Customer")
                    cell("Status", horizontalAlignment = HorizontalAlignment.End)
                }

                users.forEachIndexed { index, user ->
                    val zebra = if (index % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)
                    row(background = zebra) {
                        cell(user.id) {
                            color = PdfColor.Gray
                            fontSize = 11.sp
                        }
                        cell {
                            text(user.name) { fontSize = 14.sp; bold = true }
                            text(user.email) { fontSize = 10.sp; color = PdfColor.Gray }
                        }
                        cell(
                            value = user.status,
                            horizontalAlignment = HorizontalAlignment.End,
                            verticalAlignment = VerticalAlignment.Center,
                        ) {
                            fontSize = 12.sp
                            bold = true
                            color = when (user.status) {
                                "Active" -> PdfColor.Green
                                "Pending" -> PdfColor.fromRgb(0xE6A100)
                                else -> PdfColor.Red
                            }
                        }
                    }
                }
            }

            spacer(height = 24.dp)

            // Bulleted + numbered list helpers below the table.
            text("List helpers") { fontSize = 18.sp; fontWeight = FontWeight.SemiBold }
            text("Bulleted:") { bold = true; fontSize = 12.sp }
            bulletList(
                items = listOf(
                    "Vector text and shapes — sharp at any zoom level.",
                    "Wrapped continuation lines align under the first text line, not under the bullet.",
                    "Custom marker characters: pass `bullet = \"→\"` to override the default.",
                ),
            )
            text("Numbered (custom start):") { bold = true; fontSize = 12.sp }
            numberedList(
                items = listOf(
                    "Author the document via the DSL.",
                    "Pick a backend driver — Android or iOS.",
                    "Render and save the bytes to your storage of choice.",
                ),
                startAt = 1,
            )
        }
    }

    private data class User(
        val id: String,
        val name: String,
        val email: String,
        val status: String,
    )

    /**
     * Demonstrates the row / column / weighted DSL: a header row with a
     * left title, flexible spacer, and right metadata; a body column with
     * three weighted blocks; a footer row centred via
     * [HorizontalArrangement.Center].
     */
    public fun rowAndColumn(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Row & Column" }
        page {
            spacing = 16.dp

            // Header — title on the left, date pushed to the right with a weighted spacer.
            row(verticalAlignment = VerticalAlignment.Center) {
                text("Quarterly Report") {
                    fontSize = 22.sp
                    bold = true
                }
                weighted(1f) { spacer() }
                text("Q1 2026") {
                    fontSize = 14.sp
                    color = PdfColor.Gray
                }
            }

            // Three columns of equal width using weight = 1.
            row(spacing = 12.dp) {
                weighted(1f) {
                    text("Revenue") { bold = true; fontSize = 14.sp }
                    text("$1.2M")
                    text("+12% YoY") { color = PdfColor.Green; fontSize = 11.sp }
                }
                weighted(1f) {
                    text("Customers") { bold = true; fontSize = 14.sp }
                    text("1,840")
                    text("+8% YoY") { color = PdfColor.Green; fontSize = 11.sp }
                }
                weighted(1f) {
                    text("Churn") { bold = true; fontSize = 14.sp }
                    text("3.4%")
                    text("-0.5pp YoY") { color = PdfColor.Red; fontSize = 11.sp }
                }
            }

            spacer(height = 20.dp)

            // Centred footer using HorizontalArrangement.Center.
            row(horizontalArrangement = HorizontalArrangement.Center) {
                text("Generated by PdfKmp") {
                    fontSize = 10.sp
                    color = PdfColor.Gray
                    italic = true
                }
            }
        }
    }

    /**
     * Demonstrates [VerticalArrangement.SpaceBetween] on a column: header
     * pinned to the top, footer pinned to the bottom of the page.
     */
    public fun columnSpaceBetween(): PdfDocument = pdf {
        metadata { title = "PdfKmp – Column SpaceBetween" }
        page {
            verticalArrangement = VerticalArrangement.SpaceBetween
            horizontalAlignment = HorizontalAlignment.Center

            text("Header") {
                fontSize = 28.sp
                bold = true
            }
            text("This middle paragraph stays where the column places it; the header is pinned to the top and the footer is pinned to the bottom.")
            text("— Footer —") {
                fontSize = 12.sp
                color = PdfColor.Gray
                italic = true
            }
        }
    }

    /**
     * Demonstrates [DocumentScope.defaultPagePadding] vs. per-page override.
     * The first page uses the document default; the second page tightens
     * it.
     */
    public fun customPadding(): PdfDocument = pdf {
        defaultPagePadding = Padding.symmetric(horizontal = 60.dp, vertical = 80.dp)
        page {
            text("Page 1 uses the document default padding (60×80 dp).")
        }
        page(PageSize.A5) {
            padding = Padding.all(20.dp)
            text("Page 2 overrides to 20 dp on every side.")
        }
    }

    /**
     * A polished, brochure-style PDF designed for the README hero image.
     * Mixes large display type, gradient banners, stat cards, a styled
     * table, decorative shapes, dividers, and rich text — exercising the
     * library's visual range in a single 2-page document.
     *
     * No raster images: every visual element is generated from PdfKmp
     * primitives (text, shapes, gradients, vectors), so this sample
     * runs identically on every platform without bundled assets.
     */
    public fun brochure(): PdfDocument = pdf {
        metadata {
            title = "PdfKmp — Brochure"
            author = "PdfKmp"
            subject = "Library showcase"
        }
        defaultTextStyle = TextStyle(fontSize = 11.sp, color = PdfColor(0.20f, 0.22f, 0.27f))

        // ─── PAGE 1 ─── Hero + stats + feature cards
        page {
            padding = Padding.symmetric(horizontal = 36.dp, vertical = 40.dp)
            spacing = 24.dp

            // Hero gradient banner with title + tagline
            box(
                width = 523.dp,
                height = 200.dp,
                cornerRadius = 20.dp,
                backgroundPaint = PdfPaint.linearGradient(
                    from = PdfColor(0.05f, 0.10f, 0.40f),
                    to = PdfColor(0.45f, 0.15f, 0.60f),
                    endX = 523f, endY = 200f,
                ),
            ) {
                aligned(BoxAlignment.TopEnd) {
                    column(padding = Padding.all(28.dp)) {
                        circle(diameter = 50.dp, fill = PdfColor(1f, 1f, 1f, 0.15f))
                    }
                }
                aligned(BoxAlignment.BottomStart) {
                    column(padding = Padding.all(36.dp), spacing = 6.dp) {
                        text("PDFKMP") {
                            fontSize = 11.sp
                            letterSpacing = 4.sp
                            color = PdfColor(1f, 1f, 1f, 0.7f)
                            bold = true
                        }
                        text("Beautiful PDFs,") {
                            fontSize = 38.sp; bold = true; color = PdfColor.White
                        }
                        text("from Kotlin Multiplatform.") {
                            fontSize = 38.sp; bold = true; color = PdfColor(1f, 1f, 1f, 0.85f)
                        }
                        spacer(height = 10.dp)
                        text("Compose-style DSL · Vector text & shapes · Android + iOS") {
                            fontSize = 12.sp; color = PdfColor(1f, 1f, 1f, 0.75f)
                        }
                    }
                }
            }

            // Three stat cards
            row(spacing = 14.dp) {
                weighted(1f) {
                    card(
                        background = PdfColor(0.96f, 0.97f, 1f),
                        cornerRadius = 14.dp,
                        padding = Padding.all(18.dp),
                        borderEach = BorderSides(left = BorderStroke(3.dp, PdfColor(0.20f, 0.40f, 0.95f))),
                    ) {
                        text("VECTOR OUTPUT") {
                            fontSize = 9.sp; bold = true; letterSpacing = 1.2.sp
                            color = PdfColor(0.20f, 0.40f, 0.95f)
                        }
                        spacer(height = 6.dp)
                        text("Crisp at any zoom") { fontSize = 18.sp; bold = true; color = PdfColor.Black }
                        spacer(height = 4.dp)
                        text("Glyphs and shapes are paths, never bitmaps.") {
                            fontSize = 10.sp; color = PdfColor(0.40f, 0.45f, 0.55f)
                        }
                    }
                }
                weighted(1f) {
                    card(
                        background = PdfColor(1f, 0.97f, 0.95f),
                        cornerRadius = 14.dp,
                        padding = Padding.all(18.dp),
                        borderEach = BorderSides(left = BorderStroke(3.dp, PdfColor(0.95f, 0.45f, 0.20f))),
                    ) {
                        text("CROSS-PLATFORM") {
                            fontSize = 9.sp; bold = true; letterSpacing = 1.2.sp
                            color = PdfColor(0.95f, 0.45f, 0.20f)
                        }
                        spacer(height = 6.dp)
                        text("One DSL · two OSes") { fontSize = 18.sp; bold = true; color = PdfColor.Black }
                        spacer(height = 4.dp)
                        text("Identical output on Android and iOS.") {
                            fontSize = 10.sp; color = PdfColor(0.40f, 0.45f, 0.55f)
                        }
                    }
                }
                weighted(1f) {
                    card(
                        background = PdfColor(0.95f, 1f, 0.96f),
                        cornerRadius = 14.dp,
                        padding = Padding.all(18.dp),
                        borderEach = BorderSides(left = BorderStroke(3.dp, PdfColor(0.10f, 0.65f, 0.40f))),
                    ) {
                        text("BUILT-IN INTER") {
                            fontSize = 9.sp; bold = true; letterSpacing = 1.2.sp
                            color = PdfColor(0.10f, 0.65f, 0.40f)
                        }
                        spacer(height = 6.dp)
                        text("No font drama") { fontSize = 18.sp; bold = true; color = PdfColor.Black }
                        spacer(height = 4.dp)
                        text("CJK / Arabic / Persian via system fallbacks.") {
                            fontSize = 10.sp; color = PdfColor(0.40f, 0.45f, 0.55f)
                        }
                    }
                }
            }

            // Section heading + intro paragraph
            text("Why PdfKmp?") { fontSize = 22.sp; bold = true; color = PdfColor.Black }
            divider(thickness = 0.8.dp, color = PdfColor(0.85f, 0.87f, 0.90f))
            richText {
                span("PdfKmp is a ")
                span("Compose-style") { bold = true }
                span(" PDF generator for ")
                span("Android and iOS") { bold = true; color = PdfColor(0.20f, 0.40f, 0.95f) }
                span(". Build documents from a tree of typed nodes, with full control over ")
                span("typography") { italic = true }
                span(", layout, decorations, and pagination — and ship the bytes the same way you would any other dependency.")
            }

            // Feature pills row
            row(spacing = 8.dp) {
                listOf(
                    "DSL"        to (PdfColor(0.93f, 0.95f, 1f)   to PdfColor(0.20f, 0.40f, 0.95f)),
                    "Tables"     to (PdfColor(1f, 0.96f, 0.93f)   to PdfColor(0.95f, 0.45f, 0.20f)),
                    "Vector"     to (PdfColor(0.93f, 1f, 0.95f)   to PdfColor(0.10f, 0.65f, 0.40f)),
                    "Gradients"  to (PdfColor(0.97f, 0.93f, 1f)   to PdfColor(0.55f, 0.20f, 0.85f)),
                    "Hyperlinks" to (PdfColor(1f, 0.94f, 0.96f)   to PdfColor(0.85f, 0.20f, 0.45f)),
                    "i18n fonts" to (PdfColor(0.95f, 0.95f, 1f)   to PdfColor(0.25f, 0.25f, 0.55f)),
                    "Watermarks" to (PdfColor(0.96f, 0.98f, 0.92f) to PdfColor(0.45f, 0.55f, 0.20f)),
                ).forEach { (label, colors) ->
                    box(
                        cornerRadius = 100.dp,
                        background = colors.first,
                        padding = Padding.symmetric(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        text(label) {
                            fontSize = 10.sp; bold = true; color = colors.second
                        }
                    }
                }
            }
        }

        // ─── PAGE 2 ─── Table + lists + decorative footer
        page {
            padding = Padding.symmetric(horizontal = 36.dp, vertical = 40.dp)
            spacing = 22.dp

            // Section: Table
            row(horizontalArrangement = HorizontalArrangement.SpaceBetween, verticalAlignment = VerticalAlignment.Center) {
                column {
                    text("Q1 2026 Sales") { fontSize = 22.sp; bold = true; color = PdfColor.Black }
                    text("Quarterly performance, top regions") {
                        fontSize = 11.sp; color = PdfColor(0.45f, 0.50f, 0.60f)
                    }
                }
                box(
                    cornerRadius = 100.dp,
                    background = PdfColor(0.10f, 0.65f, 0.40f),
                    padding = Padding.symmetric(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    text("+18% YoY") { fontSize = 11.sp; bold = true; color = PdfColor.White }
                }
            }

            table(
                columns = listOf(
                    TableColumn.Weight(2f),
                    TableColumn.Fixed(85.dp),
                    TableColumn.Fixed(95.dp),
                    TableColumn.Fixed(70.dp),
                ),
                border = TableBorder(color = PdfColor(0.85f, 0.87f, 0.90f), width = 1.dp),
                cornerRadius = 12.dp,
                cellPadding = Padding.symmetric(horizontal = 14.dp, vertical = 11.dp),
            ) {
                header(background = PdfColor(0.97f, 0.98f, 1f)) {
                    cell("Region") {
                        bold = true; color = PdfColor(0.35f, 0.40f, 0.50f); fontSize = 10.sp; letterSpacing = 0.6.sp
                    }
                    cell("Customers", horizontalAlignment = HorizontalAlignment.End) {
                        bold = true; color = PdfColor(0.35f, 0.40f, 0.50f); fontSize = 10.sp; letterSpacing = 0.6.sp
                    }
                    cell("Revenue", horizontalAlignment = HorizontalAlignment.End) {
                        bold = true; color = PdfColor(0.35f, 0.40f, 0.50f); fontSize = 10.sp; letterSpacing = 0.6.sp
                    }
                    cell("YoY", horizontalAlignment = HorizontalAlignment.End) {
                        bold = true; color = PdfColor(0.35f, 0.40f, 0.50f); fontSize = 10.sp; letterSpacing = 0.6.sp
                    }
                }
                listOf(
                    Region("North America", "1,840", "$1.42M", "+12%", true),
                    Region("Europe",         "1,210", "$0.96M", "+8%",  true),
                    Region("Asia Pacific",   "  890", "$0.74M", "+24%", true),
                    Region("Latin America",  "  410", "$0.31M", "+5%",  true),
                    Region("Africa",         "  165", "$0.09M", "−2%",  false),
                ).forEachIndexed { i, r ->
                    val zebra = if (i % 2 == 0) PdfColor.White else PdfColor(0.98f, 0.98f, 0.99f)
                    row(background = zebra) {
                        cell {
                            text(r.name) { bold = true; color = PdfColor.Black }
                            text("HQ activity, projects, partnerships") {
                                fontSize = 9.sp; color = PdfColor(0.55f, 0.60f, 0.70f)
                            }
                        }
                        cell(r.customers, horizontalAlignment = HorizontalAlignment.End) { fontSize = 12.sp }
                        cell(r.revenue, horizontalAlignment = HorizontalAlignment.End) { bold = true; fontSize = 13.sp }
                        cell(r.yoy, horizontalAlignment = HorizontalAlignment.End) {
                            bold = true
                            color = if (r.up) PdfColor(0.10f, 0.65f, 0.40f) else PdfColor(0.85f, 0.20f, 0.20f)
                        }
                    }
                }
            }

            // Two-column section: Highlights + Roadmap
            row(spacing = 18.dp, verticalAlignment = VerticalAlignment.Top) {
                weighted(1f) {
                    text("Q1 highlights") { fontSize = 14.sp; bold = true; color = PdfColor.Black }
                    spacer(height = 6.dp)
                    bulletList(
                        items = listOf(
                            "Closed three enterprise contracts.",
                            "APAC team grew by 40%.",
                            "New CRM rolled out across regions.",
                        ),
                    )
                }
                weighted(1f) {
                    text("Roadmap") { fontSize = 14.sp; bold = true; color = PdfColor.Black }
                    spacer(height = 6.dp)
                    numberedList(
                        items = listOf(
                            "Launch self-serve onboarding (Q2).",
                            "Add observability dashboard (Q2).",
                            "Open second hub in Mumbai (Q3).",
                        ),
                    )
                }
            }

            // Decorative quote block with accent border
            card(
                background = PdfColor(0.97f, 0.95f, 1f),
                cornerRadius = 0.dp,
                padding = Padding.all(18.dp),
                borderEach = BorderSides(left = BorderStroke(4.dp, PdfColor(0.55f, 0.20f, 0.85f))),
            ) {
                text("“We shipped a 32-page customer-facing report in three days, with the same Kotlin code on Android and iOS.”") {
                    fontSize = 13.sp; italic = true; color = PdfColor(0.30f, 0.20f, 0.50f); lineHeight = 18.sp
                }
                spacer(height = 8.dp)
                text("— Engineering team, ConaMobile") {
                    fontSize = 10.sp; color = PdfColor(0.45f, 0.40f, 0.60f)
                }
            }

            // Footer ribbon — centred, single row
            divider(thickness = 0.5.dp, color = PdfColor(0.85f, 0.87f, 0.90f))
            row(
                verticalAlignment = VerticalAlignment.Center,
                horizontalArrangement = HorizontalArrangement.Center,
                spacing = 8.dp,
            ) {
                circle(diameter = 12.dp, fill = PdfColor(0.20f, 0.40f, 0.95f))
                text("PdfKmp · ConaMobileDev / PdfKmp") {
                    fontSize = 10.sp; color = PdfColor(0.45f, 0.50f, 0.60f)
                }
            }
        }
    }

    private data class Region(
        val name: String,
        val customers: String,
        val revenue: String,
        val yoy: String,
        val up: Boolean,
    )

    /**
     * Document-level "chrome" features grouped onto one document so they
     * can be tested together — every page carries a header, footer with
     * `Page X of Y`, and a watermark; one page demos clickable
     * hyperlinks; the last page exercises the i18n font references for
     * CJK / Arabic / Persian. Pages are chained via
     * [PageBreakStrategy.Slice] so the long body section overflows
     * naturally and the page numbering sees `totalPages > 1`.
     */
    public fun pageChrome(): PdfDocument = pdf {
        metadata {
            title = "PdfKmp – Page Chrome (header/footer/watermark/links/i18n)"
            author = "PdfKmp"
        }

        // Page 1+ — header / footer / watermark with sliced long body so
        // page numbers go beyond 1.
        page {
            spacing = 12.dp
            pageBreakStrategy = PageBreakStrategy.Slice

            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("Quarterly Report") { bold = true; fontSize = 12.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }
            footer { _ ->
                divider(thickness = 0.5.dp, color = PdfColor.LightGray, style = LineStyle.Dashed)
                text("conamobile · pdfkmp · open source") {
                    fontSize = 10.sp; color = PdfColor.Gray; italic = true
                    align = TextAlign.Center
                }
            }
            watermark {
                aligned(BoxAlignment.Center) {
                    text("DRAFT") {
                        fontSize = 120.sp; bold = true
                        color = PdfColor(0.92f, 0.92f, 0.95f)
                    }
                }
            }

            text("Headers, footers, page numbers, watermark") {
                fontSize = 22.sp; bold = true
            }
            divider()
            text("Every physical page below carries the header (top), footer (bottom), and a pale 'DRAFT' watermark behind the body. The footer interpolates the current page number against the total — accurate even though the body slices across multiple pages.")
            for (i in 1..30) {
                text("Paragraph $i — $LOREM$LOREM")
            }
        }

        // New page — hyperlinks. Header/footer applied per-page so this
        // page declares its own.
        page {
            spacing = 12.dp
            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("Quarterly Report") { bold = true; fontSize = 12.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }

            text("Hyperlinks") { fontSize = 22.sp; bold = true }
            divider()
            text("Tap a link below in a PDF reader that supports annotations (iOS Preview, Adobe Reader). Android's `PdfDocument` does not ship link-annotation APIs, so on Android the rectangles render with the visual styling but stay click-inert.") {
                color = PdfColor.Gray; fontSize = 11.sp
            }

            link(url = "https://github.com/conamobile/pdfkmp") {
                text("github.com/conamobile/pdfkmp") {
                    color = PdfColor.Blue; underline = true
                }
            }
            link(url = "https://kotlinlang.org") {
                text("kotlinlang.org") { color = PdfColor.Blue; underline = true }
            }
            link(url = "mailto:hello@example.com") {
                text("hello@example.com") { color = PdfColor.Blue; underline = true }
            }
            link(url = "https://www.anthropic.com") {
                card(
                    background = PdfColor(0.95f, 0.97f, 1f),
                    cornerRadius = 8.dp,
                    border = BorderStroke(1.dp, PdfColor.Blue),
                ) {
                    text("Click this whole card →") { color = PdfColor.Blue; bold = true }
                    text("anthropic.com") {
                        color = PdfColor.Blue; underline = true; fontSize = 11.sp
                    }
                }
            }
        }

        // i18n font references — one demo line per script.
        page {
            spacing = 12.dp
            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("Quarterly Report") { bold = true; fontSize = 12.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }

            text("Internationalisation fonts") { fontSize = 22.sp; bold = true }
            divider()
            text("PdfKmp ships Inter for Latin text. CJK / Arabic / Persian scripts route through `PdfFont.System*` references that resolve to whichever font ships on the running platform.") {
                fontSize = 11.sp; color = PdfColor.Gray
            }

            text("Latin (default Inter):") { bold = true }
            text("The quick brown fox jumps over the lazy dog. 1234567890.")

            text("CJK (PdfFont.SystemCJK):") { bold = true }
            text("漢字、ひらがな、カタカナ — 中文 / 日本語 / 한국어") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemCJK
                fontSize = 18.sp
            }
            text("永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭。") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemCJK
            }

            text("Arabic (PdfFont.SystemArabic):") { bold = true }
            text("مرحبًا بكم في PdfKmp — مكتبة لإنشاء ملفات PDF.") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemArabic
                fontSize = 18.sp
            }

            text("Persian (PdfFont.SystemPersian):") { bold = true }
            text("سلام دنیا — به PdfKmp خوش آمدید.") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemPersian
                fontSize = 18.sp
            }

            text("If a platform is missing the listed system font, the renderer falls back to Inter and missing glyphs render as tofu boxes. Register a TTF via `PdfFont.Custom` to fix that.") {
                fontSize = 10.sp; italic = true; color = PdfColor.Gray
            }
        }
    }

    /**
     * End-to-end showcase that touches every feature added in the v1
     * polish sprint: rich-text spans, text alignment + decorations,
     * dividers + dashed/dotted lines, per-corner radius and per-side
     * borders, gradient backgrounds, circle / ellipse primitives,
     * bulleted and numbered lists, headers + footers with `Page X of
     * Y`, watermarks, and clickable links.
     *
     * Use this as a single document to eyeball-test the renderer or
     * when authoring screenshots for documentation.
     */
    public fun showcase(): PdfDocument = pdf {
        metadata {
            title = "PdfKmp – Feature Showcase"
            author = "PdfKmp"
        }
        defaultTextStyle = TextStyle(fontSize = 12.sp, color = PdfColor.DarkGray)

        // Page 1 — typography, rich text, dividers, lists.
        page {
            spacing = 12.dp

            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("PdfKmp Showcase") { bold = true; fontSize = 11.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }
            footer { _ ->
                divider(thickness = 0.5.dp, color = PdfColor.LightGray, style = LineStyle.Dashed)
                text("conamobile · pdfkmp · open source") {
                    fontSize = 10.sp; color = PdfColor.Gray; italic = true
                    align = TextAlign.Center
                }
            }
            watermark {
                aligned(BoxAlignment.Center) {
                    text("DRAFT") {
                        fontSize = 96.sp
                        bold = true
                        color = PdfColor(0.92f, 0.92f, 0.95f)
                    }
                }
            }

            text("Typography & Rich Text") {
                fontSize = 24.sp; bold = true; color = PdfColor.Black
            }
            divider(thickness = 1.dp, color = PdfColor.Black)

            text("Centred heading with underline") {
                fontSize = 16.sp; underline = true; align = TextAlign.Center
            }

            richText {
                span("This sentence mixes ")
                span("bold") { bold = true }
                span(", ")
                span("italic") { italic = true }
                span(", and ")
                span("coloured") { color = PdfColor.Red }
                span(" runs while wrapping naturally.")
            }

            text("Strikethrough and underline") { strikethrough = true }
            text("Justified body — but Justify falls back to Start in v1.") {
                align = TextAlign.Justify
            }

            text("Bulleted list:") { bold = true }
            bulletList(
                items = listOf(
                    "First item with regular weight body text.",
                    "Second item demonstrates wrapped continuation lines that line up under the first text line.",
                    "Third item closes the list.",
                ),
            )

            text("Numbered steps:") { bold = true }
            numberedList(
                items = listOf("Author the DSL.", "Render to a driver.", "Ship the bytes."),
            )
        }

        // Page 2 — shapes, gradients, per-corner / per-side decoration.
        page {
            spacing = 12.dp
            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("PdfKmp Showcase") { bold = true; fontSize = 11.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }

            text("Shapes, Gradients, Borders") {
                fontSize = 24.sp; bold = true; color = PdfColor.Black
            }
            divider(thickness = 1.dp, color = PdfColor.Black)

            row(spacing = 16.dp, verticalAlignment = VerticalAlignment.Center) {
                circle(diameter = 60.dp, fill = PdfColor.Blue)
                circle(
                    diameter = 60.dp,
                    fillPaint = PdfPaint.radialGradient(
                        from = PdfColor.White, to = PdfColor.Red,
                        centerX = 30f, centerY = 30f, radius = 30f,
                    ),
                )
                ellipse(width = 100.dp, height = 60.dp, fill = PdfColor.Green)
                circle(diameter = 60.dp, strokeColor = PdfColor.Black, strokeWidth = 2.dp)
            }

            text("Linear-gradient banner with per-corner radius:") { bold = true }
            box(
                width = 400.dp,
                height = 80.dp,
                cornerRadiusEach = CornerRadius.top(20.dp),
                backgroundPaint = PdfPaint.linearGradient(
                    from = PdfColor.Blue, to = PdfColor.Red,
                    endX = 400f, endY = 0f,
                ),
            ) {
                aligned(BoxAlignment.Center) {
                    text("Top-rounded gradient tab") {
                        color = PdfColor.White; bold = true; fontSize = 18.sp
                    }
                }
            }

            text("Card with only a bottom + left border:") { bold = true }
            card(
                background = PdfColor.White,
                cornerRadius = 0.dp,
                padding = Padding.all(12.dp),
                borderEach = BorderSides(
                    bottom = BorderStroke(2.dp, PdfColor.Blue),
                    left = BorderStroke(2.dp, PdfColor.Blue),
                ),
            ) {
                text("Material-style accent quote — borders run only along the sides you ask for.")
            }

            text("Dashed and dotted dividers:") { bold = true }
            divider(thickness = 1.dp, style = LineStyle.Dashed, color = PdfColor.Gray)
            divider(thickness = 1.5.dp, style = LineStyle.Dotted, color = PdfColor.DarkGray)
        }

        // Page 3 — links and i18n.
        page {
            spacing = 12.dp
            header { ctx ->
                row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                    text("PdfKmp Showcase") { bold = true; fontSize = 11.sp }
                    text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                        fontSize = 11.sp; color = PdfColor.Gray
                    }
                }
                divider(thickness = 0.5.dp, color = PdfColor.LightGray)
            }

            text("Hyperlinks & i18n") {
                fontSize = 24.sp; bold = true; color = PdfColor.Black
            }
            divider(thickness = 1.dp, color = PdfColor.Black)

            text("Click a link below — clickable on iOS; styled-only on Android.") {
                color = PdfColor.Gray
            }
            link(url = "https://github.com/conamobile/pdfkmp") {
                text("github.com/conamobile/pdfkmp") {
                    color = PdfColor.Blue
                    underline = true
                }
            }
            link(url = "https://www.anthropic.com") {
                text("anthropic.com") {
                    color = PdfColor.Blue
                    underline = true
                }
            }

            spacer(height = 12.dp)

            text("Cross-platform i18n font references:") { bold = true }
            text("漢字 — Chinese / Japanese (SystemCJK)") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemCJK
                fontSize = 18.sp
            }
            text("مرحبا — Arabic (SystemArabic)") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemArabic
                fontSize = 18.sp
            }
            text("سلام دنیا — Persian (SystemPersian)") {
                font = com.conamobile.pdfkmp.style.PdfFont.SystemPersian
                fontSize = 18.sp
            }
            text("If your platform is missing the listed system font, register a TTF via PdfFont.Custom.") {
                fontSize = 10.sp; italic = true; color = PdfColor.Gray
            }
        }
    }

    private const val LOREM: String =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do " +
            "eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim " +
            "ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut " +
            "aliquip ex ea commodo consequat. "
}
