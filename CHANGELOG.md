# Changelog

All notable changes to this project are documented here. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/) and
versions follow [Semantic Versioning](https://semver.org). Pre-1.0
minor versions may break public API; alpha / beta / rc tags signal
an actively settling surface.

## [1.3.0] — 2026-08-17

### Breaking

- **`link(url) { … }` embeds only allowlisted schemes.** `http`, `https`,
  `mailto`, and `tel` become `/URI` annotations; every other scheme (and any
  relative, scheme-relative, or control-character-bearing URL) is skipped —
  the wrapped content is still drawn, just unlinked, and the skip is reported
  through `PdfLog`. A generated PDF outlives the app that wrote it, so an
  annotation carrying `javascript:`, `file:`, `data:`, or `content:` is a
  code-execution / local-file channel in readers that honour those schemes.
  The rule is exposed as `PdfUrls.isSafeExternalUrl(url)` and is applied again
  in `pdfkmp-viewer` before a tapped link reaches the OS. Internal navigation
  (`linkToAnchor`) is unaffected. See
  [`docs/guides/navigation.md`](docs/guides/navigation.md).
- **`save(location, filename)` validates the filename.** It must be a leaf
  name: path separators, `..`, NUL and other control characters, the Win32
  characters `: * ? " < > |`, Windows reserved device stems (`CON`, `COM1`, …),
  and a trailing space or dot now raise `IllegalArgumentException` before any
  platform code composes a path. Two previously-working call shapes break:
  passing a sub-path as the filename (`save(Custom("/dir"), "reports/a.pdf")`)
  and omitting the filename for a non-`Custom` location (`save(Downloads)`,
  which silently became `document.pdf` on the Web target). Pass an explicit
  leaf name. The checks are platform-independent on purpose — these files
  cross OS boundaries through share sheets and cloud sync.
- **An empty document is rejected.** `pdf { }` with no `page { }` block now
  throws `IllegalArgumentException` instead of producing a page-less file that
  no reader accepts. Guard data-driven page loops (`if (items.isEmpty()) return`).

### Added

- **`PdfImagePolicy.maxDecodePixels`** — the pixel ceiling applied to an
  image's *declared* header dimensions before any decode allocates memory
  (default 50 MP). Documents that legitimately carry very large imagery raise
  it once at startup; a hostile file that claims enormous dimensions in a tiny
  header is sub-sampled down to fit instead of taking the process with it.
- **`PdfUrls`** — the URL scheme allowlist described above, public so host code
  can pre-flight a URL with the same rule the DSL applies.
  **`PdfUrls.allowedSchemes`** widens it for trusted, in-house targets
  (`PdfUrls.DEFAULT_ALLOWED_SCHEMES + "myapp"`); entries are lower-cased and
  validated as RFC 3986 schemes on assignment. It is process-wide and governs
  annotation writing *and* the viewer's tap handling, so it must never be
  derived from document content.
- **`Samples.robustnessShowcase()`** — one document exercising the URL
  allowlist, surrogate-safe wrapping and ellipsis, the decode budget, and
  slice-safe tables. Wired into the desktop sample.

### Fixed

- **iOS: the abort path leaked a process-global graphics context.** When a draw
  call threw before `finish()`, `IosPdfDriver` never ran
  `UIGraphicsEndPDFContext()` or unregistered its custom fonts — the abandoned
  PDF context stayed on the thread's UIKit stack for the rest of the process,
  so every later `UIGraphicsGetCurrentContext()` caller inherited it. The
  driver now implements `close()` (idempotent, and a no-op after a successful
  `finish()`), matching the Android and JVM drivers.
- **The image decode budget behaved differently on each platform.** Android
  sub-sampled an oversized image and rendered it; iOS and the JVM dropped it
  outright — so one document produced an image on one platform and a blank slot
  on another. All three now sub-sample through the same shared factor
  (`inSampleSize` on Android, `ImageReadParam.setSourceSubsampling` on the JVM,
  `CGImageSourceCreateThumbnailAtIndex` on iOS). The pure-Kotlin/wasm writer,
  which embeds encoded streams verbatim and owns no decoder, refuses an
  over-budget image rather than passing the bomb to the reader — previously it
  applied no bound at all and embedded a 2.5-gigapixel `/Width`/`/Height` pair
  as-is.
- **Android: one decode path skipped the budget entirely.** When both declared
  dimensions already fell under the 200-DPI target, `decodeBitmap` returned an
  unsampled full decode without consulting the budget, so a large enough
  requested draw size let a dimension bomb through. Every decode now goes
  through the bounds pass first.
- **A table header cell spanning into the body could paint past its chunk.**
  The header's merged rectangle is drawn from a `spannedHeight` fixed at
  measure time, but the body-row grouping ignored the header's reach, so a
  first chunk holding fewer rows than the cell spans painted it into the
  margin while those rows rendered on the next page. The spanned rows are now
  pinned to the header's chunk.
- The JVM backend parsed an image header three times per drawn slice (common
  probe, ImageIO probe, then the real read); it now sizes and decodes in one
  reader pass — for a tall sliced image that was once per page.
- **`pdfkmp-markdown` styled the same link two different ways.** Only the
  standalone-link path honoured the scheme allowlist, so `[docs](#anchor)`
  rendered as plain body text on a line of its own and as blue underlined
  "clickable" text inside a sentence. Both paths now share one predicate.
  Relative and anchor targets also no longer call the core `link()` just to
  have it refuse them, so a document of internal cross-references stops
  emitting one `PdfLog` warning per link.
- **The JVM fallback face could be embedded twice.** A document that used the
  bundled Inter *and* fell back to it (after an unparseable custom font)
  carried two copies of the same font program; the fallback now shares the
  registry's cache entry with the ordinary lookup.
- **`MiniXml` decoded numeric references to forbidden control characters.**
  XML 1.0's `Char` production admits only tab, LF and CR from the C0 range, but
  only NUL and the surrogate halves were filtered — `&#1;`, `&#xB;`, `&#xFFFE;`
  and friends materialised as real control characters. They now fall back to
  raw text like any other invalid reference.
- Font-load failures on Android and the JVM now report the underlying cause.
  A truncated file, an unsupported CFF outline, and a full cache partition all
  arrived at the same message and were indistinguishable.

### Build

- The Android target now runs the shared test suite (`withHostTest {}`), so 237
  common tests cover the target that ships the `aar` — previously they ran on
  jvm, iOS and wasm only. Robolectric backs an `androidHostTest` source set
  covering the Android decode-budget path. `SamplesSmokeTest` and
  `BarcodeDslTest` are excluded there: they build a real PDF through
  `android.graphics.pdf.PdfDocument`, which is native-backed and cannot run on
  a host JVM at all. Covering `AndroidPdfDriver` and `AndroidFontRegistry`
  needs an instrumented test on a device, which this repo has no CI job for.

## [1.2.0] — 2026-06-07

### Added — text engine (`pdfkmp`)

- **Full justification.** `TextAlign.Justify` now distributes inter-word
  slack on every line except the paragraph's last, in both `text` and
  `richText` (stretched space segments). Previously fell back to `Start`.
- **`maxLines` + `TextOverflow { Clip, Ellipsis }`** on `text { }` —
  clamp a paragraph and ellipsize the cut line.
- **Soft hyphens (U+00AD)** are invisible break opportunities; a real
  `-` renders only when a wrap lands on one.
- **Mid-word breaking.** Words wider than their slot now split into
  fitting chunks instead of overflowing horizontally.
- **Superscript / subscript** rich-text spans via
  `span("2") { script = TextScript.Superscript }` — auto-shrunk and
  baseline-shifted.
- **Right-to-left support.** `TextDirection { Auto, Ltr, Rtl }` on
  `TextStyle` (default `Auto` detects Hebrew/Arabic from content):
  `Start`/`End`/`Justify` anchor to the correct edge. The JVM backend
  additionally runs its own bidi reorder + Arabic contextual shaping
  (presentation forms, lam-alef ligatures) since PDFBox does neither;
  Android and iOS shape natively.
- **Orphan / widow control.** `minLinesBeforeBreak` / `minLinesAfterBreak`
  on `text { }` govern how `Slice` may split a paragraph.

### Added — layout & pagination (`pdfkmp`)

- **Recursive column slicing.** Undecorated `column { }`s now slice
  across pages child-by-child under `Slice` (previously moved whole).
- **Table row slicing with repeating headers.** Tables taller than a
  page split between rows; the header row repeats on every continuation
  page (`table(repeatHeader = true)`, the default).
- **`keepTogether { }`** — `break-inside: avoid` for any group.
- **`columns(count, gap)`** — newspaper-style multi-column flow with
  height balancing.
- **`grid(columns)`** — row-major equal-width cell grid.
- Over-wide fixed table columns now shrink proportionally to fit the
  page instead of spilling past the margin.
- Images/vectors taller than a full page scale down to fit under
  `MoveToNextPage` instead of overflowing the bottom margin.
- **Mixed orientations** documented + `PageSize.landscape` / `.portrait`
  helpers; `PageContext` gains `isFirst` / `isLast` / `isEven` / `isOdd`
  for book-style mirrored chrome.

### Added — graphics & content (`pdfkmp`)

- **QR codes** — `qrCode(data)`: full ISO 18004 Model 2 generator in
  pure common Kotlin (versions 1–40, EC L/M/Q/H, Reed-Solomon, all 8
  masks), rendered as crisp vector squares.
- **Code 128 barcodes** — `barcode(data)`: code sets B/C with automatic
  digit compression and mod-103 checksum, pure vector bars.
- **Charts** — `barChart`, `lineChart`, `pieChart`, `donutChart`
  extension DSL (pure vector, with legends and value captions).
- **`freeDraw { path { moveTo/lineTo/quadTo/cubicTo/rect/close } }`** —
  free-form vector drawing in a local coordinate space.
- **Drop shadows** — `card(dropShadow = DropShadow(...))`, vector-
  approximated blur.
- **Dashed / dotted borders** — `BorderStroke(style = LineStyle.Dashed)`
  on sharp-cornered containers and per-side borders.
- **Rotation & opacity** — `rotation` (degrees, about the centre) and
  `opacity` (group transparency) on `column` / `row` / `box` / `card`.
- **Full SVG file support.** The vector parser now handles `<rect>`
  (incl. rounded), `<circle>`, `<ellipse>`, `<line>`, `<polyline>`,
  `<polygon>`, nested `<g>` transforms, inline `style=""`, opacity
  attributes, `rgb()` and ~20 named colors, and viewBox offsets.
- **`image(altText = ...)`** — accessibility description carried to
  backends that write tagged structure.

### Added — navigation & document features (`pdfkmp`)

- **Bookmarks / outline** — `bookmark("Chapter 1", level = 0)` populates
  the reader's outline sidebar (all three platforms — Android via the
  new post-processor).
