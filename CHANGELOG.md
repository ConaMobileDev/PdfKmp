# Changelog

All notable changes to this project are documented here. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/) and
versions follow [Semantic Versioning](https://semver.org). Pre-1.0
minor versions may break public API; alpha / beta / rc tags signal
an actively settling surface.

## [0.2.0-rc01] — 2026-05-02

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
