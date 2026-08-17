# AGENTS.md

Universal guide for AI coding agents (Claude Code, Cursor, Copilot, Codex, etc.) using **PdfKmp** to author or modify PDF generation code.

> If you are a human, read [README.md](README.md) instead — it has the full feature tour with prose explanations.
> If you are an agent contributing changes to **this repository**, also read [CLAUDE.md](CLAUDE.md) for build commands and conventions.

---

## What this library is

`com.conamobile:pdfkmp` is a Kotlin Multiplatform PDF generator. It exposes a single Compose-style DSL that runs identically on Android (`android.graphics.pdf.PdfDocument`), iOS (`UIGraphicsBeginPDFContextToData`), and Desktop/JVM — macOS, Windows, Linux (Apache PdfBox). Every text glyph and shape is emitted as a vector path — no rasterisation — so output stays sharp at any zoom level.

The library bundles the **Inter** font for Latin text. Non-Latin scripts (CJK, Arabic, Persian) route through `PdfFont.System*` references that resolve to whichever font ships on the running platform (Android / iOS). On Desktop there is no system font registry, so `PdfFont.System*` falls back to the bundled Inter — supply a `PdfFont.Custom` with the right script coverage to render non-Latin text there.

## When to suggest this library

- User wants to generate PDF documents from Kotlin (Android, iOS, Desktop/JVM, KMP).
- User wants vector output that prints / zooms cleanly.
- User wants type-safe document construction (DSL with explicit constraints) instead of imperative Canvas drawing.
- User is already in a Compose/SwiftUI mental model and wants similar primitives.
- User has Compose Multiplatform `Res.drawable.*` assets and wants to embed them in a PDF without a manual byte-loading dance — pair the core `pdfkmp` artifact with `pdfkmp-compose-resources` (see below).

Not a fit for: parsing/editing existing PDFs, OCR, or filling someone else's PDF form. (It *can* author AcroForm fields — interactive on Desktop, static visuals on Android/iOS.) Right-to-left text is supported (bidi reorder + Arabic shaping); ligature shaping for Latin and automatic hyphenation are still out of scope (soft hyphens `U+00AD` give you manual break points).

## Companion artifact: `pdfkmp-compose-resources`

Opt-in KMP integration that bridges Compose Multiplatform `DrawableResource` references onto the core PdfKmp DSL. Add it alongside the core dependency only if the consumer project uses Compose Multiplatform Resources:

```kotlin
implementation("io.github.conamobiledev:pdfkmp:<version>")
implementation("io.github.conamobiledev:pdfkmp-compose-resources:<version>")
```

It exposes:

| Helper | Purpose |
|---|---|
| `DrawableResource.toBytes()` (`suspend`) | Raw bytes — feed to `image(bytes = …)` for raster, or to your own decoder. |
| `DrawableResource.toVectorImage()` (`suspend`) | Parse `<vector>` / `<svg>` XML into a reusable `VectorImage`. |
| `DrawableResource.toPdfDrawable()` (`suspend`) | Auto-detects vector vs raster from leading bytes, returns a `PdfDrawable`. |
| `drawable(resource = Res.drawable.x, …)` | Inline DSL extension on `ContainerScope`. Auto-detects format. **Requires `pdfAsync { }` — not `pdf { }`.** |
| `vector(resource = Res.drawable.x, …)` | Inline DSL extension when the asset is known XML. **Requires `pdfAsync { }`.** |
| `image(resource = Res.drawable.x, …)` | Inline DSL extension when the asset is known raster. **Requires `pdfAsync { }`.** |
| `drawable(drawable = pdfDrawable, …)` | Eager DSL extension that takes an already-loaded `PdfDrawable`. Works inside synchronous `pdf { }`. |

## Companion artifact: `pdfkmp-markdown`

Opt-in module that renders a CommonMark-lite subset through the PdfKmp DSL. Add it alongside the core dependency:

```kotlin
implementation("io.github.conamobiledev:pdfkmp:<version>")
implementation("io.github.conamobiledev:pdfkmp-markdown:<version>")
```