- **Internal links** — `anchor("id")` + `linkToAnchor(anchor = "id")`
  for clickable cross-references; forward references resolve at finish.
- **Automatic table of contents** — `tableOfContents()` expands into
  clickable rows (title, dotted leader, resolved page number) using a
  dry-run pagination pass; level filtering and indentation included.
- **Encryption** — `encryption { ownerPassword = ...; userPassword = ...;
  allowPrinting/allowCopying/allowModification }`; AES-256 on JVM,
  Core Graphics passwords on iOS (Android documented unsupported).
- **File attachments** — `attachment(name, bytes, mimeType)` embeds
  files on JVM (ZUGFeRD/Factur-X-style invoices).
- **AcroForm fields** — `textField(name, ...)` / `checkBox(name, ...)`:
  interactive on Desktop, consistent static visuals on Android/iOS.
- **PDF/A (best-effort) + tagged-PDF basics** on JVM — XMP identification,
  sRGB output intent, MarkInfo, image `/Alt` entries.
- **Digital signing** — `PdfSigner` (JVM) signs finished documents.
- **`PdfLog`** — opt-in diagnostics hook surfacing silently-handled
  conditions (undecodable images, font fallbacks).

### Added — Android backend parity (`pdfkmp`)

- **Metadata, clickable links, internal links, and the outline now work
  on Android.** `android.graphics.pdf.PdfDocument` exposes none of
  these, so `finish()` now post-processes the produced bytes with a
  pure-Kotlin PDF incremental update (`/Info` dictionary, `/Link`
  annotations with URI/GoTo actions, `/Outlines` tree). Defensive: any
  parse surprise returns the original bytes unchanged.

