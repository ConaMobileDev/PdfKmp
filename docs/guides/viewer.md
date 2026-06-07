# PDF viewer (Compose)

The optional `pdfkmp-viewer` module ships a complete Compose Multiplatform PDF
viewer screen — a polished topbar, in-document search, share, save-to-Downloads,
print, hyperlink launcher, page indicator, and gesture-driven zoom / pan / text
selection. It runs on Android (`PdfRenderer`), iOS (`PDFKit`), and Desktop
(PdfBox `PDFRenderer`).

!!! note "Generate-only? Skip this."
    Add the viewer only when you want a unified, on-brand viewing experience
    inside your app. If you only generate PDFs and hand them to the system reader,
    the per-platform recipes in [Getting started](../getting-started.md) stay
    valid.

Install: see [Getting started](../getting-started.md). In short, add
`io.github.conamobiledev:pdfkmp-viewer` alongside `pdfkmp` in `commonMain`. The
viewer pulls in Compose Multiplatform. It is **not published for web**.

## Two entry points

### `KmpPdfViewer(...)` — composable

For Compose navigation graphs (NavHost / Voyager / Decompose). Integrates with
the host back stack and theming.

```kotlin
import com.conamobile.pdfkmp.viewer.KmpPdfViewer
import com.conamobile.pdfkmp.viewer.PdfSource

@Composable
fun InvoiceDetail(navController: NavController) {
    KmpPdfViewer(
        source = PdfSource.auto("https://example.com/invoice.pdf"),
        title = "Invoice 2026 Q1",
        onBack = { navController.popBackStack() },
    )
}

// PdfKmp DSL document — text selection + clickable hyperlinks light up automatically
KmpPdfViewer(
    document = document,
    title = "Hello",
    fileName = "hello.pdf",
    onBack = { navController.popBackStack() },
)
```

!!! warning "The bare-`String` composable overload is deprecated"
    `KmpPdfViewer(uri = "…")` is deprecated (since 1.0.2) — a string hides which
    transport is used and can't carry headers / timeouts. Wrap the URI in
    `PdfSource.auto(uri)` as above. (`KmpPdfLauncher.open(uri = …)` is **not**
    deprecated — the imperative launcher example below is fine.)

### `KmpPdfLauncher.open(...)` — imperative

Fire-and-forget launches from any scope: click handlers, `LaunchedEffect`,
suspend functions, notification taps. Hosts the viewer in an Activity (Android) /
`UIViewController` (iOS) / Compose for Desktop window (JVM).

```kotlin
import com.conamobile.pdfkmp.viewer.KmpPdfLauncher

KmpPdfLauncher.open(pdf, title = "Invoice")
KmpPdfLauncher.open(uri = "content://com.example.docs/123", title = "Document", fileName = "document.pdf")
```

## Input shapes

| Overload | Use when | Selection / hyperlinks |
|---|---|---|
| `source: PdfSource` (composable) | wrap any input — `PdfSource.auto(uri)` for `content://` / `file://` / `http(s)://` / asset paths, or a `PdfSource.Document(bytes, runs, links)` you built | **enabled** when `Document` |
| `document: PdfDocument` | from PdfKmp's `pdf { }` DSL | **enabled** |
| `bytes: ByteArray` | raw `%PDF-…` from disk / network / file picker | **disabled** |
| `uri: String` (composable) | **deprecated** — prefer `source = PdfSource.auto(uri)`. Still fine on `KmpPdfLauncher.open(uri = …)`. | **disabled** — opaque bytes |

## Features

- **Topbar** — Minimal Mono on Android & Desktop, Classic iOS Native on iOS,
  picked per platform via expect/actual. Long titles ellipsize by default; pass
  `titleOverflow = PdfTopBarTitleOverflow.Marquee` to scroll instead.
- **Search** — inline morphing search field; matches highlight in the page,
  chevrons cycle results, auto-scroll to the active hit.
- **Share** — `Intent.ACTION_SEND` (Android, via `FileProvider`) /
  `UIActivityViewController` (iOS) / `java.awt.Desktop.open` (Desktop).
- **Save** — `MediaStore.Downloads` (Android) / `NSDocumentDirectory` (iOS) / a
  native **Save As** dialog (Desktop, default `~/Downloads`).
- **Print** — `showPrint` action: `PrintManager` (Android),
  `UIPrintInteractionController` (iOS), `PrinterJob` + PDFBox (Desktop).
- **Dark mode** — `invertColors` renders pages colour-inverted (bitmaps only; the
  share / save / print bytes are untouched).
- **Hyperlinks** — `link(url) { … }` blocks become real clickable hotspots; tap
  opens the system browser.
- **Text selection** — invisible `SelectionContainer` overlay backed by captured
  glyph positions; long-press → drag handles → Copy. Works on Android, iOS, and
  Desktop. `pdfViewerCopyToClipboard(text)` writes straight to the pasteboard.
- **Gestures** — pinch zoom (1× → 5×), pan when zoomed, double-tap toggle,
  focal-point anchoring. Desktop adds macOS trackpad pinch, Ctrl/⌘ + mouse-wheel,
  and optional on-screen ＋ / − controls (`showZoomControls`).
