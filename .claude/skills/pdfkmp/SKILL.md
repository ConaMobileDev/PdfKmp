---
name: pdfkmp
description: "Use this skill when the user asks to generate a PDF document from Kotlin (Android, iOS, or Kotlin Multiplatform) using the PdfKmp library. Triggers: 'generate a PDF', 'export to PDF', 'create a PDF report', 'invoice PDF', 'PDF receipt', 'multi-page PDF', any time the user wants to author or modify code that calls into `com.conamobile:pdfkmp` (the `pdf { … }` DSL, `PdfDocument`, `PdfColor`, `TextStyle`, `column`/`row`/`box`/`card`/`table`, headers/footers/watermarks, hyperlinks, or i18n fonts). Covers vector PDF generation, the full DSL surface, common pitfalls, and the path to test/build (`./gradlew :pdfkmp:iosSimulatorArm64Test`)."
---

# PdfKmp — author PDF documents from Kotlin

PdfKmp is a Kotlin Multiplatform PDF generator. Authors compose a tree of layout nodes via a Compose-style DSL; the library renders vector text and shapes to a real PDF on Android and iOS.

**When in doubt about the API surface**, read `AGENTS.md` at repo root or the full `Samples.kt` in `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt` — every feature is exercised there.

## Mental model

```
pdf {                                  // → PdfDocument
    metadata { title = "…"; language = "en"; pdfACompliance = true }
    encryption { ownerPassword = "…" } // optional — full JVM, partial iOS, no-op Android
    attachment("file.xml", bytes)      // optional — embed a file (JVM only)
    defaultTextStyle = TextStyle(…)    // optional document-wide defaults
    defaultPagePadding = Padding.all(40.dp)

    page(size = PageSize.A4) {          // one logical page = one or more physical pages
        spacing = 12.dp
        pageBreakStrategy = PageBreakStrategy.Slice  // or MoveToNextPage (default)
        header { ctx -> … }            // ctx.pageNumber/totalPages/isFirst/isLast/isEven/isOdd
        footer { ctx -> … }
        watermark { aligned(BoxAlignment.Center) { … } }

        text("…") { align = TextAlign.Justify; maxLines = 2; overflow = TextOverflow.Ellipsis }
        column { … } / row { … } / box { … } / card { … }   // + dropShadow / rotation / opacity
        columns(count = 2) { … } / grid(columns = 3) { … } / keepTogether { … }
        table(columns = …, repeatHeader = true) { header { … }; row { cell(…) } }
        image(bytes, width = …, contentScale = ContentScale.Fit, altText = "…")
        vector(xml = svgString, width = …)
        circle(diameter = …, fill = …)
        ellipse(width = …, height = …)
        qrCode("…") / barcode("…")
        barChart(series, width, height) / lineChart(points, …) / pieChart(slices, diameter) / donutChart(…)
        freeDraw(w, h) { path(fill = …) { moveTo(…); lineTo(…); close() } }
        divider(thickness = …, style = LineStyle.Dashed)
        bulletList(items = listOf(…))
        numberedList(items = listOf(…), startAt = 1)
        link(url = "https://…") { text("…") { color = PdfColor.Blue; underline = true } }
        anchor("id") / linkToAnchor("id") { … }     // internal cross-references
        bookmark("…", level) / tableOfContents()    // outline + auto-TOC
        textField("name", width = …) / checkBox("name", checked = …)  // AcroForm
        richText { span("normal "); span("2") { script = TextScript.Superscript } }
    }
}
```

`pdf { … }` returns a `PdfDocument`. Get bytes via `.toByteArray()`. Save via `.save(StorageLocation.Cache, "report.pdf")`.

## Imports cheat sheet