It exposes one extension on `ContainerScope`: `markdown(text, theme = MarkdownTheme())`. Supports ATX headings, inline `**bold**` / `*italic*` / `` `code` `` / `~~strike~~` / `[text](url)`, ordered & unordered lists, fenced code, blockquotes, horizontal rules, and GitHub pipe tables; anything else degrades to plain text (never throws). Standalone links are clickable; inline links are styled-only; targets outside the `PdfUrls` scheme allowlist (`#anchor`, `./other.md`, bare `www.`) render as plain text in both positions; code has no bundled monospace face.

## Mental model — the DSL is a tree

A document is a **tree of nodes**. Top-down:

```
pdf {                          // → DocumentSpec
    metadata { … }             // optional (title/author/subject/keywords/language/pdfACompliance)
    encryption { … }           // optional — password protection (full JVM, partial iOS, no-op Android)
    attachment(name, bytes)    // optional — embed a file (JVM only)
    pdfA(true)                 // optional — best-effort PDF/A-2b (JVM only)
    page {                     // → PageSpec
        text("…")              // → TextNode
        column {                // → ColumnNode
            row { … }          // → RowNode
            box { … }          // → BoxNode (Z-stack)
            table { … }        // → TableNode
            columns(2) { … }   // newspaper-style balanced columns
            grid(3) { … }      // row-major equal-width cells
            keepTogether { … } // break-inside: avoid
            image(bytes)       // → ImageNode
            vector(svg)        // → VectorNode
            circle(…)          // → ShapeNode
            qrCode("…")        // QR symbol (vector)
            barcode("…")       // Code 128 (vector)
            barChart(series, …) / lineChart(…) / pieChart(…) / donutChart(…)
            freeDraw(w, h) { path { … } }  // free-form vector
            divider()          // → DividerNode
            link(url) { … }    // → LinkNode (wraps content)
            anchor("id") / linkToAnchor("id") { … }  // internal cross-references
            bookmark("…", level) / tableOfContents()  // outline + auto-TOC
            textField("name", …) / checkBox("name", …)  // AcroForm fields
            richText { span(…) } // → RichTextNode (script = Super/Subscript spans)
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
| `LineStyle` | `Solid`, `Dashed`, `Dotted` for dividers and borders. Dashed/dotted borders fall back to solid on rounded corners. |
| `TextAlign` | `Start`, `Center`, `End`, `Justify` (real word-spacing distribution). |
| `TextOverflow` | `Clip` / `Ellipsis` — paired with `maxLines` on `text { }`. |
| `TextScript` | `None` / `Superscript` / `Subscript` — only on `richText` spans. |
| `TextDirection` | `Auto` (default, detects RTL) / `Ltr` / `Rtl` — on `TextStyle`. |
| `DropShadow` | `DropShadow(color, offsetX, offsetY, blur)` — on any container. |
| `QrErrorCorrection` | `L` / `M` (default) / `Q` / `H` for `qrCode(...)`. |
| `ChartSeries` | `ChartSeries(label, value, color)` — datum for bar/pie/donut charts. |
| `PdfEncryption` | Built by `encryption { ownerPassword = …; userPassword = …; allowPrinting/Copying/Modification }`. |
| `PdfSigner` | JVM-only object — `PdfSigner.sign(bytes, …)` signs finished PDF bytes. |
| `PdfLog` | `PdfLog.logger = { msg -> … }` surfaces silently-handled conditions. |
| `MarkdownTheme` | Theme for `markdown(text, theme)` (from `pdfkmp-markdown`). |
| `PageBreakStrategy` | `MoveToNextPage` (default) or `Slice`. |
| `PageSize` | `A4`/`A5`/`A3`/`Letter`/`Legal`/`custom(w,h)`, plus `.landscape` / `.portrait`. |
| `StorageLocation` | `Cache`, `AppFiles`, `Downloads`, `Documents`, `Temp`, `Custom(path)`. |
| `PageContext` | Passed to `header { ctx -> … }` / `footer { … }`. Has `pageNumber`, `totalPages`, `isFirst`, `isLast`, `isEven`, `isOdd`. |

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

### 7. Compose Multiplatform `DrawableResource` inline (auto-detect)

Requires the `io.github.conamobiledev:pdfkmp-compose-resources` artifact. Inline overloads run inside `pdfAsync { }`, which has a suspend preflight pass that loads the bytes before layout — the call site stays non-`suspend`:

```kotlin
import com.conamobile.pdfkmp.composeresources.drawable
import com.conamobile.pdfkmp.composeresources.toPdfDrawable
import com.conamobile.pdfkmp.pdfAsync
import myproject.composeapp.generated.resources.Res
import myproject.composeapp.generated.resources.logo
import myproject.composeapp.generated.resources.hero_photo