- **Page indicator** — auto-fading `n / total` pill.
- **Annotations** — opt-in `showAnnotationTools` highlighter; drag to highlight,
  tap to delete. State surfaced via `initialAnnotations` / `onAnnotationsChanged`.
  On **Desktop** the highlights can be **written into the PDF** as real `Highlight`
  annotations — see [Annotation export](#annotation-export-to-pdf-desktop).
- **Page layout** — `pageLayout = PdfPageLayout.TwoPageBook` arranges pages as
  side-by-side book spreads (cover alone, then verso/recto pairs); the default
  `PdfPageLayout.Single` is one page per row.
- **Password-protected PDFs** — pass `password = "…"` to open an encrypted
  document; a missing / wrong password shows an inline message and fires
  `onDocumentError(PdfViewerError.PasswordRequired)` instead of crashing.

Each affordance has a Boolean (`showSearch` / `showShare` / `showDownload` /
`showPrint` / `showBack` / `showPageIndicator`) and behaviour toggles
(`zoomEnabled`, `doubleTapToZoom`, `textSelectable`, `hyperlinksEnabled`) — flip
to `false` to hide without un-wiring callbacks.

## Annotation export to PDF (Desktop)

By default highlights are overlay-only — UI state, not written into the bytes. On
**Desktop (JVM)**, where PdfBox exposes a writable PDF model, the highlights are
burned into the document as real `Highlight` (text-markup) annotations: while the
annotation tools are active, the **download** action exports the *annotated* bytes,
so the highlights open in any standard PDF reader. Android and iOS stay
overlay-only — `android.graphics.pdf.PdfRenderer` is read-only, and the iOS
PDFKit-write path is deferred — so on those platforms share / save / print export
the untouched original.

## Password-protected documents

```kotlin
KmpPdfViewer(
    source = PdfSource.auto("file:///path/to/encrypted.pdf"),
    password = "letmein",
    onDocumentError = { error ->
        if (error == PdfViewerError.PasswordRequired) promptForPassword()
    },
)
```

`PdfViewerError` distinguishes `PasswordRequired`, `CannotOpen`, and `LoadFailed`,
so the host can re-prompt for a password rather than just reporting a broken file.

!!! warning "Android can't open encrypted PDFs"
    `password` works on **Desktop** (the right password reopens the document) and
    **iOS**. On **Android** it is terminal regardless of the password —
    `android.graphics.pdf.PdfRenderer` has no password API and cannot open
    encrypted files at all, so the viewer fires `PasswordRequired` and shows the
    inline message.

## Two-page book layout

```kotlin
KmpPdfViewer(document = doc, pageLayout = PdfPageLayout.TwoPageBook)
```

The cover (page 1) sits alone on the first row; subsequent pages are paired
verso/recto (`2-3`, `4-5`, …) and a trailing odd page sits alone — matching a
printed book. Best on Desktop / tablet. Both layouts share the same zoom / pan
model, search highlights, and annotations.

## Search support matrix

Two engines feed search, picked automatically. PdfKmp-authored documents search
their captured text runs; external PDFs fall back to the platform text engine.

| Document source | Android | iOS | Desktop |
|---|---|---|---|
| PdfKmp `pdf { … }` (`PdfSource.Document`) | ✅ | ✅ | ✅ |
| External bytes / file / URL / asset | ❌ (no PDF text API) | ✅ (PDFKit `findString`) | ✅ (PdfBox `PDFTextStripper`) |

## Cache strategies

`cacheStrategy = PdfPageCacheStrategy.Auto` (default) is adaptive: documents up to
~200 pages keep a symmetric prefetch window (3 pages either side); past that the
window tightens to a forward-biased `(before = 2, after = 4)`. Override with
`Window(before, after)` or `All`. A hard per-platform byte budget (Android: 25% of
`maxMemory()`, iOS: 200 MB, Desktop: 25% of the JVM heap / 256 MB when the heap is
unbounded) always caps total cache size and evicts least-recently-used pages
first, so a wide window can never crash the process.

## Lower-level building blocks

When the all-in-one shape doesn't fit (custom topbar, multi-FAB layouts,
bottom-sheet share), the underlying composables stay public: `PdfViewer(...)`,
`PdfViewerTopBar(...)` / `…MinimalMono` / `…ClassicIos`, `PdfSearchBar(...)`,
`PdfShareFab` / `PdfSaveFab`, `rememberPdfShareAction()` /
`rememberPdfSaveAction()` / `rememberPdfPrintAction()` / `rememberPdfUrlLauncher()`,
and `searchPdfText(textRuns, query)`.

## Limitations

!!! warning "Selection / hyperlinks only on PdfKmp-built documents"
    External PDFs (network, file picker) are bitmap-only for selection and
    hyperlinks because the bytes carry no text-position metadata. Search, however,
    works on external PDFs on iOS and Desktop (not Android).

!!! warning "Highlight annotations: overlay-only on Android / iOS"
    On **Android and iOS** highlights live in the viewer's UI state and are **not**
    written into the PDF bytes — share / save / print export the untouched
    original, so a highlight won't appear in another reader. Persist the
    `PdfViewerAnnotation` list via `onAnnotationsChanged` and restore through
    `initialAnnotations`. On **Desktop** the download action exports the annotated
    bytes (see [Annotation export](#annotation-export-to-pdf-desktop)).

- **iOS Liquid Glass** — the library's iOS topbar uses a custom flat shell to
  match the handoff. Apps wanting the Liquid Glass look can drop down to
  `PdfViewer` and host their own SwiftUI navigation bar.
- **Compose-only** — no legacy View / UIKit-only entry point.

## See also

- [Getting started](../getting-started.md) — install + manual share recipes.
- [Links, bookmarks & TOC](navigation.md) — the hyperlinks the viewer surfaces.