```kotlin
import com.conamobile.pdfkmp.pdf
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
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.CornerRadius
import com.conamobile.pdfkmp.style.DropShadow
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextOverflow
import com.conamobile.pdfkmp.style.TextScript
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.conamobile.pdfkmp.vector.VectorImage

// Charts (extension DSL):
import com.conamobile.pdfkmp.dsl.ChartSeries
import com.conamobile.pdfkmp.dsl.barChart
import com.conamobile.pdfkmp.dsl.lineChart
import com.conamobile.pdfkmp.dsl.pieChart
import com.conamobile.pdfkmp.dsl.donutChart
// QR error-correction level:
import com.conamobile.pdfkmp.barcode.QrErrorCorrection
// Diagnostics, JVM signing, Markdown module:
import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.sign.PdfSigner          // jvmMain only
import com.conamobile.pdfkmp.markdown.markdown        // pdfkmp-markdown artifact
import com.conamobile.pdfkmp.markdown.MarkdownTheme
```

Always import — never use fully-qualified inline names. The library has `explicitApi()` on, so any new declaration in `:pdfkmp` itself must be `public` or `internal`.

## Recipes

### Hello world

```kotlin
val doc = pdf {
    metadata { title = "Hello" }
    page {
        text("Hello, world!") {
            fontSize = 24.sp; bold = true; color = PdfColor.Blue
        }
    }
}
val bytes = doc.toByteArray()
```

### Invoice (table + totals + footer)

```kotlin
data class LineItem(val name: String, val qty: Int, val price: String, val total: String)

val items = listOf(
    LineItem("Service A", 1, "$100", "$100"),
    LineItem("Service B", 2, "$50",  "$100"),
)

pdf {
    metadata { title = "Invoice #1042" }
    page {
        spacing = 16.dp

        // Header section
        row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
            text("INVOICE") { fontSize = 28.sp; bold = true }
            column(horizontalAlignment = HorizontalAlignment.End) {
                text("#1042") { bold = true }
                text("2026-04-30") { color = PdfColor.Gray; fontSize = 11.sp }
            }
        }
        divider()

        // Line items
        table(
            columns = listOf(
                TableColumn.Weight(3f),
                TableColumn.Fixed(50.dp),
                TableColumn.Fixed(80.dp),
                TableColumn.Fixed(80.dp),
            ),
            border = TableBorder(color = PdfColor.LightGray, width = 0.5.dp),
            cellPadding = Padding.symmetric(horizontal = 10.dp, vertical = 8.dp),
        ) {
            header(background = PdfColor.fromRgb(0xF5F5F5)) {
                cell("Item") { bold = true }
                cell("Qty",   bold = true, horizontalAlignment = HorizontalAlignment.End)
                cell("Price", bold = true, horizontalAlignment = HorizontalAlignment.End)
                cell("Total", bold = true, horizontalAlignment = HorizontalAlignment.End)
            }
            items.forEach { item ->
                row {
                    cell(item.name)
                    cell(item.qty.toString(), horizontalAlignment = HorizontalAlignment.End)
                    cell(item.price,          horizontalAlignment = HorizontalAlignment.End)
                    cell(item.total,          horizontalAlignment = HorizontalAlignment.End)
                }
            }
        }

        // Totals box pinned to the right
        row(horizontalArrangement = HorizontalArrangement.End) {
            card(
                background = PdfColor.fromRgb(0xF5F5F5),
                cornerRadius = 8.dp,
                padding = Padding.all(12.dp),
            ) {
                row(spacing = 24.dp) {
                    text("TOTAL") { bold = true }
                    text("$200")  { bold = true; color = PdfColor.Blue }
                }
            }
        }

        // Footer note
        spacer(height = 24.dp)
        text("Thank you for your business.") {
            fontSize = 11.sp; color = PdfColor.Gray; align = TextAlign.Center
        }
    }
}
```

### Multi-page report (header + footer + slicing)

```kotlin
pdf {
    metadata { title = "Annual Report" }
    page {
        pageBreakStrategy = PageBreakStrategy.Slice
        header { ctx ->
            row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                text("Annual Report") { bold = true; fontSize = 12.sp }
                text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                    fontSize = 11.sp; color = PdfColor.Gray
                }
            }
            divider(thickness = 0.5.dp, color = PdfColor.LightGray)
        }
        footer { _ ->
            text("Confidential") {
                fontSize = 10.sp; color = PdfColor.Gray; align = TextAlign.Center
            }
        }
        text("Executive Summary") { fontSize = 22.sp; bold = true }
        // body — overflows naturally onto new physical pages
        bodyParagraphs.forEach { text(it) }
    }
}
```

