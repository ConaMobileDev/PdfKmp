# pdfkmp-viewer

Compose Multiplatform PDF viewer with **document-wide pinch zoom**, **text selection**, **hyperlinks**, **share**, and **save to Downloads** — works on Android (`android.graphics.pdf.PdfRenderer`) and iOS (`PDFKit`).

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
pdfkmp = "0.2.0-beta01"

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

**One call, complete screen.** `KmpPdfViewer.open(...)` is the recommended entry point — it owns the topbar, search bar morph, share / save / hyperlink launchers, page indicator, and gesture model. The host configures *what* is exposed and *what happens on back*; nothing else.

```kotlin
// Open a remote PDF
KmpPdfViewer.open(
    uri = "https://example.com/invoice.pdf",
    title = "Invoice 2026 Q1",
    onBack = { navController.popBackStack() },
)

// Open a PdfKmp document — text selection + hyperlinks light up automatically
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
KmpPdfViewer.open(
    document = document,
    title = "Hello",
    fileName = "hello.pdf",
    onBack = { navController.popBackStack() },
)
```

`KmpPdfViewer.open` accepts four input shapes:

| Overload | Use when | Selection / hyperlinks |
|---|---|---|
| `open(uri: String, …)` | `content://`, `file://`, `http(s)://`, asset / bundle paths | **disabled** — opaque bytes |
| `open(document: PdfDocument, …)` | document came from PdfKmp's `pdf { }` DSL | **enabled** |
| `open(bytes: ByteArray, …)` | raw `%PDF-…` from disk / network / file picker | **disabled** |
| `open(source: PdfSource, …)` | you constructed a `PdfSource.Document(bytes, runs, links)` yourself | **enabled** when `source` is `Document` |

> Need finer control (custom topbar, multi-FAB layouts, bottom-sheet share)? The lower-level composables — [`PdfViewer`](#public-api-surface), `PdfViewerTopBar`, `PdfSearchBar`, plus the `rememberPdfShareAction` / `rememberPdfSaveAction` / `rememberPdfUrlLauncher` action factories — stay public. `KmpPdfViewer.open` is the opinionated default; the building blocks remain available.

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

Long-press text to select, drag the handles to extend, hit **Copy** in the system menu — exactly like reading a PDF in Apple Books or Samsung Notes.

How it works: during rendering, the library captures every laid-out text run with its position (`PdfTextRun`). The viewer overlays an invisible `BasicText` layer inside `SelectionContainer` on top of each rasterised page bitmap. Compose's selection UI (handles, magnifier, copy menu) comes for free.

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
| **`KmpPdfViewer.open(uri / document / bytes / source, …)`** | **All-in-one viewer screen — recommended entry point** |
| `PdfViewer(source / document / bytes, …)` | Lower-level viewer (no topbar / search) — for advanced layouts |
| `PdfSource.Bytes(bytes)` / `PdfSource.Document(bytes, runs, links)` | Sealed input shape |
| `PdfSource.of(document)` / `PdfSource.of(bytes)` | Convenience factories |
| `rememberPdfShareAction()` | Action that triggers the system share sheet |
| `rememberPdfSaveAction()` | Action that writes to Downloads / Documents |
| `PdfShareFab(document / bytes, …)` | Material 3 share FAB ready for the `overlay` slot |
| `PdfSaveFab(document / bytes, …)` | Material 3 save FAB ready for the `overlay` slot |
| `PdfShareIcon` / `PdfSaveIcon` | Inline `ImageVector`s — reuse for visual consistency in your own toolbars |

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
| `showPageIndicator: Boolean` | `true` | Bottom-centre `n / total` chip |
| `shareButtonAlignment` | `BottomEnd` | Default share FAB anchor (ignored once `overlay` is non-empty) |
| `shareButtonPadding` | `16.dp` | Default share FAB inset |
| `overlay: @Composable BoxScope.() -> Unit` | `{}` | Free-form slot rendered on top of the viewer — see [Customising the chrome](#customising-the-chrome) |

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

- **Text selection / hyperlinks only on PdfKmp-built documents.** External PDFs (network, file picker, etc.) are bitmap-only. Adding general PDF text extraction would require a parser dependency (PdfBox-Android, PDF.js, …) — a separate `:pdfkmp-viewer-text` module is on the roadmap.
- **Selection bounding boxes use Compose font metrics**, not the original PDF font. Text content selects correctly (paste produces the right characters) but the highlight rectangle hugs the Compose-laid-out text, which can drift a pixel or two from the rasterised glyphs.
- **No print preview integration**. Use `Intent.ACTION_VIEW` / `UIDocumentInteractionController` from your own UI if needed.
- **iPad share** falls through silently when the host app hasn't set a popover anchor — see KDoc on `ShareLauncher.ios.kt`.

## Versioning

Released in lock-step with `:pdfkmp` and `:pdfkmp-compose-resources`; see the root [README](../README.md#publishing-checklist) for the cut-a-release flow. Pre-1.0 minor versions may break API; alpha / beta tags signal an actively settling surface.