### Added — viewer (`pdfkmp-viewer`)

- **Print** — `showPrint` topbar action (Android `PrintManager`, iOS
  `UIPrintInteractionController`, Desktop `PrinterJob` + PDFBox).
- **Dark mode** — `invertColors` renders pages colour-inverted, cache-aware.
- **Search in external PDFs** — text-extraction fallback on iOS
  (PDFKit `findString`) and Desktop (PDFBox `PDFTextStripper`);
  PdfKmp-authored docs keep the fast textRuns path. Android external
  docs remain unsearchable (`PdfRenderer` has no text API).
- **Highlight annotations** — `showAnnotationTools` + drag-to-highlight
  overlay with `initialAnnotations` / `onAnnotationsChanged` for
  persistence (overlay-only; not written into the PDF bytes).
- **Clipboard** — `pdfViewerCopyToClipboard(text)` on all platforms;
  text selection confirmed working from common code on iOS.
- **Adaptive cache** — `Auto` strategy switches to a forward-biased
  window on 200+-page documents.

### Added — second feature wave

- **TrueType subsetting + embedding on the pure-Kotlin backend.** The
  web/wasm target now embeds `PdfFont.Custom` fonts and falls back to a
  subset of the bundled Inter for non-WinAnsi text — Cyrillic works in
  the browser out of the box. Full TTF parser/subsetter in common code
  (composite-glyph closure, fresh cmap, corrected checksums),
  CIDFontType2/Identity-H with a ToUnicode CMap (text extraction
  verified via PDFBox). Content streams are now **Flate-compressed**
  by a pure-Kotlin DEFLATE (LZ77 + fixed Huffman + Adler-32).