### Hero with gradient overlay + image

```kotlin
box(width = 480.dp, height = 200.dp, cornerRadius = 16.dp) {
    image(bytes = heroBytes, width = 480.dp, height = 200.dp, contentScale = ContentScale.Crop)
    box(width = 480.dp, height = 200.dp,
        backgroundPaint = PdfPaint.linearGradient(
            from = PdfColor(0f, 0f, 0f, 0f),
            to   = PdfColor(0f, 0f, 0f, 0.7f),
            endX = 0f, endY = 200f,
        ),
    ) {}
    aligned(BoxAlignment.BottomStart) {
        column(padding = Padding.all(20.dp)) {
            text("Title") { fontSize = 32.sp; bold = true; color = PdfColor.White }
        }
    }
}
```

### Save to disk (cross-platform)

```kotlin
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save

val saved = doc.save(StorageLocation.Downloads, filename = "report.pdf")
println(saved.path)
```

`StorageLocation` options: `Cache`, `AppFiles`, `AppExternalFiles` (Android), `Downloads`, `Documents`, `Temp`, `Custom("/abs/path/dir")`.

### Non-Latin scripts

```kotlin
text("漢字 中文 日本語") { font = PdfFont.SystemCJK; fontSize = 18.sp }
text("مرحبًا")        { font = PdfFont.SystemArabic; fontSize = 18.sp }
text("سلام دنیا")      { font = PdfFont.SystemPersian; fontSize = 18.sp }

// Or register a guaranteed font:
val noto = PdfFont.Custom("NotoCJK", bytesFromAssets)
pdf {
    registerFont(noto)
    page { text("永和九年") { font = noto } }
}
```

## Extended API reference

These are the newer DSL entries. Verify exact signatures in `ContainerScope.kt` / `Charts.kt` / `DocumentScope.kt`; copy canonical usage from `Samples.kt`.

### Advanced text

```kotlin
text(long) { align = TextAlign.Justify }                            // real word-spacing
text(long) { maxLines = 2; overflow = TextOverflow.Ellipsis }       // clamp + ellipsize (or Clip)
text(long) { minLinesBeforeBreak = 2; minLinesAfterBreak = 2 }      // orphan/widow under Slice
text("שלום עולם") { fontSize = 14.sp }                              // direction = Auto detects RTL
text("forced") { direction = TextDirection.Rtl }
richText { span("a"); span("2") { script = TextScript.Superscript } } // super/subscript spans
// Soft hyphen U+00AD inside a word = invisible break point.
```

### Layout & pagination

```kotlin
columns(count = 2, gap = 18.dp, spacing = 8.dp) { /* balanced multi-column flow */ }
grid(columns = 3, spacing = 10.dp) { /* row-major equal-width cells */ }
keepTogether { /* moves whole to next page under Slice */ }
table(columns = …, repeatHeader = true) { … }    // header repeats per page (default)
page(PageSize.A4.landscape) { … }                 // mixed orientations
header { ctx -> if (ctx.isFirst) Unit else if (ctx.isEven) … else … }  // book-style chrome
```

### Decorations (column / row / box / card)

```kotlin
card(dropShadow = DropShadow(offsetY = 4.dp, blur = 10.dp)) { … }
card(cornerRadius = 0.dp, border = BorderStroke(1.dp, PdfColor.Gray, LineStyle.Dashed)) { … } // sharp corners only
box(rotation = -8f, opacity = 0.85f) { … }
```

### Graphics

