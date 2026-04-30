# PdfKmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.conamobiledev/pdfkmp.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.conamobiledev/pdfkmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/platform-Android%20%7C%20iOS-lightgrey.svg)](#installation)

> Kotlin Multiplatform PDF generator for Android and iOS — vector-first, type-safe, DSL-driven.

PdfKmp lets you build PDF documents from a Compose-style DSL that runs identically on Android and iOS. Text becomes glyph paths, shapes become path operators — every page stays sharp at any zoom level. The library ships the **Inter** font for cross-platform Latin parity and exposes opt-in references to system CJK / Arabic / Persian fonts so non-Latin scripts render natively.

> Built end-to-end with [Claude](https://claude.com/claude-code) — every file in this repository, including this README, was authored in collaboration with Anthropic's coding agent.

---

## Table of contents

- [Installation](#installation)
- [Hello world](#hello-world)
- [Document & pages](#document--pages)
- [Text](#text)
- [Rich text (multi-style spans)](#rich-text-multi-style-spans)
- [Layout — column / row / box / weighted](#layout--column--row--box--weighted)
- [Decorations — backgrounds, corners, borders, gradients](#decorations--backgrounds-corners-borders-gradients)
- [Dividers, lines and shapes](#dividers-lines-and-shapes)
- [Lists](#lists)
- [Tables](#tables)
- [Images](#images)
- [Vector / SVG](#vector--svg)
- [Header, footer, page numbers, watermark](#header-footer-page-numbers-watermark)
- [Hyperlinks](#hyperlinks)
- [i18n fonts (CJK / Arabic / Persian) and custom fonts](#i18n-fonts-cjk--arabic--persian-and-custom-fonts)
- [Saving the document](#saving-the-document)
- [Page break strategies](#page-break-strategies)
- [Sample apps](#sample-apps)
- [Contributing](#contributing)
- [License](#license)

---

## Installation

PdfKmp is published to **Maven Central**. The library exposes:
- an Android `aar` (`io.github.conamobiledev:pdfkmp-android`),
- an iOS framework named `PdfKmp` for arm64, x64, and simulator-arm64,
- common Kotlin metadata (`io.github.conamobiledev:pdfkmp`).

### Kotlin Multiplatform project

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.conamobiledev:pdfkmp:0.1.0-alpha01")
        }
    }
}
```

### Android-only project

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.conamobiledev:pdfkmp-android:0.1.0-alpha01")
}
```

### iOS / Swift consumer

The Xcode project consumes the framework via a Gradle build phase that calls
`./gradlew :pdfkmp:embedAndSignAppleFrameworkForXcode`. Add a build phase to
your iOS target:

```bash
cd "$SRCROOT/.." && ./gradlew :pdfkmp:embedAndSignAppleFrameworkForXcode
```

Then `import PdfKmp` from Swift.

### Versioning policy

The library follows [semver](https://semver.org). Pre-1.0 releases (`0.x.y`) may introduce small API changes between minor versions; alpha tags (`-alpha0N`) signal that the surface is still settling. Breaking changes are documented in the GitHub release notes.

### Requirements

- JDK 17+
- Android Gradle Plugin 8.x, `compileSdk` 34+
- Xcode 16+ for iOS framework consumers

---

## Hello world

```kotlin
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.sp

val document = pdf {
    metadata { title = "Hello, PdfKmp" }
    page {
        text("Hello, world!") {
            fontSize = 24.sp
            bold = true
            color = PdfColor.Blue
        }
    }
}

val bytes: ByteArray = document.toByteArray()
```

`toByteArray()` returns the encoded PDF — write it to disk, attach it to an email, ship it over the wire, or feed it to a viewer.

---

## Document & pages

Every document opens with `pdf { ... }`. Inside, configure metadata, document-wide defaults, and add one or more pages:

```kotlin
pdf {
    metadata {
        title = "Quarterly Report"
        author = "PdfKmp"
        subject = "Sales summary for Q1"
        keywords = listOf("sales", "q1", "report")
    }

    // Defaults inherited by every page until overridden inside the page block.
    defaultTextStyle = TextStyle(fontSize = 12.sp, color = PdfColor.DarkGray)
    defaultPagePadding = Padding.symmetric(horizontal = 40.dp, vertical = 56.dp)
    defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage

    page(size = PageSize.A4) {
        spacing = 12.dp
        text("First page")
    }

    page(size = PageSize.Letter) {
        padding = Padding.all(20.dp) // override default for this page
        text("Second page — Letter format")
    }
}
```

Available page sizes include `PageSize.A4`, `PageSize.A5`, `PageSize.Letter`, `PageSize.Legal`, plus `PageSize.custom(widthPt, heightPt)`. Use `Padding.all`, `Padding.symmetric`, or pass each side explicitly.

---

## Text

Text takes a string plus an optional configuration block. Every property starts at the inherited `textStyle` and can be overridden:

```kotlin
text("Heading") {
    fontSize = 28.sp
    fontWeight = FontWeight.Bold      // or `bold = true`
    fontStyle = FontStyle.Italic      // or `italic = true`
    color = PdfColor.Black
    letterSpacing = 1.5.sp
    lineHeight = 32.sp                // 0 keeps the font's natural value
    underline = true
    strikethrough = false
    align = TextAlign.Center          // Start / Center / End / Justify
    font = PdfFont.Default            // Inter (bundled)
}
```

Color helpers: `PdfColor.Red / Green / Blue / Black / White / Gray / LightGray / DarkGray`, `PdfColor(r, g, b, a)` for floats in 0..1, and `PdfColor.fromRgb(0xFF5722)` for hex.

---

## Rich text (multi-style spans)

When a single paragraph mixes weights, colors, or decorations and must wrap as one block of prose, use `richText { ... }`:

```kotlin
richText {
    span("This sentence has a ")
    span("highlighted") { color = PdfColor.Red; bold = true }
    span(" word and an ")
    span("italic phrase") { italic = true }
    span(" inside it.")
}
```

The wrapper packs every span into one paragraph, wraps lines at the parent's width, and respects per-span styling end-to-end.

---

## Layout — column / row / box / weighted

PdfKmp uses Compose-flavoured layout primitives.

### Column (vertical stack)

```kotlin
column(
    spacing = 8.dp,
    verticalArrangement = VerticalArrangement.Top,           // Top / Center / Bottom / SpaceBetween / ...
    horizontalAlignment = HorizontalAlignment.Start,         // Start / Center / End
) {
    text("First")
    text("Second")
    text("Third")
}
```

### Row (horizontal stack)

```kotlin
row(
    spacing = 12.dp,
    horizontalArrangement = HorizontalArrangement.SpaceBetween,
    verticalAlignment = VerticalAlignment.Center,
) {
    text("Left")
    text("Right")
}
```

### Box (Z-stack)

Layered children — first added is at the bottom, last on top.

```kotlin
box(width = 400.dp, height = 200.dp, cornerRadius = 12.dp) {
    image(bytes = heroBytes, contentScale = ContentScale.Crop)
    aligned(BoxAlignment.BottomStart) {
        text("Hero title") { color = PdfColor.White; fontSize = 28.sp }
    }
}
```

### Weighted children

Inside a `row` or `column`, `weighted(n)` claims a fractional share of the leftover space along the main axis:

```kotlin
row(spacing = 12.dp) {
    weighted(1f) { text("33%") }
    weighted(2f) { text("66%") }
}
```

### Spacer

Explicit gap when the container's `spacing` isn't enough:

```kotlin
spacer(height = 24.dp)
```

---

## Decorations — backgrounds, corners, borders, gradients

Every container (`column`, `row`, `box`, `card`) accepts the same decoration vocabulary.

### Solid background + uniform border + rounded corners

```kotlin
card(
    background = PdfColor.White,
    cornerRadius = 12.dp,
    padding = Padding.all(16.dp),
    border = BorderStroke(1.dp, PdfColor.LightGray),
) {
    text("Material-style card")
}
```

`card { ... }` is shorthand for a decorated `column`.

### Per-corner radius

When corners differ — tabs, asymmetric tiles — pass `cornerRadiusEach`:

```kotlin
box(
    width = 240.dp,
    height = 60.dp,
    background = PdfColor.Blue,
    cornerRadiusEach = CornerRadius.top(16.dp), // top-only rounded; bottom flat
) { aligned(BoxAlignment.Center) { text("Active tab") { color = PdfColor.White } } }

// Fully asymmetric:
cornerRadiusEach = CornerRadius(
    topLeft = 24.dp, topRight = 4.dp,
    bottomLeft = 4.dp, bottomRight = 24.dp,
)
```

### Per-side border

Outline only the sides you want — Material-style accent quotes, progress steppers:

```kotlin
card(
    cornerRadius = 0.dp,
    padding = Padding.all(12.dp),
    borderEach = BorderSides(
        bottom = BorderStroke(2.dp, PdfColor.Blue),
        left = BorderStroke(2.dp, PdfColor.Blue),
    ),
) {
    text("Accent quote — bottom + left only")
}
```

### Gradient backgrounds

Pass a `PdfPaint` (linear or radial) via `backgroundPaint`. Coordinates are local to the container — `(0, 0)` is its top-left:

```kotlin
box(
    width = 480.dp, height = 80.dp, cornerRadius = 12.dp,
    backgroundPaint = PdfPaint.linearGradient(
        from = PdfColor.Blue, to = PdfColor.Red,
        endX = 480f, endY = 0f,
    ),
) {
    aligned(BoxAlignment.Center) {
        text("Sunrise banner") { color = PdfColor.White; bold = true }
    }
}

// Radial:
backgroundPaint = PdfPaint.radialGradient(
    from = PdfColor.White, to = PdfColor(0.1f, 0.1f, 0.4f),
    centerX = 240f, centerY = 60f, radius = 240f,
)

// Multi-stop:
backgroundPaint = PdfPaint.LinearGradient(
    startX = 0f, startY = 0f, endX = 480f, endY = 0f,
    stops = listOf(
        GradientStop(0.00f, PdfColor.Red),
        GradientStop(0.33f, PdfColor.Green),
        GradientStop(0.66f, PdfColor.Blue),
        GradientStop(1.00f, PdfColor.Red),
    ),
)
```

---

## Dividers, lines and shapes

### Divider

A thin horizontal rule that spans the parent's width:

```kotlin
divider(thickness = 0.5.dp, color = PdfColor.Gray)                       // solid
divider(thickness = 1.dp,   color = PdfColor.Gray, style = LineStyle.Dashed)
divider(thickness = 1.5.dp, color = PdfColor.Red,  style = LineStyle.Dotted)
```

### Circle / Ellipse

Vector circles and ellipses with solid fills, gradient paints, or stroke-only outlines:

```kotlin
circle(diameter = 60.dp, fill = PdfColor.Red)
circle(
    diameter = 80.dp,
    fillPaint = PdfPaint.radialGradient(
        from = PdfColor.White, to = PdfColor.Blue,
        centerX = 40f, centerY = 40f, radius = 40f,
    ),
)
ellipse(width = 100.dp, height = 60.dp, fill = PdfColor.Green)
circle(diameter = 60.dp, strokeColor = PdfColor.Black, strokeWidth = 2.dp)
```

---

## Lists

```kotlin
bulletList(
    items = listOf(
        "First item.",
        "Wrapped continuation lines line up under the first text line, not under the bullet.",
        "Third item.",
    ),
)

bulletList(items = listOf("Step", "Skip", "Stomp"), bullet = "→")

numberedList(
    items = listOf("Author", "Render", "Ship"),
    startAt = 1,
)
```

---

## Tables

```kotlin
table(
    columns = listOf(
        TableColumn.Fixed(70.dp),     // fixed width
        TableColumn.Weight(2f),       // share of remaining
        TableColumn.Weight(1f),
    ),
    border = TableBorder(color = PdfColor.fromRgb(0xCFD8DC), width = 1.dp),
    cornerRadius = 10.dp,
    cellPadding = Padding.symmetric(horizontal = 12.dp, vertical = 10.dp),
) {
    header(background = PdfColor.fromRgb(0xECEFF1)) {
        cell("ID")
        cell("Customer")
        cell("Status", horizontalAlignment = HorizontalAlignment.End)
    }

    users.forEachIndexed { i, user ->
        val zebra = if (i % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)
        row(background = zebra) {
            cell(user.id) { color = PdfColor.Gray }
            cell {
                text(user.name) { bold = true }
                text(user.email) { fontSize = 10.sp; color = PdfColor.Gray }
            }
            cell(
                value = user.status,
                horizontalAlignment = HorizontalAlignment.End,
                verticalAlignment = VerticalAlignment.Center,
            )
        }
    }
}
```

Cells can wrap arbitrary node trees — stack a title + subtitle, embed an icon, etc. Border configuration: `TableBorder.None`, or set `showOutline / showHorizontalLines / showVerticalLines` independently.

---

## Images

PNG and JPEG are decoded everywhere; WebP / HEIF where the platform supports them.

```kotlin
image(bytes = imageBytes, width = 300.dp, contentScale = ContentScale.Fit)

// Square crop:
image(
    bytes = imageBytes,
    width = 200.dp,
    height = 200.dp,
    contentScale = ContentScale.Crop,
)

// Width given, height derived from intrinsic aspect ratio:
image(bytes = imageBytes, width = 480.dp)

// Intrinsic pixel size (1px → 1pt):
image(bytes = imageBytes)
```

`ContentScale.FillBounds` stretches to the destination box.

---

## Vector / SVG

Both Android `<vector>` XML and W3C `<svg>` are accepted by `VectorImage.parse(...)`. Vectors stay vector inside the PDF — no rasterisation, sharp at any zoom.

```kotlin
val star = VectorImage.parse(starXml) // parse once, reuse many times

vector(image = star, width = 64.dp)
vector(image = star, width = 64.dp, tint = PdfColor.Red)              // override fill
vector(image = star, width = 64.dp, strokeMode = VectorStrokeMode.Disabled)

// Inline parsing for one-offs:
vector(xml = """<svg ...>...</svg>""", width = 48.dp)
```

Supported features include solid fills, linear and radial gradients, elliptical arcs (`A`/`a` SVG path commands), and `<g transform="translate/rotate/scale">` group transforms.

---

## Header, footer, page numbers, watermark

Every logical page can declare a header, footer, and watermark. Headers and footers receive a `PageContext` carrying the current page number and the total page count — accurate even when content slices across multiple physical pages, because the renderer does a counting dry-run before the real pass.

```kotlin
page {
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

    footer { ctx ->
        divider(thickness = 0.5.dp, color = PdfColor.LightGray, style = LineStyle.Dashed)
        text("conamobile · pdfkmp") {
            fontSize = 10.sp; color = PdfColor.Gray; align = TextAlign.Center
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

    // … body content
}
```

Header is rendered inside the top page padding band, footer inside the bottom padding band, watermark behind the body content of every physical page.

---

## Hyperlinks

`link(url) { … }` wraps any content in a clickable region:

```kotlin
link(url = "https://github.com/conamobiledev/PdfKmp") {
    text("github.com/conamobiledev/PdfKmp") {
        color = PdfColor.Blue; underline = true
    }
}

link(url = "mailto:hello@example.com") {
    text("hello@example.com") { color = PdfColor.Blue; underline = true }
}

// Whole card clickable:
link(url = "https://example.com") {
    card(border = BorderStroke(1.dp, PdfColor.Blue)) {
        text("Visit site →") { color = PdfColor.Blue; bold = true }
    }
}
```

On iOS the rectangles produce real PDF link annotations via `UIGraphicsSetPDFContextURLForRect`. Android's `PdfDocument` does not expose annotation APIs in v1, so on Android the visual styling stands in for the click target — the rectangle is recorded but readers cannot dispatch clicks. Both behaviours are intentional and the iOS output works in any reader (Preview, Adobe Reader, Chrome's PDF viewer).

---

## i18n fonts (CJK / Arabic / Persian) and custom fonts

PdfKmp ships **Inter** for Latin text. For non-Latin scripts, use the cross-platform `PdfFont.System*` references — each one is a comma-separated candidate chain that resolves to whichever font the running platform has installed:

```kotlin
text("漢字、ひらがな、カタカナ — 中文 / 日本語 / 한국어") {
    font = PdfFont.SystemCJK         // → "Noto Sans CJK SC, PingFang SC"
}

text("مرحبًا بكم في PdfKmp") {
    font = PdfFont.SystemArabic      // → "Noto Sans Arabic, Geeza Pro"
}

text("سلام دنیا") {
    font = PdfFont.SystemPersian     // → "Noto Naskh Arabic, Tahoma, Geeza Pro"
}
```

If a platform is missing every candidate the renderer falls back to Inter, which lacks those glyphs. Register a `.ttf` / `.otf` to guarantee coverage:

```kotlin
val notoCjk = PdfFont.Custom(name = "NotoSansCJK", bytes = readFromAssets("NotoSansCJK.ttf"))

pdf {
    registerFont(notoCjk)
    page {
        text("永和九年") { font = notoCjk }
    }
}
```

Custom fonts referenced anywhere in the document are picked up automatically — `registerFont` is only needed for fonts that no current node references but should still be embedded.

---

## Saving the document

`PdfDocument.save(...)` is a multiplatform helper that writes the PDF to a typed location. Cross-platform:

```kotlin
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save

val doc = pdf { /* ... */ }

val saved = doc.save(StorageLocation.Cache, filename = "report.pdf")
println(saved.path) // platform-specific absolute path
```

Available locations:

| Location | Android | iOS |
|---|---|---|
| `Cache` | app-internal cache | `Caches/` |
| `AppFiles` | app-internal files | `Library/` |
| `AppExternalFiles` | scoped external (no permission) | not applicable; falls back to `Documents/` |
| `Downloads` | shared `Downloads/` via MediaStore | shared Files app `Downloads/` |
| `Documents` | shared `Documents/` | iCloud `Documents/` |
| `Temp` | `cacheDir/tmp/` | `tmp/` |
| `Custom("/abs/path/dir")` | any writable directory | any writable directory |

`save` returns a `SavedPdf` carrying `path`, `name`, and `location`. For low-level bytes, call `toByteArray()` or `toNSData()` (iOS) directly.

---

## Page break strategies

Long content overflows pages naturally. Two strategies control the split:

```kotlin
page {
    pageBreakStrategy = PageBreakStrategy.MoveToNextPage  // default
    // Whole element moves to a new page if it would not fit.
}

page {
    pageBreakStrategy = PageBreakStrategy.Slice
    // Text splits at line boundaries; tall images cut at the page edge
    // and continue seamlessly on the next page.
}
```

Set the document-wide default via `defaultPageBreakStrategy`, override per page on `PageScope.pageBreakStrategy`.

---

## Sample apps

The repo contains two sample apps that mirror each other feature-for-feature:

- `:sample` — Compose Android app (`./gradlew :sample:installDebug`).
- `iosApp/iosApp.xcodeproj` — SwiftUI iOS app. Open in Xcode and Run; the build phase calls `:pdfkmp:embedAndSignAppleFrameworkForXcode` automatically.

Both apps surface every demo from `Samples` (in `:pdfkmp/src/commonMain/kotlin/.../samples/Samples.kt`) so you can eyeball each feature on real devices.

---

## Contributing

Bug reports, feature requests and pull requests are all welcome — open an issue at <https://github.com/ConaMobileDev/PdfKmp/issues> or send a PR.

When working on the library locally:

```bash
# Run the full test suite (canonical surface — exercises every backend on iOS Simulator)
./gradlew :pdfkmp:iosSimulatorArm64Test

# Build all platform artefacts
./gradlew :pdfkmp:assemble

# Install the Android sample on a connected device
./gradlew :sample:installDebug
```

For repo conventions and AI-agent guidance see [CLAUDE.md](CLAUDE.md) (working **on** this codebase) and [AGENTS.md](AGENTS.md) / `.claude/skills/pdfkmp/SKILL.md` (using the library).

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

The bundled Inter font is licensed separately under the SIL Open Font License 1.1 — see `pdfkmp/fonts/Inter-LICENSE.txt`.

---

## Powered by Claude

This library — the public API, the layout engine, the Android and iOS rendering backends, every test, the sample apps, and this README — was authored end-to-end with [Claude Code](https://claude.com/claude-code), Anthropic's coding agent. Bug reports, suggestions, and PRs welcome.
