# AGENTS.md

Universal guide for AI coding agents (Claude Code, Cursor, Copilot, Codex, etc.) using **PdfKmp** to author or modify PDF generation code.

> If you are a human, read [README.md](README.md) instead — it has the full feature tour with prose explanations.
> If you are an agent contributing changes to **this repository**, also read [CLAUDE.md](CLAUDE.md) for build commands and conventions.

---

## What this library is

`com.conamobile:pdfkmp` is a Kotlin Multiplatform PDF generator. It exposes a single Compose-style DSL that runs identically on Android (`android.graphics.pdf.PdfDocument`) and iOS (`UIGraphicsBeginPDFContextToData`). Every text glyph and shape is emitted as a vector path — no rasterisation — so output stays sharp at any zoom level.

The library bundles the **Inter** font for Latin text. Non-Latin scripts (CJK, Arabic, Persian) route through `PdfFont.System*` references that resolve to whichever font ships on the running platform.

## When to suggest this library

- User wants to generate PDF documents from Kotlin (Android, iOS, KMP).
- User wants vector output that prints / zooms cleanly.
- User wants type-safe document construction (DSL with explicit constraints) instead of imperative Canvas drawing.
- User is already in a Compose/SwiftUI mental model and wants similar primitives.

Not a fit for: parsing/editing existing PDFs, OCR, form filling, complex typography (RTL bidi, ligature shaping, hyphenation are minimal in v1).

## Mental model — the DSL is a tree

A document is a **tree of nodes**. Top-down:

```
pdf {                          // → DocumentSpec
    metadata { … }             // optional
    page {                     // → PageSpec
        text("…")              // → TextNode
        column {                // → ColumnNode
            row { … }          // → RowNode
            box { … }          // → BoxNode (Z-stack)
            table { … }        // → TableNode
            image(bytes)       // → ImageNode
            vector(svg)        // → VectorNode
            circle(…)          // → ShapeNode
            divider()          // → DividerNode
            link(url) { … }    // → LinkNode (wraps content)
            richText { span(…) } // → RichTextNode
            bulletList(…)
            numberedList(…)
        }
        header { ctx -> … }    // optional, gets PageContext
        footer { ctx -> … }    // optional
        watermark { … }        // optional
    }
}
```

The DSL is closed (sealed) — every node maps 1:1 to a `MeasuredNode` produced by the layout engine, then to draw calls on `PdfCanvas`. New node types must update all three. For 99% of consumer code, work at the DSL layer only.

## Key types to know

| Type | What it is |
|---|---|
| `pdf { … }` | Top-level builder. Returns `PdfDocument`. |
| `PdfDocument` | Built tree. Call `.toByteArray()` or `.save(StorageLocation, name)`. |
| `Dp`, `Sp` | Layout / text size units. `12.dp`, `16.sp`. |
| `PdfColor` | Color. `PdfColor.Red`, `PdfColor(0.5f, 0.5f, 0.5f)`, `PdfColor.fromRgb(0xFF5722)`. |
| `TextStyle` | Resolved text style passed to `text { … }` blocks. |
| `PdfFont` | `Default` (Inter), `System(name)`, `Custom(name, bytes)`, plus `SystemCJK`/`SystemArabic`/`SystemPersian`. |
| `Padding` | `Padding.all(16.dp)`, `Padding.symmetric(horizontal = …, vertical = …)`. |
| `BorderStroke` | `BorderStroke(1.dp, PdfColor.Gray)`. |
| `CornerRadius` | Per-corner override. `CornerRadius.top(16.dp)`, `CornerRadius.all(8.dp)`. |
| `BorderSides` | Per-side border override. |
| `PdfPaint` | `PdfPaint.Solid`, `PdfPaint.linearGradient(…)`, `PdfPaint.radialGradient(…)`. |
| `LineStyle` | `Solid`, `Dashed`, `Dotted` for dividers. |
| `TextAlign` | `Start`, `Center`, `End`, `Justify`. |
| `PageBreakStrategy` | `MoveToNextPage` (default) or `Slice`. |
| `StorageLocation` | `Cache`, `AppFiles`, `Downloads`, `Documents`, `Temp`, `Custom(path)`. |
| `PageContext` | Passed to `header { ctx -> … }` / `footer { … }`. Has `pageNumber`, `totalPages`. |

## Top 6 patterns (copy-paste templates)

### 1. Minimal document

```kotlin
val pdf = pdf {
    metadata { title = "Hello" }
    page {
        text("Hello, world!") { fontSize = 24.sp; bold = true }
    }
}
val bytes = pdf.toByteArray()
```

### 2. Multi-page report with header / footer / page numbers

