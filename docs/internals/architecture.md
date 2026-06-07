# Architecture

PdfKmp is built around a single closed (sealed) `PdfNode` tree authored via a
Compose-style DSL. Layout runs in common code; rendering is dispatched to a
platform-specific `PdfCanvas`.

## The pipeline

```
pdf { … }                       authoring DSL (commonMain/dsl)
   │
   ▼
PdfNode tree                    sealed node hierarchy (commonMain/node)
   │
   ▼
measure(...) → MeasuredNode     layout engine (commonMain/layout/LayoutEngine.kt)
   │
   ▼
DocumentRenderer.place(...)     page placement + pagination (commonMain/render)
   │
   ▼
PdfCanvas draw calls            one implementation per platform
```

Every DSL node maps 1:1 to a `MeasuredNode` produced by the layout engine, then
to draw calls on `PdfCanvas`. A new node type must update all three layers. The
renderer does a counting dry-run pass before the real pass so `PageContext`'s
`totalPages` (and `tableOfContents()` page numbers) are exact.

### `pdf` vs `pdfAsync` — the preflight pass

The synchronous `pdf { }` entry point walks the tree and renders straight away.
`pdfAsync { }` inserts one extra step in front of `measure(...)`: a suspend
**preflight** that calls `DocumentSpec.resolveDeferred()`, replacing every
`LazyNode` (the deferred placeholders emitted by the typed `Res.drawable.*`
overloads) with a concrete node by running each one's suspend resource loader.
After resolving, `pdfAsync` re-collects custom fonts (a `TextNode` produced by a
resolver wasn't visible during the original DSL walk) before handing the finished
spec to the renderer. A `LazyNode` that reaches the renderer through synchronous
`pdf { }` throws — the error points the caller back at `pdfAsync`.

### `PdfDriverFactory` — the expect/actual backend seam

Which backend encodes a document is chosen by `defaultPdfDriverFactory()`, an
`expect fun` with one `actual` per platform (Android / iOS / JVM / wasmJs). Both
`pdf` and `pdfAsync` take a `factory: PdfDriverFactory = defaultPdfDriverFactory()`
parameter, so tests (and consumers wanting a custom backend) can pass
`FakePdfDriver` or any other factory without touching the DSL. The factory's
`create(metadata, customFonts)` returns the platform `PdfDriver` that produces the
`PdfCanvas` the renderer draws onto.

## Platform canvases

| Backend | Implementation | Notes |
|---|---|---|
| Android | `AndroidPdfCanvas` | `android.graphics.pdf.PdfDocument` + `Canvas`. A pure-Kotlin post-processor adds the info dict, link/GoTo annotations, and outline in `finish()`. |
| iOS | `IosPdfCanvas` | `UIGraphicsBeginPDFContextToData` + Core Graphics. |
| Desktop (JVM) | `JvmPdfCanvas` (`JvmPdfDriver`) | Apache PDFBox; subset TrueType fonts, axial/radial shadings, real link annotations + info dict. PDFBox lives only in `jvmMain`. |
| Web (Wasm) | `KmpPdfCanvas` / `KmpPdfDriver` | The `kmpwriter` pure-Kotlin PDF 1.7 writer (in `commonMain`), so the wasm target needs no platform PDF engine. |
| Test | `FakePdfCanvas` (`FakePdfDriver`) | Records every draw call as a sealed `DrawCall` in `commonTest` so the whole pipeline runs without native APIs. |

Every text glyph and shape is emitted as a vector path — no rasterisation —
across all backends.

### The kmpwriter (web backend)

Because browsers expose no PDF engine to Wasm, PdfKmp ships its own pure-Kotlin
PDF writer in `commonMain` (`com.conamobile.pdfkmp.kmpwriter`): page/object
writers, WinAnsi text encoding with Standard-14 Helvetica metrics, an inflate/
deflate stream codec, axial/radial shading, JPEG + 8-bit PNG embedding, and a
navigation (links / destinations / outline) writer. The identical writer is
validated on the JVM by re-parsing and rasterising its output with PDFBox. See
[Web (Kotlin/Wasm)](../guides/web.md).

### Recording driver

`RecordingPdfDriver` is a transparent decorator around any platform `PdfDriver`:
it snapshots every `drawText` and `linkAnnotation` (with page index) into
`PdfDocument.textRuns` / `PdfDocument.hyperlinks` so the [viewer](../guides/viewer.md)
can layer text selection / clickable overlays without re-parsing the bytes.
Output bytes are byte-for-byte identical to a non-recording render.

## Module map

| Module | What it is |
|---|---|
| `:pdfkmp` | Core KMP library — Android `aar` + iOS framework `PdfKmp` + JVM `jar` + wasmJs klib. Compose-free. Publishable. |
| `:pdfkmp-compose-resources` | Opt-in bridge mapping Compose `DrawableResource` references onto the DSL (`toVectorImage()`, `toBytes()`, the `pdfAsync` overloads). Publishable. |
| `:pdfkmp-markdown` | Opt-in CommonMark-lite renderer (`markdown(text, theme)`). Publishable. |
| `:pdfkmp-viewer` | Opt-in Compose Multiplatform `PdfViewer` / `KmpPdfLauncher`. Android / iOS / Desktop (not web). Publishable. |
| `:sample-shared` | KMP library (Android target) holding the Compose-Resources slice of the Android sample. Not published. |
| `:sample` | Plain `com.android.application` Compose Android sample. |
| `iosApp/` | SwiftUI / PDFKit iOS sample. |
| `:sample-desktop` | Compose for Desktop sample (master/detail + live playground). |

## Where things live (library)

| What | Where |
|---|---|
| DSL entry points (`pdf { }`, `column`, `row`, `text`, …) | `pdfkmp/src/commonMain/.../dsl/` |
| Sealed node hierarchy (`PdfNode`, `TextNode`, …) | `pdfkmp/src/commonMain/.../node/` |
| Layout engine (`measure(...)` → `MeasuredNode`) | `pdfkmp/src/commonMain/.../layout/LayoutEngine.kt` |
| Renderer / page placement | `pdfkmp/src/commonMain/.../render/DocumentRenderer.kt` |
| Charts DSL | `pdfkmp/src/commonMain/.../dsl/Charts.kt` |
| Document-level scope (`encryption`, `attachment`, `pdfA`, `metadata`) | `pdfkmp/src/commonMain/.../dsl/DocumentScope.kt` |
| Android canvas | `pdfkmp/src/androidMain/.../render/AndroidPdfCanvas.kt` |
| iOS canvas | `pdfkmp/src/iosMain/.../render/IosPdfCanvas.kt` |
| JVM canvas (+ driver, font registry, shading) | `pdfkmp/src/jvmMain/.../render/JvmPdfCanvas.kt` |
| Web writer (`kmpwriter`) | `pdfkmp/src/commonMain/.../kmpwriter/` |
| Test backend | `pdfkmp/src/commonTest/.../test/FakePdfBackend.kt` |
| Worked-example documents | `pdfkmp/src/commonMain/.../samples/Samples.kt` |
| Smoke tests for samples | `pdfkmp/src/commonTest/.../samples/SamplesSmokeTest.kt` |

## Conventions

- Coordinates are in **PDF points with a top-left origin** (Y grows downward).
  Each backend translates to its native convention internally.
- `explicitApi()` is on for `:pdfkmp` — every declaration is `public` or
  `internal`.
- Base package: `com.conamobile.pdfkmp`.

## See also

- [Streaming & memory](../streaming-and-memory.md)
- [Wasm feasibility](../wasm-feasibility.md)
- [Platform parity](../guides/platform-parity.md)