```kotlin
qrCode("https://…", size = 110.dp, errorCorrection = QrErrorCorrection.H)
barcode("INV-2026-00042", height = 50.dp)         // Code 128, caption not auto-drawn
val series = listOf(ChartSeries("Q1", 10f, PdfColor.Red), ChartSeries("Q2", 20f, PdfColor.Green))
barChart(series, width = 300.dp, height = 160.dp)
lineChart(listOf(3f, 7f, 4f, 9f), width = 300.dp, height = 120.dp, fillUnderLine = true)
pieChart(series, diameter = 160.dp); donutChart(series, diameter = 160.dp, holeRatio = 0.55f)
freeDraw(60.dp, 60.dp) { path(fill = PdfColor.Red) { moveTo(30f, 4f); lineTo(56f, 52f); lineTo(4f, 52f); close() } }
image(bytes, width = 300.dp, altText = "chart")   // accessibility alt text
```

### Navigation & document features

```kotlin
bookmark("Introduction", level = 0)               // outline (all platforms via Android post-processor)
anchor("intro"); linkToAnchor(anchor = "intro") { text("← back") { color = PdfColor.Blue; underline = true } }
tableOfContents(maxLevel = 1)                      // page body only; clickable rows w/ dry-run page numbers
textField("fullName", width = 300.dp); checkBox("agree", checked = true)  // interactive Desktop; static Android/iOS
pdf {
    metadata { language = "en"; pdfACompliance = true }   // best-effort PDF/A (JVM only)
    encryption { ownerPassword = "owner"; userPassword = "user"; allowPrinting = false } // full JVM, partial iOS, no-op Android
    attachment("factur-x.xml", xmlBytes, mimeType = "application/xml")  // JVM only
    page { /* … */ }
}
PdfLog.logger = { msg -> println("PdfKmp: $msg") }         // surface silently-handled conditions
```

### JVM digital signing (`jvmMain` only)

```kotlin
val signed = PdfSigner.sign(pdfBytes, name = "Jane Doe", reason = "Approved") { content ->
    myCmsService.signDetached(content)            // DER-encoded CMS/PKCS#7 SignedData
}
// or from a KeyStore (needs org.bouncycastle:bcpkix-jdk18on on the runtime classpath):
val signed2 = PdfSigner.sign(pdfBytes, keyStore, alias = "mykey", password = pwd.toCharArray())
```

### Markdown (`pdfkmp-markdown` artifact)

```kotlin
import com.conamobile.pdfkmp.markdown.markdown
page { markdown("# Title\n**bold** and [a link](https://example.com)\n- item", theme = MarkdownTheme()) }
// Standalone links clickable; inline links styled-only; code has no monospace face.
```

## Pitfalls (read before writing code)