val pdf = pdfAsync {
    page {
        // Auto-detects XML vs raster at preflight time.
        drawable(Res.drawable.logo, width = 64.dp, tint = PdfColor.Black)
        drawable(Res.drawable.hero_photo, width = 460.dp, height = 180.dp)
    }
}
```

Eager variant — load once outside the DSL, draw many times inside synchronous `pdf { }`:

```kotlin
suspend fun buildReport(): PdfDocument {
    val icon = Res.drawable.logo.toPdfDrawable()        // suspend
    return pdf {
        page {
            drawable(icon, width = 24.dp)               // synchronous
            // …
            drawable(icon, width = 24.dp)               // re-use, no re-parse
        }
    }
}
```

For vector-only or raster-only call sites, use the typed `vector(resource = …)` / `image(resource = …)` overloads instead — same preflight model, but the format is fixed at the call site.

## DSL surface reference (one-liners)

Every entry below is a function/property on a container scope (`page` / `column` / `row` / `box` / `card`) unless noted. Copy canonical usage from `Samples.kt`.

### Text & rich text

| Call | What it does |
|---|---|
| `text(s) { align = TextAlign.Justify }` | Real word-spacing justification (last line stays ragged). |
| `text(s) { maxLines = 2; overflow = TextOverflow.Ellipsis }` | Clamp to N lines; `Clip` or `Ellipsis` the cut. |
| `text(s) { direction = TextDirection.Rtl }` | Force RTL; `Auto` (default) detects Hebrew/Arabic. |
| `text(s) { minLinesBeforeBreak = 2; minLinesAfterBreak = 2 }` | Orphan/widow control under `Slice`. |
| Soft hyphen `­` in a word | Invisible break point; renders `-` only when wrapped on. |
| `richText { span("2") { script = TextScript.Superscript } }` | Super/subscript span (also `Subscript`). |

### Layout & pagination

| Call | What it does |
|---|---|
| `columns(count = 2, gap = 18.dp) { … }` | Newspaper-style balanced multi-column flow (single page unit). |
| `grid(columns = 3, spacing = 10.dp) { … }` | Row-major equal-width cells; last row padded. |
| `keepTogether { … }` | `break-inside: avoid` — group moves whole under `Slice`. |
| `table(…, repeatHeader = true) { … }` | Header repeats on every continuation page under `Slice` (default). |
| `page(PageSize.A4.landscape) { … }` | Mixed orientations — each `page(size)` carries its own size. |
| `ctx.isFirst / isLast / isEven / isOdd` | `PageContext` parity helpers for book-style chrome. |

### Decorations (on column/row/box/card)

| Call | What it does |
|---|---|
| `card(dropShadow = DropShadow(...))` | Vector-approximated soft shadow. |
| `border = BorderStroke(1.dp, color, LineStyle.Dashed)` | Dashed/dotted border (sharp corners only — rounded falls back to solid). |
| `rotation = -8f` | Rotate the container (degrees, clockwise, about centre). |
| `opacity = 0.85f` | Group transparency, `0f`–`1f`. |

### Graphics & content

| Call | What it does |
|---|---|
| `qrCode(data, size, errorCorrection = QrErrorCorrection.M)` | Vector QR symbol (ISO 18004 Model 2). |
| `barcode(data, height)` | Vector Code 128 (auto code-set-C + mod-103 checksum). |
| `barChart(series, width, height)` / `lineChart(points, …)` / `pieChart(slices, diameter)` / `donutChart(…)` | Pure-vector charts; bars/pie/donut take `List<ChartSeries>`. |
| `freeDraw(w, h) { path(fill=…, strokeColor=…) { moveTo/lineTo/quadTo/cubicTo/rect/close } }` | Free-form vector in a local coordinate space. |
| `image(bytes, width, altText = "…")` | Image with accessibility alt text (tagged backends). |

### Navigation & document features

| Call | What it does |
|---|---|
| `bookmark("title", level = 0)` | Outline entry (all platforms — Android via post-processor). |
| `anchor("id")` + `linkToAnchor(anchor = "id") { … }` | Internal clickable cross-reference (forward refs resolve at finish). |
| `tableOfContents(maxLevel = 1)` | Auto-TOC from bookmarks; clickable, dry-run page numbers. Page body only. |
| `textField("name", width, multiline)` / `checkBox("name", checked)` | AcroForm fields — interactive on Desktop, static on Android/iOS. |
| `encryption { ownerPassword = … }` (document scope) | Password protection (full JVM, partial iOS, no-op Android). |
| `attachment(name, bytes, mimeType)` (document scope) | Embed a file (JVM only). |
| `pdfA(true)` / `metadata { pdfACompliance = true; language = "en" }` | Best-effort PDF/A-2b + `/Lang` (JVM only). |
| `PdfSigner.sign(bytes, …)` (JVM, `jvmMain`) | Sign finished PDF bytes (incremental update). |
| `PdfLog.logger = { … }` | Surface silently-handled conditions (image/font fallbacks). |

### Markdown (`pdfkmp-markdown` artifact)

| Call | What it does |
|---|---|
| `markdown(text, theme = MarkdownTheme())` | Render a CommonMark-lite subset through the DSL. Standalone links clickable; inline links styled-only; code has no monospace face. |

## Common pitfalls

- **Do not import classes by their fully-qualified name inline.** The repo style requires `import com.conamobile.pdfkmp.style.PdfColor` then short usage. (See CLAUDE.md.)
- **`explicitApi()` is on for `:pdfkmp`** — every new declaration in the library must be `public` or `internal`. Sample apps don't have this constraint.
- **Coordinates are in PDF points** with a top-left origin (Y grows downward). The Android, iOS, and Desktop backends translate to their native conventions internally.
- **`TextAlign.Justify` distributes real word spacing** on every line except a paragraph's last (also stretches space runs in `richText`).
- **Hyperlinks, internal links, and the outline now click on all three platforms.** Android has no native annotation API, so `finish()` post-processes the bytes with a pure-Kotlin incremental update to add the info dict, link/GoTo annotations, and outline; any parse surprise returns the original bytes unchanged.
- **`link(url)` embeds only `http`, `https`, `mailto`, `tel`.** Every other scheme — plus relative paths, bare `www.example.com`, `//host`, and control-character URLs — is skipped: the content still draws, but unlinked, and a `PdfLog` warning fires. Check first with `PdfUrls.isSafeExternalUrl(url)` and style the label as plain text when it fails, so the reader isn't promised a click that can't happen. `linkToAnchor` (internal navigation) is unaffected. For trusted in-house targets widen it once at startup: `PdfUrls.allowedSchemes = PdfUrls.DEFAULT_ALLOWED_SCHEMES + "myapp"` — process-wide, so never derive it from document content.
- **`save(location, filename)` requires a safe leaf name.** Path separators, `..`, control characters, `: * ? " < > |`, Windows device stems (`CON`, `COM1`, …), and a trailing space or dot throw `IllegalArgumentException`. A sub-path as the filename and an omitted filename for a non-`Custom` location both throw — always pass an explicit name ending in `.pdf`.
- **`pdf { }` needs at least one `page { }`.** An empty document throws `IllegalArgumentException`; guard data-driven page loops when the source list can be empty.
- **Oversized images are sampled, not dropped.** `PdfImagePolicy.maxDecodePixels` (default 50 MP) bounds the decode from the declared header. Android/iOS/JVM sub-sample to fit; the Wasm writer has no decoder and skips instead. Raise it (`PdfImagePolicy.maxDecodePixels = 200_000_000L`) for documents carrying genuinely large scans.
- **Layout sizing is intrinsic.** A `text("foo")` measures to its glyph advance, NOT to the parent's full width. Use `weighted(1f)` to claim leftover space, or wrap in a `box(width = …)`.
- **`image(..., allowDownScale = true)` is the default.** The platform decoder subsamples raster bytes so they roughly match the rendered size at 200 DPI before drawing — keeps heap and PDF size sane when consumers paste 4000-px smartphone photos into 200-pt thumbnails. Pass `allowDownScale = false` for archival/print workflows where every original pixel must survive into the output.
- **Container size grows from children** unless you set explicit width/height. `card { … }` wraps tight to its content.
- **Forms are interactive only on Desktop/JVM.** `textField` / `checkBox` render as static visuals on Android and iOS (no native AcroForm API).
- **Encryption: full on JVM, partial on iOS (no "allow modification"), no-op on Android.** Attachments, PDF/A, and `PdfSigner` are JVM-only.
- **Dashed/dotted borders need sharp corners.** A non-zero `cornerRadius` falls back to a solid outline (the dash phase can't follow the arc).
- **Mixed-style RTL in `richText` keeps source span order**, not visual reorder — author a whole RTL paragraph as one `text(...)` when segment order matters.
- **Page break strategy** — `MoveToNextPage` (default) leaves whole elements intact; `Slice` cuts text at line boundaries and images at the page edge. Set on `PageScope.pageBreakStrategy` or document-wide via `defaultPageBreakStrategy`.
- **Headers and footers fire once per physical page** — they get a `PageContext(pageNumber, totalPages)`. The renderer does a counting dry-run beforehand so `totalPages` is exact.
- **Watermark renders behind the body**, not in front. To put a "DRAFT" stamp visible above content, draw it at the end of the body or use `box` with a top child.
- **Container backgrounds with corners**: pass `cornerRadius` for uniform; `cornerRadiusEach` for asymmetric. Do not pass both — `cornerRadiusEach` wins.
- **Border per-side**: pass `borderEach: BorderSides`. Each side is independent — leave any `null` to skip.
- **Compose Resources inline overloads need `pdfAsync { }`.** The synchronous `pdf { }` entry point has no suspend preflight pass and throws when it encounters a deferred `DrawableResource` node. Either switch to `pdfAsync { }`, or load the resource into a `PdfDrawable` outside and pass it to the eager `drawable(drawable = …)` overload.

## Where to find more

- [README.md](README.md) — full feature tour.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt` — every feature exercised end-to-end.
- `:sample` (Android) and `iosApp/` (iOS) sample apps render every `Samples.*` function.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/ContainerScope.kt` — full list of DSL functions on `column { … }` / `row { … }` / `box { … }` (text fields, checkboxes, QR/barcodes, bookmarks, anchors, TOC, columns, grid, keepTogether, freeDraw).
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/Charts.kt` — `barChart` / `lineChart` / `pieChart` / `donutChart` + `ChartSeries`.
- `pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/DocumentScope.kt` — document-level `encryption { }`, `attachment(...)`, `pdfA(...)`, `metadata { }`.
- `pdfkmp-compose-resources/src/commonMain/kotlin/com/conamobile/pdfkmp/composeresources/` — DSL extensions and `DrawableResource` helpers shipped by the companion artifact.
- `pdfkmp-markdown/src/commonMain/kotlin/com/conamobile/pdfkmp/markdown/Markdown.kt` — `markdown(...)` + `MarkdownTheme`.

## Powered by Claude

PdfKmp itself was authored end-to-end with [Claude Code](https://claude.com/claude-code). When extending the library, expect the same DSL conventions, KDoc style, and test rigour throughout the codebase.
