# pdfkmp-viewer

Compose Multiplatform PDF viewer with **document-wide pinch zoom**, **text selection** (Android / iOS / Desktop), **hyperlinks**, **search**, **highlight annotations** (with Desktop export into the PDF), **password-protected documents** (Desktop / iOS), **two-page book mode**, **share**, and **save to Downloads** — works on Android (`android.graphics.pdf.PdfRenderer`), iOS (`PDFKit`), and Desktop (PdfBox).

> **Looking for the document generator?** That lives in the sibling [`:pdfkmp`](../pdfkmp) module. This module renders any PDF — but text selection and hyperlinks only light up for documents authored through the PdfKmp DSL.

```
┌──────────────────────────────────────────────────┐
│ ⬅  Sample title                       ⬇   ⤴      │   ← topbar (your app)
├──────────────────────────────────────────────────┤
│                                                  │
│        [ rasterised PDF page, fits width ]       │   ← PdfViewer
│                                                  │
│        long-press → text selection ┄┄┄┄          │
│        tap link    → opens browser  ┄┄┄          │
│                                                  │
│                    ╭──────╮                      │
│                    │ 1/12 │  (auto-fades)        │
│                    ╰──────╯                      │
└──────────────────────────────────────────────────┘
```

## Install

Available on Maven Central as `io.github.conamobiledev:pdfkmp-viewer`.

```kotlin
// libs.versions.toml
[versions]
pdfkmp = "1.2.0"

[libraries]
pdfkmp = { module = "io.github.conamobiledev:pdfkmp", version.ref = "pdfkmp" }
pdfkmp-viewer = { module = "io.github.conamobiledev:pdfkmp-viewer", version.ref = "pdfkmp" }
```

```kotlin
// build.gradle.kts (KMP module)
sourceSets {
    commonMain.dependencies {
        implementation(libs.pdfkmp)          // generator (DSL)
        implementation(libs.pdfkmp.viewer)   // this module — viewer + selection
    }
}
```

The viewer pulls in Compose Multiplatform 1.10+ (`compose.foundation`, `compose.material3`, `compose.ui`). On Android it requires Compose Material 3 1.2+ for surface tones.

## Quick start

The library ships **two paired entry points** so you can open the viewer from anywhere — Compose UI tree *or* arbitrary imperative code.

### `KmpPdfViewer(...)` — Composable (idiomatic Compose)

Drop into your navigation graph or any `@Composable` scope. Owns the topbar, search bar morph, share / save / hyperlink launchers, page indicator, and gesture model.

```kotlin
@Composable
fun MyScreen() {
    KmpPdfViewer(
        uri = "https://example.com/invoice.pdf",
        title = "Invoice 2026 Q1",
        onBack = { navController.popBackStack() },
    )
}

// PdfKmp DSL document — text selection + hyperlinks light up automatically
val document = remember {
    pdf {
        page {
            text("Hello, world!") { fontSize = 18.sp; bold = true }
            link(url = "https://example.com") {
                text("Visit our site") { fontSize = 14.sp; color = PdfColor.Blue }
            }
        }
    }
}
KmpPdfViewer(
    document = document,
    title = "Hello",
    fileName = "hello.pdf",
    onBack = { navController.popBackStack() },
)
```

### `KmpPdfLauncher.open(...)` — Imperative (call from anywhere)

For fire-and-forget launches outside a `@Composable` scope: click handlers, `LaunchedEffect`, suspend functions, notification taps, etc. The launcher hosts `KmpPdfViewer` inside an Activity (Android) / `UIViewController` (iOS) and dismisses on back.

```kotlin
Button(onClick = {
    scope.launch {
        val pdf = pdfAsync { … build PDF … }
        KmpPdfLauncher.open(pdf, title = "Invoice")
    }
})

// URI directly — bytes fetched on a background dispatcher
KmpPdfLauncher.open(
    uri = "content://com.example.docs/123",
    title = "Document",
    fileName = "document.pdf",
)
```

Both APIs accept four input shapes:

