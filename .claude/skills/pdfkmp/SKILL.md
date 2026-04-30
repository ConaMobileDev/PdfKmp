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
    metadata { title = "…" }
    defaultTextStyle = TextStyle(…)    // optional document-wide defaults
    defaultPagePadding = Padding.all(40.dp)

    page(size = PageSize.A4) {          // one logical page = one or more physical pages
        spacing = 12.dp
        pageBreakStrategy = PageBreakStrategy.Slice  // or MoveToNextPage (default)
        header { ctx -> … }            // ctx.pageNumber, ctx.totalPages
        footer { ctx -> … }
        watermark { aligned(BoxAlignment.Center) { … } }

        text("…") { … }
        column { … } / row { … } / box { … } / card { … }
        table(columns = …) { header { … }; row { cell(…) } }
        image(bytes, width = …, contentScale = ContentScale.Fit)
        vector(xml = svgString, width = …)
        circle(diameter = …, fill = …)
        ellipse(width = …, height = …)
        divider(thickness = …, style = LineStyle.Dashed)
        bulletList(items = listOf(…))
        numberedList(items = listOf(…), startAt = 1)
        link(url = "https://…") { text("…") { color = PdfColor.Blue; underline = true } }
        richText { span("normal "); span("bold") { bold = true } }
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
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.LineStyle
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.conamobile.pdfkmp.vector.VectorImage
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

## Pitfalls (read before writing code)

1. **`TextAlign.Justify` falls back to `Start` in v1** — per-word spacing not implemented.
2. **Android hyperlinks don't click** — `PdfDocument` doesn't expose annotations. iOS gets real annotations.
3. **Coordinates are in PDF points, top-left origin.** Don't flip Y.
4. **Layout sizing is intrinsic.** Use `weighted(1f)` inside a `row`/`column` to claim leftover space.
5. **`cornerRadiusEach` wins over `cornerRadius`.** Don't set both expecting them to combine.
6. **Header / footer / watermark are per-page**, not document-wide. Either repeat them, or extract a helper.
7. **`PageContext.totalPages` is exact** — the renderer counts pages with a dry-run pass before the real render.
8. **Watermarks render BEHIND body content.** For a stamp on top, draw at end of body or use a `box` overlay.
9. **`bulletList` / `numberedList` items are plain strings.** For styled list items, build the list manually with `column { row { circle(...); weighted(1f) { text(...) } } }`.
10. **Custom fonts are auto-collected** from any `TextStyle.font = PdfFont.Custom(...)` in the document tree. `registerFont` is only needed for fonts that no node references but should still be embedded.

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

- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt` — feature-by-feature example documents.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/ContainerScope.kt` — all DSL functions available inside `column`/`row`/`box`.
- `AGENTS.md` (root) — universal agent guide (this skill is the Claude Code-specific version).
- `README.md` — end-user docs.
- `CLAUDE.md` — repo conventions for agents working ON this codebase (not on USING the library).
