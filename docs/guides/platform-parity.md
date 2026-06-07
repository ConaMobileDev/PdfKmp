# Platform parity

The core layout, text, graphics, charts, QR / barcodes, and decorations render
**identically on every platform**. Document-level navigation and security
features differ because the native PDF backends expose different capabilities.

On **Android**, metadata, clickable links, internal links, and the outline are
produced by a pure-Kotlin **post-processor** that applies an incremental update to
the bytes `android.graphics.pdf.PdfDocument` produces (it has no API for any of
them); the patcher returns the original bytes unchanged on any parse surprise.

On **Web (Wasm)**, the pure-Kotlin `kmpwriter` backs the target — every vector
feature plus links, named destinations, the outline, and the info dictionary
work. It now **embeds fonts**: a `PdfFont.Custom` is embedded as a TrueType
subset, and non-WinAnsi text falls back to a bundled-Inter subset (so Latin +
Cyrillic render in the browser), with Flate-compressed content streams. See
[Web (Kotlin/Wasm)](web.md).

## Backends

| Platform | Backend |
|---|---|
| Android | `android.graphics.pdf.PdfDocument` + `Canvas` |
| iOS | `UIGraphicsBeginPDFContextToData` + Core Graphics |
| Desktop (JVM) | Apache PDFBox — pure-Java |
| Web (Wasm) | `kmpwriter` — PdfKmp's own pure-Kotlin PDF 1.7 writer |

## Feature matrix

| Feature | Android | iOS | Desktop (JVM) | Web (Wasm) |
|---|---|---|---|---|
| Core layout / text / shapes / decorations | ✅ | ✅ | ✅ | ✅ |
| Table `colSpan` / `rowSpan` merging | ✅ | ✅ | ✅ | ✅ |
| Charts / QR / barcodes / Data Matrix / freeDraw | ✅ | ✅ | ✅ | ✅ |
| Gradients | ✅ | ✅ | ✅ | ✅ |
| Info dictionary (title / author / …) | ✅ via post-processor | ✅ | ✅ | ✅ |
| Hyperlink annotations (`link`) | ✅ via post-processor | ✅ | ✅ | ✅ |
| Internal links (`anchor` / `linkToAnchor`) | ✅ via post-processor | ✅ | ✅ | ✅ named destinations |
| Outline / bookmarks + TOC | ✅ via post-processor | ✅ | ✅ | ✅ |
| Vector text embedding | ✅ native | ✅ native | ✅ subset TrueType | ✅ subset TrueType (Helvetica for plain Latin) |
| Non-Latin scripts (CJK / Arabic / Persian) | ✅ via `System*` | ✅ via `System*` | ⚠️ custom font required | ⚠️ custom font required (Cyrillic via bundled Inter) |
| Custom font embedding (`registerFont`) | ✅ | ✅ | ✅ | ✅ TrueType-outline (CFF/OTF → Helvetica) |
| Raster images | ✅ PNG/JPEG (+ WebP/HEIF where the OS decodes them) | ✅ PNG/JPEG (+ WebP/HEIF where the OS decodes them) | ✅ PNG/JPEG | ⚠️ JPEG + 8-bit RGB/gray PNG only |
| Encryption | ❌ no-op | ⚠️ passwords + print/copy (no "modify") | ✅ AES-256 | ❌ |
| File attachments | ❌ skipped | ❌ skipped | ✅ | ❌ |
| Forms interactivity (`textField` / `checkBox`) | ⚠️ static visual | ⚠️ static visual | ✅ interactive | ❌ static visual |
| PDF/A (best-effort) + image `/Alt` | ❌ ignored | ❌ ignored | ⚠️ best-effort | ❌ |
| Digital signing (`PdfSigner`) | ❌ | ❌ | ✅ | ❌ |
| `PdfTools` (merge / split / watermark / overlay) | ❌ | ❌ | ✅ | ❌ |
| Factur-X / ZUGFeRD attach (`attachFacturX`) | ❌ | ❌ | ✅ | ❌ |
| RTL bidi + Arabic shaping (+ kashida justify) | ✅ native | ✅ native | ✅ own bidi + shaping pass | ⚠️ embedded-font glyphs only |

✅ supported · ⚠️ partial / best-effort · ❌ not supported on that platform.

The `FacturXInvoice` builder is common code (it produces the `factur-x.xml`
string everywhere); only the `PdfTools.attachFacturX` *attachment* step is
JVM-only. See [Forms & security](forms-security.md).

!!! note "Viewer annotations"
    In the [viewer](viewer.md), highlight annotations are overlay-only on Android
    and iOS (UI state via `initialAnnotations` / `onAnnotationsChanged`). On
    **Desktop** they can be **written into the PDF** as real `Highlight`
    annotations — the download action then exports the annotated bytes.

## See also

- [Forms & security](forms-security.md) — encryption, attachments, signing, PDF/A.
- [Fonts, i18n & RTL](i18n.md) — the non-Latin coverage table.
- [Web (Kotlin/Wasm)](web.md) — the Web backend's documented limits.
