# PdfKmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.conamobiledev/pdfkmp.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.conamobiledev/pdfkmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/platform-Android%20%7C%20iOS%20%7C%20Desktop%20(macOS%20%C2%B7%20Windows%20%C2%B7%20Linux)%20%7C%20Web%20(Wasm)-lightgrey.svg)](#installation)
[![Docs](https://img.shields.io/badge/docs-conamobiledev.github.io%2FPdfKmp-7c6ff0.svg)](https://conamobiledev.github.io/PdfKmp/)

> Kotlin Multiplatform PDF generator for Android, iOS, Desktop (JVM) & Web (Wasm) — vector-first, type-safe, DSL-driven.

**📖 Full documentation: [conamobiledev.github.io/PdfKmp](https://conamobiledev.github.io/PdfKmp/)** — 20 hand-written guides, every sample, and the [Dokka API reference](https://conamobiledev.github.io/PdfKmp/api/).

<table align="center">
  <tr>
    <td align="center"><strong>Android</strong></td>
    <td align="center"><strong>Desktop — macOS · Windows · Linux (JVM)</strong></td>
  </tr>
  <tr>
    <td align="center" valign="middle"><img src="docs/screenshots/brochure-1.png" width="300" alt="PdfKmp brochure rendered on Android" /></td>
    <td align="center" valign="middle"><img src="docs/screenshots/brochure-3.png" width="580" alt="PdfKmp brochure rendered on Desktop (macOS / Windows / Linux)" /></td>
  </tr>
  <tr>
    <td align="center"><strong>iOS</strong></td>
    <td align="center"><strong>Web (Kotlin/Wasm)</strong></td>
  </tr>
  <tr>
    <td align="center" valign="middle"><img src="docs/screenshots/brochure-2.png" width="300" alt="PdfKmp brochure rendered on iOS" /></td>
    <td align="center" valign="middle"><img src="docs/screenshots/web-sample.png" width="580" alt="The PdfKmp web sample running in the browser — every PDF generated in-page by Kotlin/Wasm" /></td>
  </tr>
</table>

<p align="center">
  <em>The same <code>Samples.brochure()</code> document rendered on Android, iOS and Desktop — pixel-identical vector output — plus the browser sample generating it live on the Web. Try it: <code>Samples.brochure().toByteArray()</code>.</em>
</p>

PdfKmp lets you build PDF documents from a Compose-style DSL that runs identically on Android, iOS, Desktop (JVM — macOS, Windows, Linux), and the browser (Kotlin/Wasm). Text becomes glyph paths, shapes become path operators — every page stays sharp at any zoom level. The library ships the **Inter** font for cross-platform Latin parity and exposes opt-in references to system CJK / Arabic / Persian fonts so non-Latin scripts render natively on Android and iOS (on Desktop, supply a custom font — see [i18n fonts](https://conamobiledev.github.io/PdfKmp/guides/i18n/)).

**What's in the box:**

- **Rich text engine** — full justification, automatic hyphenation, `maxLines` + ellipsis, soft-hyphen + mid-word breaking, super/subscript spans, right-to-left support with bidi reorder + Arabic shaping (incl. kashida justification), and orphan/widow control.
- **Layout & pagination** — column / row / box / card, weighted children, recursive column slicing, table row slicing with repeating headers, `keepTogether`, multi-column (`columns`) and uniform `grid`, mixed page orientations.
- **Tables, lists, dividers, circles & ellipses**, plus a free-form vector DSL (`freeDraw`).
- **Graphics** — QR codes, Code 128 / EAN-13 / UPC-A barcodes, Data Matrix, and bar / stacked-bar / (multi-series) line / pie / donut charts — all pure vector, no external dependencies.
- **Decorations** — backgrounds, per-corner radii, per-side borders, dashed / dotted borders, gradients, drop shadows, rotation, and group opacity.
- **Navigation & document features** — bookmarks / outline, internal links + auto table of contents, hyperlinks, AcroForm fields, encryption, file attachments, best-effort PDF/A, and JVM digital signing.
- **Images & vectors** — PNG / JPEG / WebP / HEIF, Android `<vector>` and W3C `<svg>` (kept vector inside the PDF).
- **Companion modules** — a Compose Multiplatform PDF [viewer](https://conamobiledev.github.io/PdfKmp/guides/viewer/), a [Compose Resources](https://conamobiledev.github.io/PdfKmp/guides/compose-resources/) bridge, and a [Markdown renderer](https://conamobiledev.github.io/PdfKmp/guides/markdown/).

See the [platform feature parity](https://conamobiledev.github.io/PdfKmp/guides/platform-parity/) table for what each backend supports.

**Under the hood — native / pure-Java PDF stacks per platform:**

| Platform | Backend |
|---|---|
| Android | [`android.graphics.pdf.PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) + `android.graphics.Canvas` |
| iOS | [`UIGraphicsBeginPDFContextToData`](https://developer.apple.com/documentation/uikit/uigraphicsbeginpdfcontexttodata) + Core Graphics (`CGContext`) |
| Desktop (JVM) | [Apache PDFBox](https://pdfbox.apache.org/) — pure-Java, runs on macOS / Windows / Linux with no native libraries |
| Web (Wasm) | `kmpwriter` — PdfKmp's own pure-Kotlin PDF 1.7 writer (no browser PDF API exists), with TrueType subsetting + Flate compression |

Every DSL node funnels into these PDF backends, so the resulting bytes are real, vector PDFs — readable in Preview, Adobe Reader, Chrome, and any spec-compliant viewer. On Desktop the embedded TrueType fonts are subsetted to only the glyphs you draw, so text stays vector and the file stays small.

---

## Installation

PdfKmp is published to **Maven Central**. The library exposes:
- an Android `aar` (`io.github.conamobiledev:pdfkmp-android`),
- an iOS framework named `PdfKmp` for arm64 (device) and simulator-arm64 (Apple-Silicon simulator),
- a Desktop / JVM `jar` (`io.github.conamobiledev:pdfkmp-jvm`) for macOS, Windows, and Linux,
- a Web / Kotlin-Wasm klib (`io.github.conamobiledev:pdfkmp-wasm-js`) — see [Web (Kotlin/Wasm)](#web-kotlinwasm) for what the browser backend covers,
- common Kotlin metadata (`io.github.conamobiledev:pdfkmp`),
- an optional Compose Multiplatform Resources companion (`io.github.conamobiledev:pdfkmp-compose-resources`, with `-android` / `-jvm` / `-wasm-js` platform variants),
- an optional Markdown renderer (`io.github.conamobiledev:pdfkmp-markdown`, same platform variants),
- an optional Compose Multiplatform PDF viewer screen (`io.github.conamobiledev:pdfkmp-viewer`, with `-android` / `-jvm` platform variants — not on web; see below).

### Web (Kotlin/Wasm)

The browser exposes no PDF engine to Wasm, so the web target renders
through PdfKmp's own pure-Kotlin PDF writer. Everything vector works
exactly like the other platforms — text layout, tables, shapes,
gradients, QR codes, barcodes, Data Matrix, charts, freeDraw,
bookmarks/TOC, links — plus JPEG and 8-bit RGB/grayscale PNG images.
The web backend now **embeds fonts**: a `PdfFont.Custom` is embedded as
a TrueType subset, and non-WinAnsi text with no custom font falls back to
a bundled-Inter subset, so **Cyrillic works in the browser out of the
box** (plain Latin still uses Standard-14 Helvetica); content streams are
Flate-compressed. Remaining honest font limits: CFF/OTF custom fonts fall
back to Helvetica, single-face customs get no synthetic bold/italic, and
coverage is gated by the font's own glyphs (CJK/Arabic generally need a
`PdfFont.Custom`). Other current limits (each warned through `PdfLog` and
skipped): palette/alpha PNGs, interactive form widgets, encryption, and
attachments.

Viewing on the web needs no library at all — browsers ship excellent
PDF viewers, so hand them the bytes:

```kotlin
val doc = pdf { page { text("Hello, Wasm!") } }
doc.openInNewTab()                                  // browser's own viewer
doc.save(StorageLocation.Downloads, "hello.pdf")    // browser download
```

Make sure Maven Central is in your repository list:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Pick the section that matches your project. The paths are mutually exclusive — KMP projects depend on `pdfkmp` only (Gradle resolves the right platform variant automatically, **including for Android and Desktop targets**), while plain Android projects depend on `pdfkmp-android` and plain JVM/Desktop projects on `pdfkmp-jvm`. Inside each path, use whichever dependency style your project already uses; the `libs.versions.toml` form is recommended for new projects.

### Kotlin Multiplatform project

Depend on `pdfkmp` from `commonMain`. Do **not** add `pdfkmp-android` separately — Gradle picks the Android variant of `pdfkmp` for your Android target on its own.

With a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
pdfkmp = "1.2.0"

[libraries]
pdfkmp = { module = "io.github.conamobiledev:pdfkmp", version.ref = "pdfkmp" }
pdfkmp-compose-resources = { module = "io.github.conamobiledev:pdfkmp-compose-resources", version.ref = "pdfkmp" } # optional
pdfkmp-viewer = { module = "io.github.conamobiledev:pdfkmp-viewer", version.ref = "pdfkmp" } # optional
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.pdfkmp)
            implementation(libs.pdfkmp.compose.resources) // optional
            implementation(libs.pdfkmp.viewer)            // optional
        }
    }
}
```

Without a version catalog:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.conamobiledev:pdfkmp:1.2.0")
            implementation("io.github.conamobiledev:pdfkmp-compose-resources:1.2.0") // optional
            implementation("io.github.conamobiledev:pdfkmp-viewer:1.2.0")            // optional
        }
    }
}
```

Both companions are **opt-in**:

- `pdfkmp-compose-resources` — add it only if your project uses Compose Multiplatform Resources (`Res.drawable.*`) and you want typed resource references inside the PdfKmp DSL. Full usage in the [Compose Resources guide](https://conamobiledev.github.io/PdfKmp/guides/compose-resources/).
- `pdfkmp-viewer` — Compose Multiplatform PDF viewer screen with topbar, search, share, save, hyperlinks, gesture-driven zoom & text selection. Skip it if you only need to *generate* PDFs and let users view them in their system default reader. Full usage in the [viewer guide](https://conamobiledev.github.io/PdfKmp/guides/viewer/).

The core `pdfkmp` artifact itself stays Compose-free — non-Compose consumers don't pay for either companion.

### Android-only project

For projects that don't use Kotlin Multiplatform (plain `com.android.application` or `com.android.library`), depend on `pdfkmp-android`. KMP consumers should use the section above instead — they should not add this artifact.

With a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
pdfkmp = "1.2.0"

[libraries]
pdfkmp-android = { module = "io.github.conamobiledev:pdfkmp-android", version.ref = "pdfkmp" }
pdfkmp-viewer = { module = "io.github.conamobiledev:pdfkmp-viewer", version.ref = "pdfkmp" } # optional
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.pdfkmp.android)
    implementation(libs.pdfkmp.viewer) // optional — Compose viewer screen
}
```

Without a version catalog:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.conamobiledev:pdfkmp-android:1.2.0")
    implementation("io.github.conamobiledev:pdfkmp-viewer:1.2.0") // optional
}
```

`pdfkmp-viewer` brings in Compose Multiplatform — skip it if your Android project still uses the legacy View system. The viewer assumes Compose hosting (a `ComponentActivity` with `setContent { … }` or an embedded `ComposeView`).

### Desktop / JVM project

KMP projects with a `jvm()` target need no extra setup — Gradle resolves the Desktop variant of `pdfkmp` for the JVM target automatically, exactly like it does for Android. For a **plain JVM project** (a standalone Compose for Desktop app or any non-KMP Gradle JVM module), depend on `pdfkmp-jvm` directly:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.conamobiledev:pdfkmp-jvm:1.2.0")
    implementation("io.github.conamobiledev:pdfkmp-viewer-jvm:1.2.0") // optional — Compose for Desktop viewer
}
```

The Desktop backend is built on [Apache PDFBox](https://pdfbox.apache.org/) (pulled in transitively) and is pure-Java, so the same artifact runs on macOS, Windows, and Linux with no native libraries to bundle. The generation API (`pdf { … }`, `document.save(...)`, `toByteArray()`) is identical across all platforms. `pdfkmp-viewer` on Desktop hosts the viewer in a Compose for Desktop window and rasterises pages with PDFBox's `PDFRenderer`.

### Requirements

- JDK 17+
- Android Gradle Plugin 8.x, `compileSdk` 34+ (Android targets)
- Xcode 16+ when targeting iOS via Kotlin Multiplatform
- A desktop JRE 17+ when targeting Desktop / JVM (macOS, Windows, Linux)

### R8 / ProGuard

R8 is fully supported — no additional keep rules required.

---

## Hello world

```kotlin
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.sp

// 1. Build the document
//    Use `pdf { }` for a synchronous build, or `pdfAsync { }` (suspend) when
//    the tree contains typed `Res.drawable.*` references — see Compose
//    Multiplatform Resources below.
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

// 2. Pick what to do with it:
val bytes: ByteArray = document.toByteArray()              // raw bytes
// or
val saved = document.save(StorageLocation.Cache, "hello.pdf")  // returns SavedPdf (synchronous)
println(saved.path)  // absolute filesystem path you can hand to a viewer / share intent
```

After `save(...)` you get a `SavedPdf` with a real filesystem path you can hand to a PDF viewer or a share sheet — the [Getting started guide](https://conamobiledev.github.io/PdfKmp/getting-started/) has ready-made view / share / open snippets for every platform.

---

## Documentation

This README covers installation and your first document only — **everything else lives on the documentation site**:

**📖 [conamobiledev.github.io/PdfKmp](https://conamobiledev.github.io/PdfKmp/)**

- [Getting started](https://conamobiledev.github.io/PdfKmp/getting-started/) — every feature guide: text & typography, layout, tables, decorations, images & SVG, QR / barcodes / charts, navigation & TOC, pages / headers / watermarks, forms & security, fonts & i18n, the Compose viewer, Markdown-to-PDF, and the Web (Wasm) target.
- [Samples](https://conamobiledev.github.io/PdfKmp/samples/) — every bundled showcase document, with the code that builds it.
- [Internals](https://conamobiledev.github.io/PdfKmp/internals/architecture/) — how the layout engine and the per-platform renderers work.
- [API Reference](https://conamobiledev.github.io/PdfKmp/api/) — generated KDoc for every public declaration.

For repo conventions and AI-agent guidance see [CLAUDE.md](CLAUDE.md) (working **on** this codebase) and [AGENTS.md](AGENTS.md) / [`.claude/skills/pdfkmp/SKILL.md`](.claude/skills/pdfkmp/SKILL.md) (using the library).

---

## Powered by Claude

This library — the public API, the layout engine, the Android and iOS rendering backends, every test, the sample apps, and this README — was authored end-to-end with [Claude Code](https://claude.com/claude-code), Anthropic's coding agent. Bug reports, suggestions, and PRs welcome.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

The bundled Inter font is licensed separately under the SIL Open Font License 1.1 — see `pdfkmp/fonts/Inter-LICENSE.txt`.