- **Table `colSpan` / `rowSpan`** — HTML-style cell merging with an
  occupancy grid: separators never cross a merged region, row heights
  grow to fit spanning content, and page slicing keeps a rowspan block
  atomic. Spans of 1 are bit-for-bit identical to the old layout.
- **EAN-13 / UPC-A and Data Matrix (ECC 200)** symbologies —
  `barcode(data, symbology = Ean13)` and `dataMatrix(data)`, both pure
  vector with known-vector tests. (PDF417 was evaluated and deliberately
  not shipped: its 2,787-entry cluster tables can't be reproduced
  reliably without the spec at hand — wrong tables would render
  unscannable symbols.)
- **Chart upgrades** — `stackedBarChart` with per-segment legends,
  multi-series `lineChart`, value grid lines and axis labels on bar and
  line charts.
- **Automatic hyphenation** — `hyphenation = Hyphenators.EnUs`: Liang's
  algorithm over a hand-verified reduced `hyphen.tex` pattern subset;
  breaks render the same visible `-` as soft hyphens and never override
  author-supplied ones.
- **Kashida justification** — `kashidaJustify = true` elongates justified
  RTL lines at cursive joining points with tatweel (U+0640) before
  widening word gaps, the way Arabic typography expects.
- **`PdfTools` (JVM)** — post-process existing PDFs: `merge`, `split`,
  `extractPages`, diagonal `addWatermarkText`, page `overlay`, plus
  `validatePdfABasics`. **Factur-X/ZUGFeRD**: build a MINIMUM-profile
  `FacturXInvoice` XML in common code and attach it with the right
  `AFRelationship` via `PdfTools.attachFacturX`.