```kotlin
pdf {
    metadata { title = "Q1 Report"; author = "PdfKmp" }
    page {
        pageBreakStrategy = PageBreakStrategy.Slice
        header { ctx ->
            row(horizontalArrangement = HorizontalArrangement.SpaceBetween) {
                text("Q1 Report") { bold = true; fontSize = 12.sp }
                text("Page ${ctx.pageNumber} of ${ctx.totalPages}") {
                    fontSize = 11.sp; color = PdfColor.Gray
                }
            }
            divider(thickness = 0.5.dp, color = PdfColor.LightGray)
        }
        footer { _ ->
            text("conamobile · 2026") {
                fontSize = 10.sp; color = PdfColor.Gray; align = TextAlign.Center
            }
        }
        // body
        text("Executive summary") { fontSize = 22.sp; bold = true }
        text(longSummaryString)
    }
}
```

### 3. Data-driven table (invoice / users / etc.)

```kotlin
table(
    columns = listOf(
        TableColumn.Fixed(60.dp),
        TableColumn.Weight(2f),
        TableColumn.Weight(1f),
    ),
    border = TableBorder(color = PdfColor.LightGray, width = 1.dp),
    cornerRadius = 8.dp,
) {
    header(background = PdfColor.fromRgb(0xECEFF1)) {
        cell("ID")
        cell("Item")
        cell("Total", horizontalAlignment = HorizontalAlignment.End)
    }
    items.forEachIndexed { i, item ->
        row(background = if (i % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)) {
            cell(item.id) { color = PdfColor.Gray }
            cell(item.name) { bold = true }
            cell(item.total, horizontalAlignment = HorizontalAlignment.End)
        }
    }
}
```

### 4. Hero card with gradient + image overlay

```kotlin
box(width = 460.dp, height = 180.dp, cornerRadius = 16.dp) {
    image(bytes = heroBytes, width = 460.dp, height = 180.dp, contentScale = ContentScale.Crop)
    aligned(BoxAlignment.BottomStart) {
        column(padding = Padding.all(20.dp)) {
            text("Title") { fontSize = 28.sp; bold = true; color = PdfColor.White }
            text("Subtitle") { fontSize = 14.sp; color = PdfColor.White }
        }
    }
}
```

### 5. Save to a typed location

```kotlin
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save

val saved = pdf.save(StorageLocation.Downloads, filename = "report.pdf")
println(saved.path)
```

### 6. Non-Latin script

```kotlin
text("永和九年，岁在癸丑") {
    font = PdfFont.SystemCJK
    fontSize = 18.sp
}
text("مرحبًا بكم") { font = PdfFont.SystemArabic }
text("سلام دنیا") { font = PdfFont.SystemPersian }
```

For guaranteed coverage, register a `.ttf`:

```kotlin
val noto = PdfFont.Custom("NotoCJK", bytesFromAssets)
pdf {
    registerFont(noto)
    page { text("漢字") { font = noto } }
}
```

## Common pitfalls

- **Do not import classes by their fully-qualified name inline.** The repo style requires `import com.conamobile.pdfkmp.style.PdfColor` then short usage. (See CLAUDE.md.)
- **`explicitApi()` is on for `:pdfkmp`** — every new declaration in the library must be `public` or `internal`. Sample apps don't have this constraint.
- **Coordinates are in PDF points** with a top-left origin (Y grows downward). Both Android and iOS backends translate to their native conventions internally.
- **`TextAlign.Justify` falls back to `Start` in v1** — per-word spacing isn't implemented. Use `Center` / `End` for now.
- **Hyperlinks click only on iOS.** Android's `PdfDocument` lacks annotation APIs; the rectangle is recorded but readers can't dispatch clicks. Visual styling (`color = Blue; underline = true`) conveys the link affordance.
- **Layout sizing is intrinsic.** A `text("foo")` measures to its glyph advance, NOT to the parent's full width. Use `weighted(1f)` to claim leftover space, or wrap in a `box(width = …)`.
- **Container size grows from children** unless you set explicit width/height. `card { … }` wraps tight to its content.
- **Page break strategy** — `MoveToNextPage` (default) leaves whole elements intact; `Slice` cuts text at line boundaries and images at the page edge. Set on `PageScope.pageBreakStrategy` or document-wide via `defaultPageBreakStrategy`.
- **Headers and footers fire once per physical page** — they get a `PageContext(pageNumber, totalPages)`. The renderer does a counting dry-run beforehand so `totalPages` is exact.
- **Watermark renders behind the body**, not in front. To put a "DRAFT" stamp visible above content, draw it at the end of the body or use `box` with a top child.
- **Container backgrounds with corners**: pass `cornerRadius` for uniform; `cornerRadiusEach` for asymmetric. Do not pass both — `cornerRadiusEach` wins.
- **Border per-side**: pass `borderEach: BorderSides`. Each side is independent — leave any `null` to skip.

## Where to find more

- [README.md](README.md) — full feature tour.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt` — every feature exercised end-to-end.
- `:sample` (Android) and `iosApp/` (iOS) sample apps render every `Samples.*` function.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/ContainerScope.kt` — full list of DSL functions on `column { … }` / `row { … }` / `box { … }`.

## Powered by Claude

PdfKmp itself was authored end-to-end with [Claude Code](https://claude.com/claude-code). When extending the library, expect the same DSL conventions, KDoc style, and test rigour throughout the codebase.
