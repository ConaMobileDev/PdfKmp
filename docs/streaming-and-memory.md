# Streaming / incremental rendering — findings

*Investigated 2026-06 as part of the large feature wave. Status: analysis
with low-hanging improvements already in place; full streaming is
deliberately out of scope.*

## How memory behaves today

The pipeline is already incremental **per top-level child**:

- `DocumentRenderer.renderPage` measures each top-level child of a page
  *one at a time* (`measure(child, …)` inside the loop) and places it
  immediately. The full `MeasuredNode` tree for a page never needs to
  exist at once unless one child *is* the whole page.
- Slicing (`sliceText` / `sliceColumn` / `sliceTable`) walks lazily —
  each chunk is constructed, drawn, and dropped before the next page
  opens.
- The dry-run passes (`CountingPdfDriver`) emit no output and retain
  nothing but counters and the TOC destination map.

What *does* hold the whole document in memory is the **driver layer**,
and that is inherent to every backend:

| Backend | Buffer | Why it can't stream |
|---|---|---|
| JVM / PdfBox | `PDDocument` in heap until `save()` | PdfBox needs the full object graph to write the xref table and subset fonts (subsetting depends on the total glyph set, only known at the end). |
| iOS | `NSMutableData` | `UIGraphicsBeginPDFContextToData` writes into a growing data buffer by design; the file-based variant (`…ToFile`) would stream to disk but changes the public `ByteArray` contract. |
| Android | `PdfDocument` internal buffer + the post-processing patcher | `PdfDocument.writeTo` happens once at the end; the incremental-update patcher then needs the complete byte array. |

## Peak-memory profile

For text-dominant documents the dominant cost is the backend buffer
(roughly the size of the output PDF) — layout structures are transient.
For image-heavy documents the dominant cost is decoded bitmaps; the
`allowDownScale` subsampling (default on, ~200 DPI target) is the
existing mitigation and caps each image's decoded footprint.

## Low-hanging improvements (already done)

- Per-child measurement in `renderPage` (was already the design).
- Image subsampling on by default.
- The TOC/anchor pre-pass reuses one `CountingPdfDriver` for both the
  page count and destination resolution instead of two passes.

## What full streaming would take (not planned)

1. A `PdfDriver.finishTo(sink: Output)` variant so backends with
   file-streaming modes (iOS `…ToFile`, PdfBox `save(OutputStream)`)
   avoid one full-copy — saves one buffer copy, not the working set.
2. Restructuring font subsetting on JVM to two-pass (collect glyphs →
   stream pages), which PdfBox does not support without forking its
   writer.

Given that a 200-page text document produces a working set of a few MB,
the cost/benefit does not justify the API churn. Revisit if users report
multi-hundred-MB image catalogues.
