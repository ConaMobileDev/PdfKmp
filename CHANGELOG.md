# Changelog

All notable changes to this project are documented here. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/) and
versions follow [Semantic Versioning](https://semver.org). Pre-1.0
minor versions may break public API; alpha / beta / rc tags signal
an actively settling surface.

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
- **Desktop-native viewer gestures & actions.** The viewer adapts to
  mouse/trackpad input: **Ctrl + scroll wheel** (⌘ + scroll on macOS,
  or a Ctrl-held two-finger trackpad scroll) zooms anchored under the
  cursor, while a plain wheel scrolls pages and double-click toggles
  zoom. The **download** button opens a native **Save As** dialog
  (`java.awt.FileDialog`, defaulting to `~/Downloads`) so the user
  chooses where the file lands, and **share** opens the PDF in the OS
  default handler. Touch platforms are unchanged — pinch-to-zoom stays
  their path.
- **`:sample-desktop`** Compose-for-Desktop app: a master list of every
  bundled `Samples.*` document; click one to open it in `KmpPdfViewer`.
  Run with `./gradlew :sample-desktop:run`.

### Changed

- **`kotlinx.coroutines` 1.10.2 → 1.11.0.**
- Moved the iOS-only `BrochurePdfDump` screenshot utility from
  `commonTest` to `iosTest` (it used Foundation APIs that don't exist
  on the new JVM/host test source set). No public API impact.

> **No breaking changes.** Every existing Android and iOS API,
> signature, and behaviour is preserved; Desktop is purely additive.

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