| Overload | Use when | Selection / hyperlinks |
|---|---|---|
| `KmpPdfViewer(uri / KmpPdfLauncher.open(uri, …)` | `content://`, `file://`, `http(s)://`, asset / bundle paths | **disabled** — opaque bytes |
| `KmpPdfViewer(document / KmpPdfLauncher.open(document, …)` | from PdfKmp's `pdf { }` DSL | **enabled** |
| `KmpPdfViewer(bytes / KmpPdfLauncher.open(bytes, …)` | raw `%PDF-…` from disk / network / file picker | **disabled** |
| `KmpPdfViewer(source = …)` (composable only) | you constructed a `PdfSource.Document(bytes, runs, links)` yourself | **enabled** when `source` is `Document` |

> **When to prefer which** — composable form integrates with the host's back stack and theming directly (no Activity / `presentViewController` ceremony) and is the right default for Compose-based navigation. The imperative form is for code that doesn't have a composable surface to mount the viewer into — modal launches from worker coroutines, click handlers in legacy View hierarchies, etc.

> Need finer control (custom topbar, multi-FAB layouts, bottom-sheet share)? The lower-level composables — [`PdfViewer`](#public-api-surface), `PdfViewerTopBar`, `PdfSearchBar`, plus the `rememberPdfShareAction` / `rememberPdfSaveAction` / `rememberPdfUrlLauncher` action factories — stay public.

## Features

### Document-wide pinch zoom

Pinch anywhere on the document to scale all pages together. Gesture model:

- **Pinch** — zooms the document between `1×` and `maxZoom` (default `5×`). The pinch focal point stays anchored under the user's fingers.
- **Single-finger drag at zoom = 1** — vertical scroll between pages, native fling.
- **Single-finger drag at zoom > 1** — free 2D pan of the zoomed document.
- **Two-finger drag** — always pans the document.
- **Double tap** — toggles `1×` ↔ `2.5×` with a smooth animation.

Sharpness is preserved automatically. Each visible page re-rasterises at `renderDensity × stableZoom` (capped at `2× renderDensity`) once the user has stopped pinching, so text stays crisp at any zoom level without paying the memory cost during the gesture itself.

```kotlin
PdfViewer(document = doc, maxZoom = 4f)               // cap zoom at 4×
PdfViewer(document = doc, zoomEnabled = false)        // no pinch, no double-tap
PdfViewer(document = doc, doubleTapToZoom = false)    // pinch only
```

### Text selection

Long-press text to select, drag the handles to extend, hit **Copy** in the system menu — exactly like reading a PDF in Apple Books or Samsung Notes. Works on **Android, iOS, and Desktop** alike.

How it works: during rendering, the library captures every laid-out text run with its position (`PdfTextRun`). The viewer overlays an invisible `BasicText` layer inside `SelectionContainer` on top of each rasterised page bitmap. Compose's selection UI (handles, magnifier, copy menu) comes for free. Because the overlay is pure common Compose, the same long-press selection + copy behaviour lights up on every platform — including iOS, where the `SelectionContainer` copy menu writes through Compose's clipboard.

Need a programmatic copy (e.g. your own "Copy all text" button)? The public `pdfViewerCopyToClipboard(text)` writes straight to the platform pasteboard — Android `ClipboardManager`, iOS `UIPasteboard.generalPasteboard`, Desktop AWT clipboard.

> **Limitation**: text selection only works for PDFs **built through the PdfKmp DSL** (`pdf { … }` / `pdfAsync { … }`). For arbitrary external PDFs, the bytes don't carry text-position metadata and the selection layer has nothing to render.

```kotlin
PdfViewer(document = doc, textSelectable = false)     // disable selection overlay
```

### Hyperlinks

`link(url) { … }` blocks in the DSL produce real clickable hotspots in the viewer. Tapping a link opens the URL in the system browser (Android `Intent.ACTION_VIEW`, iOS `UIApplication.openURL`).

```kotlin
val doc = pdf {
    page {
        link(url = "https://kotlinlang.org") {
            text("kotlinlang.org") { color = PdfColor.Blue }
        }
    }
}

PdfViewer(document = doc)                              // links live
PdfViewer(document = doc, hyperlinksEnabled = false)   // suppress overlay
```

Same caveat as text selection: only works for PdfKmp-built documents.

### Search

`KmpPdfViewer` ships an inline search bar (tap the search icon in the topbar). Type a query, then step through matches with the ↑ / ↓ chips — each match is highlighted on the page (amber, with the active match in a stronger fill) and scrolled into view. Two engines feed it, picked automatically:

- **PdfKmp-authored documents** search their captured text runs directly (the `searchPdfText` matcher). Exact, instant, and works on **every** platform.
- **External PDFs** (network, file picker, bundled third-party files — anything without captured runs) fall back to the **platform text engine**. The search bar UI is identical; only the source of the match rectangles differs.

Per-platform search support:

| Document source | Android | iOS | Desktop |
|---|---|---|---|
| PdfKmp `pdf { … }` (`PdfSource.Document`) | ✅ | ✅ | ✅ |
| External bytes / file / URL / asset | ❌ (no PDF text API) | ✅ (PDFKit `findString`) | ✅ (PdfBox `PDFTextStripper`) |

> **Android external-PDF search is unavailable**: `android.graphics.pdf.PdfRenderer` exposes no text-extraction API, so an external document on Android carries nothing to match against. The search affordance is auto-suppressed there (PdfKmp-authored documents still search normally because they carry their own text). On iOS and Desktop, external-document text is extracted lazily off the main thread the first time you search and cached for the session, so subsequent queries are cheap.
>
> External-PDF match rectangles come from the platform engine's glyph geometry (PDFKit selection bounds / PdfBox direction-adjusted boxes), mapped into the viewer's page-point space — they line up with the rasterised text the same way the PdfKmp path does.

```kotlin
KmpPdfViewer(document = doc, showSearch = true)   // search button (default)
KmpPdfViewer(bytes = external, showSearch = true) // searchable on iOS / Desktop; hidden on Android
```

### Highlight annotations (overlay)

`KmpPdfViewer` ships an **opt-in** highlight tool. Pass `showAnnotationTools = true` to surface a highlighter toggle in the topbar (it matches the existing icon style — a Lucide outlined highlighter, tinted iOS-blue / filled to show the active state). Tap the toggle to enter annotation mode, then:

- **Drag** on a page → draws a translucent yellow highlight rectangle (page-coordinate space, scaled with zoom exactly like search highlights).
- **Tap** an existing highlight → deletes it.

While annotation mode is on, page panning / pinch-zoom and text selection stand down so the drag is unambiguously a new highlight; toggle the tool back off to resume reading.

State lives in viewer state and is surfaced to the host so it can persist / restore:

```kotlin
var saved by remember { mutableStateOf(loadAnnotations()) }   // your storage

KmpPdfViewer(
    document = doc,
    showAnnotationTools = true,
    initialAnnotations = saved,                       // restore on open
    onAnnotationsChanged = { saved = it; persist(it) } // called on every add / delete
)
```

Each highlight is a `PdfViewerAnnotation(pageIndex, x, y, width, height, color)` in PDF points (top-left origin, Y down) — the same coordinate space as `PdfTextRun` / `PdfSearchHighlight`, so it lines up with the rasterised page at any zoom. Two pure helpers back the gesture logic and are reusable / unit-testable: `hitTestAnnotation(...)` (which highlight a tap landed on) and `buildAnnotationFromDrag(...)` (normalise + clamp a drag into a box).

> **Overlay by default; export on Desktop.** Highlights are painted *on top of* the rasterised page and are **not** written into the PDF bytes for share / print. On **Desktop**, the **save / download** action burns them in — see [Exporting highlights into the PDF](#exporting-highlights-into-the-pdf-desktop). On Android / iOS highlights stay overlay-only (no writable PDF API). Persist the `PdfViewerAnnotation` list yourself (via `onAnnotationsChanged`) and restore it through `initialAnnotations`.

### Exporting highlights into the PDF (Desktop)

On **Desktop** the viewer can burn the in-viewer highlights into the saved PDF as real `Highlight` (text-markup) annotations — so they show up when the file is reopened in any standard reader, not just this viewer. The behaviour is wired into the existing save action automatically: when `showAnnotationTools = true`, there is at least one highlight, and the platform supports export, the **download / save** action writes the **annotated** bytes instead of the originals.

```kotlin
KmpPdfViewer(
    document = doc,
    showAnnotationTools = true,
    // Desktop: tapping the topbar download/save button now saves a PDF with
    // the highlights embedded. Share / print still export the original.
)
```

Per-platform support:

| | Android | iOS | Desktop |
|---|---|---|---|
| Burn highlights into saved PDF | ❌ (read-only `PdfRenderer`, no writable PDF API) | ❌ (PDFKit write deferred — overlay only for now) | ✅ (PdfBox `PDAnnotationHighlight`) |

On Android / iOS, save still exports the untouched original — highlights remain overlay-only there. The low-level helpers behind this are `pdfViewerSupportsAnnotationExport` / `writeAnnotationsIntoPdf(...)` (internal; the wiring above is the public path). Each exported highlight carries the annotation's colour and a constant ~0.4 interior opacity, and the box is flipped from the viewer's top-left-origin coordinates into the PDF's bottom-left space.

### Password-protected PDFs

Pass `password` to open an encrypted document. With a missing or wrong password the viewer shows an inline "password protected" message instead of crashing, and fires `onDocumentError(PdfViewerError.PasswordRequired)` so the host can re-prompt.

```kotlin
KmpPdfViewer(
    bytes = encryptedBytes,
    password = "letmein",
    onDocumentError = { error ->
        when (error) {
            PdfViewerError.PasswordRequired -> promptForPassword()
            PdfViewerError.CannotOpen, PdfViewerError.LoadFailed -> showGenericError()
        }
    },
)
```

Per-platform support:

| | Android | iOS | Desktop |
|---|---|---|---|
| Open password-protected PDF | ❌ (`PdfRenderer` has **no** password API — always errors) | ✅ (PDFKit `unlockWithPassword`, pending macOS verification) | ✅ (PdfBox `Loader.loadPDF(bytes, password)`) |

> **Android cannot open encrypted PDFs at all.** `android.graphics.pdf.PdfRenderer` throws on a password-protected file and exposes no unlock API, so an encrypted document surfaces `PdfViewerError.PasswordRequired` there regardless of the password you pass — it's terminal. Show your own messaging / fall back to an external viewer.

### Two-page book mode

Pass `pageLayout = PdfPageLayout.TwoPageBook` to lay pages out as side-by-side spreads, like an open book — the cover sits alone first, then verso/recto pairs (`2-3`, `4-5`, …); a trailing odd page sits alone. Best on Desktop / tablet. Zoom, pan, search highlights, and annotations all map to the correct page. The default `PdfPageLayout.Single` (continuous one-page-per-row scroll) is unchanged.

```kotlin
KmpPdfViewer(document = doc, pageLayout = PdfPageLayout.TwoPageBook)
```

There is no built-in topbar toggle — `pageLayout` is parameter-only; flip it from your own UI if you want a switch.

### Share

A built-in share FAB hands the encoded PDF to the system share sheet (`Intent.ACTION_SEND` on Android via `FileProvider`, `UIActivityViewController` on iOS).

```kotlin
PdfViewer(document = doc, shareFileName = "invoice-2026-q1.pdf")
PdfViewer(document = doc, showShareButton = false)     // hide the FAB
```

Or wire your own share affordance:

```kotlin
val share = rememberPdfShareAction()
IconButton(onClick = { share(doc.toByteArray(), "invoice.pdf") }) {
    Icon(Icons.Default.Share, contentDescription = "Share")
}
PdfViewer(document = doc, showShareButton = false)
```

### Save to Downloads

`rememberPdfSaveAction()` returns a public action that persists the PDF to a user-visible location: **Android** writes to `Downloads/` via `MediaStore.Downloads` (API 29+) or `Environment.DIRECTORY_DOWNLOADS` (legacy), with a "Saved to Downloads" toast. **iOS** writes to `<NSDocumentDirectory>/`, surfaced in the Files app under "On My iPhone / <AppName>".

```kotlin
val save = rememberPdfSaveAction()
IconButton(onClick = { save(doc.toByteArray(), "invoice.pdf") }) {
    Icon(Icons.Default.Download, contentDescription = "Save")
}
```

There's no auto-rendered save FAB; placement is up to you. See [Customising the chrome](#customising-the-chrome) below for the two recommended patterns.

### Page indicator

A small pill — `n / total` — fades in at the bottom while the user scrolls and fades out 900 ms after they stop. Single-page documents see `1 / 1` too.

```kotlin
PdfViewer(document = doc, showPageIndicator = false)   // hide the chip
```

## Customising the chrome

The viewer ships with sensible defaults (share FAB at bottom-end, page indicator at bottom-centre) but exposes two escape hatches so the chrome ends up exactly where the host app needs it.

### Pattern 1 — actions in your own toolbar

Best when your screen already has a `Scaffold` with a `TopAppBar`. Suppress the built-in share FAB, then call the public actions from your own `IconButton`s:

```kotlin
@Composable
fun InvoiceScreen(doc: PdfDocument) {
    val share = rememberPdfShareAction()
    val save  = rememberPdfSaveAction()
    val bytes = remember(doc) { doc.toByteArray() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice") },
                actions = {
                    IconButton(onClick = { save(bytes, "invoice.pdf") }) {
                        Icon(PdfSaveIcon, contentDescription = "Save")
                    }
                    IconButton(onClick = { share(bytes, "invoice.pdf") }) {
                        Icon(PdfShareIcon, contentDescription = "Share")
                    }
                },
            )
        },
    ) { padding ->
        PdfViewer(
            document = doc,
            modifier = Modifier.padding(padding),
            showShareButton = false,
        )
    }
}
```

### Pattern 2 — `overlay` slot for floating actions

Best for full-screen viewers without their own toolbar, or when you want extra FABs alongside the bitmap. The `overlay: @Composable BoxScope.() -> Unit` slot is rendered on top of every other piece of chrome — drop in [`PdfShareFab`](#public-api-surface) / [`PdfSaveFab`](#public-api-surface) for one-line setup, or any composable you like:

```kotlin
PdfViewer(
    document = doc,
    showShareButton = false,         // suppress the auto-rendered FAB
    overlay = {                      // BoxScope receiver — use Modifier.align
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PdfSaveFab(doc, fileName = "invoice.pdf")
            PdfShareFab(doc, fileName = "invoice.pdf")
        }
    },
)
```

The slot takes anything composable — watermarks, custom HUDs, page-jump controls, your branded action sheet:

```kotlin
PdfViewer(
    document = doc,
    overlay = {
        Text(
            "DRAFT",
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = -30f; alpha = 0.06f },
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
        )
    },
)
```

## Public API surface

| Composable / function | Purpose |
|---|---|
| **`@Composable KmpPdfViewer(uri / document / bytes / source, …)`** | **All-in-one viewer screen — recommended composable entry** |
| **`KmpPdfLauncher.open(uri / document / bytes, …)`** | **Imperative wrapper — call from any scope** |
| `PdfViewer(source / document / bytes, …)` | Lower-level viewer (no topbar / search) — for advanced layouts |
| `PdfViewerTopBar(…)` / `…MinimalMono(…)` / `…ClassicIos(…)` | Standalone topbar variants. `titleOverflow = PdfTopBarTitleOverflow.{Ellipsis, Marquee}` picks how a long title yields — icons never shrink |
| `PdfSource.Bytes(bytes)` / `PdfSource.Document(bytes, runs, links)` | Sealed input shape |
| `PdfSource.of(document)` / `PdfSource.of(bytes)` | Convenience factories |
| `rememberPdfShareAction()` | Action that triggers the system share sheet |
| `rememberPdfSaveAction()` | Action that writes to Downloads / Documents |
| `rememberPdfPrintAction()` | Action that opens the platform print pipeline (`PrintManager` / `UIPrintInteractionController` / `PrinterJob`) — surfaced in the topbar via `showPrint = true` |
| `PdfShareFab(document / bytes, …)` | Material 3 share FAB ready for the `overlay` slot |
| `PdfSaveFab(document / bytes, …)` | Material 3 save FAB ready for the `overlay` slot |
| `PdfShareIcon` / `PdfSaveIcon` | Inline `ImageVector`s — reuse for visual consistency in your own toolbars |
| `PdfViewerAnnotation(pageIndex, x, y, width, height, color)` | One overlay highlight, in PDF points |
| `hitTestAnnotation(...)` / `buildAnnotationFromDrag(...)` | Pure annotation helpers (tap hit-test / drag → box) |
| `PdfPageLayout.{Single, TwoPageBook}` | Page arrangement for the viewer's `pageLayout` |
| `PdfViewerError.{PasswordRequired, CannotOpen, LoadFailed}` | Why a document couldn't open, surfaced via `onDocumentError` |
| `pdfViewerCopyToClipboard(text)` | Writes text to the platform pasteboard (Android / iOS / Desktop) |

`PdfViewer` parameters (defaults shown):

| Parameter | Default | Purpose |
|---|---|---|
| `showShareButton: Boolean` | `true` | Built-in share FAB |
| `shareFileName: String` | `"document.pdf"` | Filename surfaced to the share sheet |
| `backgroundColor: Color` | `surfaceContainerLow` | Behind the page bitmaps |
| `pageBackgroundColor: Color` | `White` | Behind each page |
| `contentPadding: PaddingValues` | `0.dp` | Around the page list |
| `pageSpacing: Dp` | `4.dp` | Vertical gap between pages |
| `renderDensity: Float` | `2f` | Base rasterisation density |
| `maxZoom: Float` | `5f` | Pinch ceiling |
| `zoomEnabled: Boolean` | `true` | Master switch for pinch + double-tap |
| `doubleTapToZoom: Boolean` | `true` | Independent double-tap toggle |
| `textSelectable: Boolean` | `true` | Selection overlay |
| `hyperlinksEnabled: Boolean` | `true` | Clickable link overlay |
| `invertColors: Boolean` | `false` | Dark-mode page rendering — bitmaps are colour-inverted (white → near-black, black text → white); the encoded PDF and the share / save / print bytes are untouched |
| `showPageIndicator: Boolean` | `true` | Bottom-centre `n / total` chip |
| `pageLayout: PdfPageLayout` | `Single` | `Single` (one page per row) or `TwoPageBook` (side-by-side book spreads, cover alone). Shares zoom / pan / search / annotations |
| `password: String?` | `null` | Unlocks an encrypted PDF. Wrong / missing → inline "password protected" message + `onDocumentError`. Android can't open encrypted PDFs at all |
| `onDocumentError: ((PdfViewerError) -> Unit)?` | `null` | Fired when a document can't open: `PasswordRequired` / `CannotOpen` / `LoadFailed` |
| `shareButtonAlignment` | `BottomEnd` | Default share FAB anchor (ignored once `overlay` is non-empty) |
| `shareButtonPadding` | `16.dp` | Default share FAB inset |
| `annotations: List<PdfViewerAnnotation>` | `emptyList()` | Highlight annotations painted over the pages (overlay only) |
| `annotationMode: Boolean` | `false` | When `true`, drag draws a highlight and tap deletes one |
| `onAnnotationCreated: ((PdfViewerAnnotation) -> Unit)?` | `null` | Fired when a drag in annotation mode produces a highlight |
| `onAnnotationDeleted: ((Int) -> Unit)?` | `null` | Fired with the annotation index a tap deleted |
| `overlay: @Composable BoxScope.() -> Unit` | `{}` | Free-form slot rendered on top of the viewer — see [Customising the chrome](#customising-the-chrome) |

`KmpPdfViewer` adds the higher-level, state-owning annotation API on top of those low-level `PdfViewer` hooks:

| Parameter | Default | Purpose |
|---|---|---|
| `showAnnotationTools: Boolean` | `false` | Surfaces the highlighter toggle in the topbar. When on + ≥1 highlight + Desktop, the save action exports the **annotated** PDF |
| `initialAnnotations: List<PdfViewerAnnotation>` | `emptyList()` | Highlights restored into viewer state on first composition |
| `onAnnotationsChanged: ((List<PdfViewerAnnotation>) -> Unit)?` | `null` | Fired with the full list on every add / delete, for persistence |
| `pageLayout: PdfPageLayout` | `Single` | `Single` or `TwoPageBook` (side-by-side book spreads). Parameter-only — no topbar toggle |
| `password: String?` | `null` | Unlocks an encrypted PDF (Desktop / iOS). Android can't open encrypted PDFs |
| `onDocumentError: ((PdfViewerError) -> Unit)?` | `null` | Fired when the document can't open (`PasswordRequired` / `CannotOpen` / `LoadFailed`) |

## Architecture

```
┌────────────────────────────────────────────────────────┐
│ pdf { }  ───► DocumentRenderer  ───► RecordingDriver   │  :pdfkmp
│                                       │                │
│                                       ▼                │
│                          ByteArray + textRuns + links  │
└────────────────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────┐
│ PdfSource.Document(bytes, runs, links)                 │  :pdfkmp-viewer
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ LazyColumn  (vertical, virtualised pages)        │  │
│  │   ├── Image     ← bitmap from PdfRenderer/PDFKit │  │
│  │   ├── SelectionContainer + invisible BasicText   │  │
│  │   └── clickable Box per hyperlink                │  │
│  └──────────────────────────────────────────────────┘  │
│  + share FAB / page indicator chip                     │
└────────────────────────────────────────────────────────┘
```

The recording driver is a transparent decorator around the platform `PdfDriver` — every `drawText` and `linkAnnotation` call gets captured with its page index, the rest of the operations forward verbatim. Output bytes are byte-for-byte identical to a non-recording render.

## Limitations

- **Text selection / hyperlinks only on PdfKmp-built documents.** External PDFs (network, file picker, etc.) are bitmap-only for *selection* and *hyperlinks* — those overlays need the captured text-position metadata that only the PdfKmp DSL produces. **Search**, however, now works on external PDFs on iOS (PDFKit) and Desktop (PdfBox); see the [Search](#search) support matrix. Android external-PDF search stays unavailable because `PdfRenderer` exposes no text API.
- **Selection bounding boxes use Compose font metrics**, not the original PDF font. Text content selects correctly (paste produces the right characters) but the highlight rectangle hugs the Compose-laid-out text, which can drift a pixel or two from the rasterised glyphs.
- **External-PDF search highlights are approximate per platform.** Match rectangles come from the platform engine (PDFKit selection bounds / PdfBox direction-adjusted glyph boxes), so a complex / rotated layout can land the highlight a pixel or two off the glyph. The matched text is always correct; only the rectangle is approximate.
- **Highlight annotations are overlay-only except for Desktop save.** They are painted on top of the rasterised page; share / print always export the untouched original. On **Desktop** the save / download action burns them into the PDF as real `Highlight` annotations (see [Exporting highlights into the PDF](#exporting-highlights-into-the-pdf-desktop)); on Android / iOS there is no writable PDF API so save also keeps exporting the original. Persist the `PdfViewerAnnotation` list via `onAnnotationsChanged` and restore through `initialAnnotations`.
- **Password-protected PDFs open on Desktop and iOS only.** Pass `password`; Android's `PdfRenderer` has no password API, so encrypted documents are unreadable there (surfaced as `PdfViewerError.PasswordRequired`). The iOS unlock path (PDFKit) is pending macOS verification.
- **No print preview integration**. Use `Intent.ACTION_VIEW` / `UIDocumentInteractionController` from your own UI if needed.
- **iPad share** falls through silently when the host app hasn't set a popover anchor — see KDoc on `ShareLauncher.ios.kt`.
- **Very large documents prefetch a tighter window.** The default `cacheStrategy = PdfPageCacheStrategy.Auto` is adaptive: documents up to ~200 pages keep a symmetric prefetch window (3 pages either side); past that the window tightens to a forward-biased `(before = 2, after = 4)` so the viewer never tries to warm a wide ring that the memory budget would only evict. Pass an explicit `PdfPageCacheStrategy.Window(...)` / `.All` to override. A hard per-platform byte budget (Android: 25 % of `maxMemory()`, iOS: 200 MB) always caps total cache size and evicts least-recently-used pages first, so a wide window can never crash the process.

## Versioning

Released in lock-step with `:pdfkmp` and `:pdfkmp-compose-resources`; see the root [README](../README.md#publishing-checklist) for the cut-a-release flow. Pre-1.0 minor versions may break API; alpha / beta tags signal an actively settling surface.