- **Viewer** — highlights can now be **written into the PDF** on
  Desktop (real `Highlight` annotations; the download action exports
  the annotated bytes); **password-protected PDFs** open via
  `password = ...` with a typed `PdfViewerError` instead of a generic
  failure (Desktop + iOS; Android's `PdfRenderer` has no password API);
  **two-page book layout** via `pageLayout = PdfPageLayout.TwoPageBook`
  (cover alone, then verso/recto pairs).
- **Documentation site** — Material for MkDocs (20 hand-written pages)
  + the aggregated Dokka API reference under `/api`, deployed to GitHub
  Pages by a new `docs.yml` workflow. A 6-agent audit verified coverage
  page-by-page and caught four long-standing README accuracy bugs
  (`SavedPdf` fields, `save()` wrongly described as suspend, an
  imaginary iOS iCloud mapping, a non-compiling `keywords` snippet) —
  all fixed in both places.

### Added — Web (Kotlin/Wasm) target

- **`pdfkmp`, `pdfkmp-compose-resources`, and `pdfkmp-markdown` now ship
  a `wasmJs` target** — PdfKmp runs in the browser. Browsers expose no
  PDF engine to Wasm, so a new pure-Kotlin PDF 1.7 writer
  (`kmpwriter`, common code) backs the target: Standard-14 Helvetica
  text (WinAnsi/Latin), every vector feature (shapes, gradients, QR,
  barcodes, charts, freeDraw, rotation/opacity), JPEG and 8-bit
  RGB/gray PNG embedding without decoding, links, named destinations,
  outline, and the info dictionary. The identical writer is validated
  on the JVM by re-parsing and rasterising its output with PDFBox, and
  the full common test suite (176 tests) also runs on actual Wasm
  under Node.
- **`PdfDocument.openInNewTab()`** (web) — hands the bytes to the
  browser's own PDF viewer via a Blob URL; `save(...)` becomes a
  browser download. No embedded web viewer is planned — browsers ship
  better ones than a page could.
- Not yet on the web backend (warned via `PdfLog`, skipped): custom
  font embedding (falls back to Helvetica), non-WinAnsi glyphs,
  palette/alpha PNGs, interactive form widgets, encryption,
  attachments.

### Added — ecosystem

- **New module `pdfkmp-markdown`** (`io.github.conamobiledev:pdfkmp-markdown`)
  — renders a CommonMark-lite subset (headings, emphasis, code, lists,
  tables, blockquotes, links, rules) through the PdfKmp DSL via
  `markdown(text, theme)`.
- **CI test workflow** (`test.yml`) — `jvmTest` on Ubuntu + the iOS
  Simulator suites and full assemble on macOS for every push/PR.
- **Desktop playground** — a live-preview screen in `sample-desktop`
  that rebuilds a real `pdf { }` document as you tweak controls.
- New samples: `textAdvanced`, `longTable`, `barcodes`, `designExtras`,
  `navigation`, `newsletter`, `pageTemplates`.

### Fixed

- iOS outline entries now reference their 1-based page number instead of a
  synthetic named destination — `CGPDFContextSetOutline` rejects name
  strings (and empty `Children` arrays), crashing document generation with
  an `NSInvalidArgumentException` whenever `bookmark()` was used.
- `TextAlign.Justify` no longer silently falls back to start alignment.
- Decorated containers no longer get sliced mid-decoration (they move
  whole, by design).
- Stale documentation: image slicing TODO, "Justify falls back" sample
  copy, inline fully-qualified names in the renderer.
- Adversarial-review pass over the wave itself (11 fixes):
  `tableOfContents()` / `bookmark()` now work inside `columns { }` and
  `keepTogether { }` (previously crashed / silently dropped); a sliced
  table with `repeatHeader = false` no longer loses its header when it
  starts low on a page; split tables drop their corner radius instead of
  re-rounding every fragment; multiline form-field fallbacks draw each
  line instead of stacking them on one baseline; rich-text justification
  reaches the right margin exactly (trailing invisible spaces no longer
  count); blank rich-text lines take their owning span's height; a
  double-space can no longer overflow a line; and the Android
  post-processor writes byte-exact xref offsets, keeps the sign of
  near-zero negative coordinates, and never leaks object numbers for
  unresolved anchors.

## [1.1.1] — 2026-05-30

### Fixed — `pdfkmp-viewer`

- **iOS topbar action icons no longer shrink or disappear when the
  title is long.** The Classic iOS topbar centered the filename in an
  unweighted slot, so a long title greedily consumed the whole bar and
  collapsed the side columns to zero width — the share icon vanished
  and the rest shrank. The bar now uses a custom three-slot layout that
  measures the trailing icons (and back affordance) at their natural
  size first and hands the title only the symmetric gutter that remains,
  so the icons are inviolable and the title stays optically centered.

### Added — `pdfkmp-viewer`

- **`PdfTopBarTitleOverflow { Ellipsis, Marquee }`** — choose how a
  long topbar title behaves. `Ellipsis` (default) truncates with `…`
  like Android; `Marquee` scrolls the title horizontally when it
  overflows. Exposed as a `titleOverflow` parameter on `KmpPdfViewer`,
  `PdfViewerTopBar`, `PdfViewerTopBarClassicIos`, and
  `PdfViewerTopBarMinimalMono`; applies on Android / Desktop as well.

## [1.1.0] — 2026-05-29

### Added — Desktop / JVM support (all three modules)

- **New `jvm` target on `pdfkmp`, `pdfkmp-viewer`, and
  `pdfkmp-compose-resources`.** PdfKmp now runs on Desktop (JVM) —
  macOS, Windows, and Linux — in addition to Android and iOS. The
  public generation API (`pdf { … }`, `pdfAsync { … }`,
  `document.save(...)`, `toByteArray()`) is unchanged and identical
  across every platform. Published as `pdfkmp-jvm`,
  `pdfkmp-viewer-jvm`, and `pdfkmp-compose-resources-jvm`; KMP
  consumers with a `jvm()` target get the right variant automatically.
- **Desktop PDF backend on Apache PDFBox.** A new `PdfDriver` /
  `PdfCanvas` / `FontMetrics` implementation renders the full DSL —
  vector text (subset-embedded TrueType fonts), shapes, rounded
  rects, dashed/dotted lines, clipping, linear & radial gradients
  (axial / radial PDF shadings), and images — to a real vector PDF.
  PDFBox is pure-Java, so there are no native libraries to bundle and
  the same artifact runs on every desktop OS. The dependency is scoped
  to the `jvm` source set only; Android and iOS keep their native
  backends and never pull it in.
- **Desktop wins over Android in two places:** the document **info
  dictionary** (title / author / subject / keywords) is written
  (Android's `PdfDocument` can't), and **hyperlinks** become real
  clickable `PDAnnotationLink` annotations.
- **`pdfkmp-viewer` on Desktop.** `KmpPdfViewer` and the imperative
  `KmpPdfLauncher` work on Desktop — pages rasterise through PDFBox's
  `PDFRenderer`, the launcher hosts the viewer in a Compose for
  Desktop window, and hyperlinks open in the default browser.
- **Desktop-native viewer zoom & actions.** Zoom on Desktop works via:
  a **macOS trackpad pinch** (wired through Apple's
  `com.apple.eawt.event.MagnificationListener` — reflection-only, no
  native code; the internal package is opened at runtime so **no launch
  flag is required** — works from an IDE run, Gradle, or a packaged app),
  **Ctrl/⌘ + mouse-wheel** anchored under the
  cursor, **double-click** to toggle, and an optional on-screen **＋ / −
  pill** (`showZoomControls` flag, default `true`, Desktop-only —
  the cross-OS fallback, since Windows/Linux trackpads don't deliver a
  pinch gesture to the toolkit). Both the cursor-anchored Ctrl-scroll
  and pinch keep the focal point under the pointer (the inter-page gap
  scales with zoom so the anchor stays exact on lower pages). The
  **download** button opens a native **Save As** dialog
  (`java.awt.FileDialog`, defaulting to `~/Downloads`), and **share**
  opens the PDF in the OS default handler. Touch platforms are
  unchanged — pinch-to-zoom stays their path and the pill is hidden.
- **Sharper text on Desktop.** Two changes: the baseline `renderDensity`
  now defaults to **3× on Desktop** (vs 2× on Android/iOS) for a crisper
  view before any zoom; and the zoom re-render density tracks the full
  zoom factor on Desktop (up to `maxZoom`, ≤ ~720 DPI) so text keeps
  sharpening the deeper you zoom, instead of stopping at 2×. Override
  `renderDensity` explicitly for finer control.
- **`:sample-desktop`** Compose-for-Desktop app: a master list of every
  bundled `Samples.*` document; click one to open it in `KmpPdfViewer`.
  Run with `./gradlew :sample-desktop:run`.

### Changed

- **Compose Multiplatform `1.10.3` → `1.11.0`** and **`kotlinx.coroutines`
  `1.10.2` → `1.11.0`.**
- **Dropped the `iosX64` (Intel-Mac simulator) target** from all modules.
  Compose Multiplatform 1.11.0 removed `iosX64` / `macosX64` from its own
  artifacts (the Compose-dependent modules can no longer publish it), and
  Intel Macs are end-of-life. iOS is now `iosArm64` (device) +
  `iosSimulatorArm64` (Apple-Silicon simulator).
- Moved the iOS-only `BrochurePdfDump` screenshot utility from
  `commonTest` to `iosTest` (it used Foundation APIs that don't exist
  on the new JVM/host test source set). No public API impact.

> **API compatibility.** Every existing Android / iOS / source-level API,
> signature, and behaviour is preserved — Desktop is purely additive. The
> **one** non-additive change is the removal of the `iosX64` *artifact*:
> projects still building for the Intel-Mac simulator must switch to an
> Apple-Silicon simulator (`iosSimulatorArm64`). No source changes are
> needed for `iosArm64` / `iosSimulatorArm64` consumers.

## [1.0.2] — 2026-05-25

### Fixed — `pdfkmp`

- **iOS rounded-rect crash on real devices.** Containers with a
  `cornerRadius` larger than half of the measured rect (e.g. a pill-
  style badge with `cornerRadius = 100.dp` on a 40 pt-wide box) now
  render correctly on iOS hardware. Earlier builds passed the radius
  straight to `CGPathAddRoundedRect`, which asserts
  `2 × cornerWidth ≤ rect.width` on real devices but silently
  tolerated the violation in some simulators — masking the bug
  during local testing. The iOS canvas now clamps the radius to
  `min(width, height) / 2` across `drawRoundedRect` /
  `strokeRoundedRect` / `clipRoundedRect`, matching the existing
  clamping in `buildRoundedRectPath` (per-corner path) and Android's
  `Canvas.drawRoundRect`, so cross-platform output stays identical
  and "pill" radii collapse to a fully-rounded ellipse instead of
  crashing.

### Added — `pdfkmp-viewer`

- **`showTopBar: Boolean`** master switch on `KmpPdfViewer` and
  `KmpPdfLauncher.open`. `false` hides both the topbar and the
  morphed search bar, leaving a "poor viewer" surface with just
  pages, indicator, and gestures — for hosts that wire their own
  navigation and share affordances.
- **`PdfPageCacheStrategy` + memory-budgeted bitmap LRU.** Pages
  rendered while scrolling are now retained in a per-document
  bitmap cache so scrolling back to a previously visited page is
  an instant memory hit rather than a fresh rasterisation. Three
  presets — `Auto` (default — modest prefetch window, RAM-bounded),
  `Window(pagesBefore, pagesAfter)` (explicit warm window), and
  `All` (try to keep every page warm, still RAM-bounded). The cache
  is always capped to a per-platform memory budget (Android — 25 %
  of `Runtime.maxMemory()`, iOS — 200 MB) and evicts the oldest
  entries first, so over-eager windows can never crash the process.
  Wider prefetch is best-effort: the cache keeps as much as fits.
- **Unified `PdfSource` shape**: in addition to the existing `Bytes`
  / `Document` variants, the sealed type now carries
  `FilePath` / `Remote(url, headers, timeoutMillis)` / `ContentUri`
  (Android-only) / `Asset` (resolved through `Context.assets` on
  Android, `NSBundle` on iOS), plus a `PdfSource.auto(uri)` factory
  for callers that only have an opaque string. `KmpPdfViewer` and
  `KmpPdfLauncher` now resolve any variant through a single async
  loader instead of branching on URI prefixes inside an opaque
  `String`. `Remote.headers` / `timeoutMillis` are honoured on
  Android; the iOS resolver falls back to `NSData(contentsOfURL:)`
  and ignores both (documented).

### Deprecated — `pdfkmp-viewer`

- **`KmpPdfViewer(uri: String, …)`** — replaced by
  `KmpPdfViewer(source = PdfSource.auto(uri), …)` (or the matching
  explicit variant). String inputs hide which transport is in use
  and can't carry per-shape configuration like HTTP headers.

## [1.0.0] — 2026-05-04

### Added — `pdfkmp-viewer` (new optional module)

Compose Multiplatform PDF viewer screen for Android and iOS. Drop one
composable into your nav graph (or fire one imperative call from any
scope) and you get a complete reader with topbar, search, share,
save-to-Downloads, hyperlinks, gestures, and a page indicator.

- **`@Composable KmpPdfViewer(uri / document / bytes / source, …)`**
  — top-level all-in-one composable. Four overloads cover every
  realistic input shape. The URI overload loads bytes asynchronously
  via `loadPdfBytesFromUri` (supports `content://`, `file://`,
  `http(s)://`, asset / bundle paths, bare filesystem paths).
- **`KmpPdfLauncher.open(uri / document / bytes, …)`** — imperative
  counterpart for non-composable scopes (click handlers,
  `LaunchedEffect`, suspend funcs, notification taps). Hosts
  `KmpPdfViewer` in an internal Activity (Android) or
  `ComposeUIViewController` (iOS). Document payload survives the
  hop via a process-local registry so text selection / hyperlinks
  stay alive across the launch boundary.
- **Topbar** — platform-aware default via `PdfViewerTopBar` (expect /
  actual): `PdfViewerTopBarMinimalMono` on Android (38×38 chips,
  primary black download chip), `PdfViewerTopBarClassicIos` on iOS
  (chevron + back-label + 17pt iOS-blue trailing icons).
- **In-document search** via `searchPdfText` + `PdfSearchBar` morph.
  Substring scan over captured runs, prev / next chevrons, match
  counter, auto-scroll to active match, translucent yellow
  highlights with a stronger fill on the active hit.
- **Text selection** — invisible `SelectionContainer` overlay backed
  by captured glyph positions; long-press → drag handles → Copy.
  Only available for documents authored through the PdfKmp DSL —
  external bytes carry no position metadata.
- **Hyperlinks** — `link(url) { … }` blocks produce real clickable
  hotspots. Tap routes through `rememberPdfUrlLauncher`
  (`Intent.ACTION_VIEW` / `UIApplication.openURL`).
- **Share** — `rememberPdfShareAction()` (public). Android:
  `Intent.ACTION_SEND` via `FileProvider`. iOS:
  `UIActivityViewController`.
- **Save** — `rememberPdfSaveAction()` (public). Android:
  `MediaStore.Downloads` (API 29+) or `Environment.DIRECTORY_DOWNLOADS`
  with a "Saved to Downloads" Toast. iOS: `NSDocumentDirectory` with
  a system alert.
- **Gestures** — pinch zoom 1×–5× with focal-point anchoring on both
  axes, single-finger pan when zoomed, two-finger pan, double-tap
  toggle, free 2D pan during pinch (Compose's nested-scrollable
  axis-lock bypassed via `PointerEventPass.Initial`).
- **Page indicator chip** — auto-fades pill with tabular-nums,
  switches to the next page once it crosses the viewport midpoint
  (rather than waiting for the previous page to fully scroll off).
- **Behaviour toggles** — `zoomEnabled`, `doubleTapToZoom`,
  `textSelectable`, `hyperlinksEnabled`, `showSearch` / `showShare`
  / `showDownload` / `showBack` / `showPageIndicator`. Every
  affordance can be hidden without un-wiring the callback.
- **Lower-level building blocks** stay public: `PdfViewer`,
  `PdfViewerTopBar`, `PdfSearchBar`, `PdfShareFab`, `PdfSaveFab`,
  `PdfShareIcon`, `PdfSaveIcon`, the `remember…Action` factories,
  `searchPdfText`. `KmpPdfViewer` is the opinionated default; the
  building blocks are for custom topbars / multi-FAB layouts /
  bottom-sheet share / etc.

### Added — `pdfkmp` (generator)

- **`PdfDocument.textRuns`** + **`PdfDocument.hyperlinks`** —
  `RecordingPdfDriver` snapshots every `drawText` / `linkAnnotation`
  during render so consumers (most notably `:pdfkmp-viewer`) can
  layer selection / clickable overlays without re-parsing the
  encoded bytes.
- New public types `PdfTextRun` and `PdfHyperlink` (PDF-points,
  top-left origin, zero-based page index).
- `pdf { }` and `pdfAsync { }` both wrap the platform driver in the
  recorder; output bytes are byte-for-byte identical to a
  non-recording render.

### Added — sample app (`:sample`)

- Categorised list (Getting started / Typography / Layout / Tables /
  Vector graphics / Images / Long documents / Showcase) with
  per-entry descriptions.
- "Two ways to open the viewer" hint banner: tap → `KmpPdfViewer`
  composable, long-press → `KmpPdfLauncher.open` imperative — both
  exercise the same screen so a developer can compare the
  navigation models hands-on.
- `iosApp/iosApp/ContentView.swift` rewritten to match the Classic
  iOS Native handoff: custom flat topbar (bypasses iOS 26's Liquid
  Glass capsule wrapping), search via `.searchable`, share via
  `ShareLink`, save via `NSDocumentDirectory`, prev / next match
  navigation in a bottom safe-area inset.

### Documentation

- `pdfkmp-viewer/README.md` — full module reference covering install,
  the all-in-one + lower-level APIs, every parameter, the design
  handoff direction the platform variants implement, and known
  limitations.
- Main `README.md` gains a "PDF viewer" section listing
  `pdfkmp-viewer` as the second optional companion alongside
  `pdfkmp-compose-resources`. "What to do after save" now points at
  the viewer first and frames the manual `PdfRenderer` / PDFKit
  recipes as the "I want a custom UI" escape hatch.

### Compatibility

- No breaking changes to `:pdfkmp` public API. `PdfDocument`'s
  primary constructor stays internal; the new `textRuns` /
  `hyperlinks` fields default to empty so manually-constructed
  documents (in tests, etc.) compile unchanged.
- Minimum Compose Multiplatform 1.10. Material 3 1.2+ on Android
  for the new surface tones.
- iOS 17+ for the SwiftUI sample (`ShareLink`, `.searchable`).
  The library targets work on iOS 13+.

### Internal

- `RecordingPdfDriver` (commonMain) wraps any `PdfDriver`.
- `KmpPdfLauncherRegistry` (commonMain) holds non-primitive payloads
  across the imperative-launcher hop.
- AndroidX App Startup `ViewerContextInitializer` captures the
  application context for the launcher's `startActivity` and the
  share / save / URL launchers.
- `:pdfkmp-viewer` ships its own `AndroidManifest.xml` with the
  hosted activity declaration; consumers don't need to register
  anything.

---

## [0.2.0-alpha01] — 2026-04

Initial 0.2.0 line — see git log for the per-commit details.

## [0.1.x]

See git log; pre-public surface stabilisation.