1. **`TextAlign.Justify` distributes real word spacing** (last line of each paragraph stays ragged; also stretches space runs in `richText`).
2. **Hyperlinks, internal links, and the outline click on all three platforms now.** Android has no native annotation API, so `finish()` post-processes the bytes with a pure-Kotlin incremental update (info dict + link/GoTo annotations + outline); any parse surprise returns the original bytes unchanged.
3. **Coordinates are in PDF points, top-left origin.** Don't flip Y.
4. **Layout sizing is intrinsic.** Use `weighted(1f)` inside a `row`/`column` to claim leftover space.
5. **`cornerRadiusEach` wins over `cornerRadius`.** Don't set both expecting them to combine.
6. **Header / footer / watermark are per-page**, not document-wide. Either repeat them, or extract a helper.
7. **`PageContext.totalPages` is exact** — the renderer counts pages with a dry-run pass before the real render. `ctx` also has `isFirst` / `isLast` / `isEven` / `isOdd` for book-style chrome.
8. **Watermarks render BEHIND body content.** For a stamp on top, draw at end of body or use a `box` overlay.
9. **`bulletList` / `numberedList` items are plain strings.** For styled list items, build the list manually with `column { row { circle(...); weighted(1f) { text(...) } } }`, or use the `pdfkmp-markdown` module for inline-styled lists.
10. **Custom fonts are auto-collected** from any `TextStyle.font = PdfFont.Custom(...)` in the document tree. `registerFont` is only needed for fonts that no node references but should still be embedded.
11. **Forms are interactive only on Desktop/JVM.** `textField` / `checkBox` render as static visuals on Android and iOS.
12. **Encryption: full on JVM, partial on iOS (no "allow modification" flag), no-op on Android.** Attachments, PDF/A, and `PdfSigner` are JVM-only. PDF/A is best-effort, not full veraPDF conformance.
13. **Dashed / dotted borders require sharp corners** — a non-zero `cornerRadius` falls back to a solid outline.
14. **Mixed-style RTL in `richText` keeps source span order**, not visual reorder — author a whole RTL paragraph as one `text(...)` when segment order matters.
15. **`link(url)` embeds only `http`, `https`, `mailto`, `tel`.** Any other scheme — and relative paths, bare `www.example.com`, `//host`, control-character URLs — is skipped: the content draws unlinked and `PdfLog` warns. Gate on `PdfUrls.isSafeExternalUrl(url)` and fall back to plain `text(...)` so the label doesn't look clickable when it isn't. `linkToAnchor` is unaffected. Widen for trusted targets with `PdfUrls.allowedSchemes = PdfUrls.DEFAULT_ALLOWED_SCHEMES + "myapp"`.
16. **`save(location, filename)` needs a safe leaf name.** Separators, `..`, control chars, `: * ? " < > |`, Windows device stems (`CON`, `COM1`, …), trailing space/dot → `IllegalArgumentException`. A sub-path filename, or no filename on a non-`Custom` location, also throws. Always pass an explicit `"name.pdf"`.
17. **`pdf { }` must contain at least one `page { }`** — an empty document throws. Guard loops that build pages from a possibly-empty list.
18. **Oversized images are sampled down, not dropped.** `PdfImagePolicy.maxDecodePixels` (default 50 MP) bounds the decode from the declared header; Android/iOS/JVM sub-sample, the Wasm writer skips (no decoder). Raise it for legitimately huge scans: `PdfImagePolicy.maxDecodePixels = 200_000_000L`.

## Common color helpers

```kotlin
PdfColor.Red / Green / Blue / Black / White / Gray / LightGray / DarkGray
PdfColor(r, g, b, a)                      // floats in 0..1
PdfColor.fromRgb(0xFF5722)                // hex literal
```

## Verifying a change

```bash
# Common test on iOS Simulator (the canonical test surface)
./gradlew :pdfkmp:iosSimulatorArm64Test

# JVM / Desktop test surface (PdfBox backend's own gate; fast, no simulator)
./gradlew :pdfkmp:jvmTest

# No Mac? Type-check iosMain without a simulator or Apple toolchain:
./gradlew :pdfkmp:compileIosMainKotlinMetadata

# Build all platform artifacts
./gradlew :pdfkmp:assemble

# Run the Android sample on a connected device
./gradlew :sample:installDebug
```

When adding a feature, also extend `Samples.kt` and `SamplesSmokeTest.kt` so the new path is exercised end-to-end on iOS Simulator.

## Repository layout

- `:pdfkmp` — library (Android `aar` + iOS framework `PdfKmp`).
- `:sample` — Android demo (Compose).
- `iosApp/` — iOS demo (SwiftUI). Build phase runs `:pdfkmp:embedAndSignAppleFrameworkForXcode`.

## Reference files

- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt` — feature-by-feature example documents (incl. `textAdvanced`, `longTable`, `barcodes`, `designExtras`, `navigation`, `newsletter`, `pageTemplates`, `formsAndAccessibility`).
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/ContainerScope.kt` — all DSL functions available inside `column`/`row`/`box` (incl. QR/barcode, bookmarks, anchors, TOC, columns, grid, keepTogether, freeDraw, form fields).
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/Charts.kt` — chart DSL + `ChartSeries`.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/DocumentScope.kt` — document-level `encryption` / `attachment` / `pdfA` / `metadata`.
- `pdfkmp-markdown/src/commonMain/kotlin/com/conamobile/pdfkmp/markdown/Markdown.kt` — `markdown(...)` + `MarkdownTheme`.
- `AGENTS.md` (root) — universal agent guide (this skill is the Claude Code-specific version).
- `README.md` — end-user docs.
- `CLAUDE.md` — repo conventions for agents working ON this codebase (not on USING the library).
