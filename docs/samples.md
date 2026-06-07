# Samples

[`Samples.kt`](https://github.com/conamobiledev/PdfKmp/blob/master/pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/samples/Samples.kt)
ships ready-to-render demo documents you can call straight from any consumer —
handy as smoke tests, copy-paste starting points, or golden-file fixtures. Every
entry returns a `PdfDocument`; pipe it through `.toByteArray()` or `.save(...)`
and you have a real PDF in seconds.

```kotlin
val bytes = Samples.brochure().toByteArray()
```

## Every `Samples.*` function

| Function | What it shows |
|---|---|
| `Samples.helloWorld()` | Minimal one-page document. |
| `Samples.brochure()` | Multi-page marketing layout — the hero document (no raster assets). |
| `Samples.typography()` | Every `TextStyle` knob + decorations + alignment + `richText` spans. |
| `Samples.textAdvanced()` | Justification, `maxLines` + ellipsis, soft hyphens, super/subscript. |
| `Samples.rowAndColumn()` | Layout primitives with `weighted` children. |
| `Samples.columnSpaceBetween()` | Column with `VerticalArrangement.SpaceBetween`. |
| `Samples.alignmentShowcase()` | Cross-axis alignment dedup across containers. |
| `Samples.customPadding()` | Per-page padding override. |
| `Samples.tableShowcase()` | Tables (incl. a `colSpan` banner + `rowSpan` label merge), bullet lists, numbered lists. |
| `Samples.longTable()` | Table row slicing with a repeating header row. |
| `Samples.vectorShowcase()` | Vector / SVG icons + circles + ellipses. |
| `Samples.vectorAdvanced()` | Gradient fills, elliptical arcs, group transforms. |
| `Samples.customDesigns(imageBytes)` | Cards with gradients, corner radii, per-side borders. |
| `Samples.designExtras()` | Drop shadows, dashed / dotted borders, `freeDraw`, `grid`. |
| `Samples.barcodes()` | Vector QR codes (EC levels), Code 128, EAN-13 / UPC-A, and Data Matrix codes. |
| `Samples.withImage(imageBytes)` | Raster image with `ContentScale.Fit` + `.Crop`. |
| `Samples.slicedImage(imageBytes)` | Tall image sliced across multiple physical pages. |
| `Samples.imageDownscale(imageBytes)` | The `allowDownScale` knob — default vs full-resolution. |
| `Samples.longBody()` | Page-break strategy `MoveToNextPage`. |
| `Samples.slicedBody()` | Page-break strategy `Slice`. |
| `Samples.pageChrome()` | Header, footer, page numbers, watermark, hyperlinks, i18n fonts. |
| `Samples.pageTemplates()` | Book-style mirrored chrome + a mixed landscape page. |
| `Samples.navigation()` | Bookmarks, clickable auto-TOC, internal cross-reference links. |
| `Samples.newsletter()` | Pairs multi-column newspaper flow with auto-detected right-to-left paragraphs. |
| `Samples.formsAndAccessibility()` | AcroForm fields, image alt text, best-effort PDF/A. |
| `Samples.showcase()` | Every v1 feature in one PDF — headers / footers / watermarks, links, i18n, the lot. |

!!! note "Image samples take bytes"
    `withImage`, `slicedImage`, `imageDownscale`, and `customDesigns` take a
    `ByteArray` so the demo media stays in the consumer app's assets rather than
    the library jar — see `:sample/src/main/assets/sample.png` for the bytes used
    by the bundled apps.

## Running the samples

The repo ships three sample apps that mirror each other feature-for-feature.

=== "Desktop"
    A master/detail window listing every `Samples.*` document (plus a
    Compose-Resources demo and a live playground). Click one to open it in
    `KmpPdfViewer`:
    ```bash
    ./gradlew :sample-desktop:run
    ```

=== "Android"
    A categorised list with per-entry descriptions; the detail screen drops
    straight into `KmpPdfViewer`:
    ```bash
    ./gradlew :sample:installDebug
    ```

=== "iOS"
    Open `iosApp/iosApp.xcodeproj` in Xcode and Run. The build phase calls
    `:pdfkmp:embedAndSignAppleFrameworkForXcode` automatically.

The Desktop sample also includes a **live playground** that rebuilds a real
`pdf { }` document as you tweak controls.

## See also

- [Getting started](getting-started.md) — `pdf {}` / `pdfAsync {}`, save & share.
- [PDF viewer (Compose)](guides/viewer.md) — the viewer the samples open into.
